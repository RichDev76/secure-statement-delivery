package com.example.statementservice.statement.download;

import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLogRepository;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.StatementStorageUnavailableException;
import com.example.statementservice.statement.signedlink.LinkValidationResult;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiterPort;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadService {

    private static final String AUDIT_KEY_IP = "ip";
    private static final String AUDIT_KEY_USER_AGENT = "userAgent";
    private static final String AUDIT_KEY_TOKEN = "token";
    private static final String AUDIT_KEY_REASON = "reason";
    private static final String AUDIT_KEY_ERROR = "error";
    private static final String AUDIT_UNKNOWN = "unknown";
    private static final String STAGE_EXISTENCE_CHECK = "checking file existence";
    private static final String STAGE_OPENING_FILE = "opening file";

    private final SignedLinkService signedLinkService;
    private final StatementService statementService;
    private final AuditService auditService;
    private final SignedLinkRateLimiterPort rateLimiter;
    private final AuditLogRepository auditLogRepository;

    public DownloadStreamResult validateAndStreamDetailed(
            String token,
            Long expires,
            UUID linkId,
            String fileName,
            String clientIp,
            String userAgent,
            String performedBy) {
        log.debug("Download request (detailed) - token: {}, ip: {}, user: {}", maskToken(token), clientIp, performedBy);

        // Ahead of signature validation deliberately: a signature-guessing flood against a known
        // real linkId is throttled too, not just genuinely valid requests.
        if (linkId != null && !rateLimiter.tryConsume(linkId)) {
            log.warn("Rate limit exceeded - linkId: {}", linkId);
            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    null,
                    null,
                    linkId,
                    performedBy,
                    getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.RATE_LIMITED.getValue()));
            return new DownloadStreamResult(DownloadOutcome.RATE_LIMITED, Optional.empty());
        }

        // Step 1: Validate link
        var result = signedLinkService.validate(token, expires, linkId, fileName);
        if (!result.isValid()) {
            handleInvalidLink(result, token, clientIp, userAgent, performedBy);
            var outcome = getDownloadOutcome(result);
            return new DownloadStreamResult(outcome, Optional.empty());
        }

        // Step 2: Fetch statement
        var link = result.getLink();
        Optional<Statement> statementOpt = statementService.findStatementById(link.getStatementId());
        if (statementOpt.isEmpty()) {
            handleMissingStatement(link, token, clientIp, userAgent, performedBy);
            return new DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        }

        // Step 3: Verify file exists
        var statement = statementOpt.get();
        try {
            if (!statementService.fileExists(statement)) {
                handleMissingFile(statement, link, token, clientIp, userAgent, performedBy);
                return new DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
            }
        } catch (StatementStorageUnavailableException e) {
            handleStorageUnavailable(
                    statement, link, token, clientIp, userAgent, performedBy, STAGE_EXISTENCE_CHECK, e);
            return new DownloadStreamResult(DownloadOutcome.STORAGE_UNAVAILABLE, Optional.empty());
        }

        // Step 4: Decrypt and stream
        return decryptAndStream(statement, link, token, clientIp, userAgent, performedBy);
    }

    private DownloadOutcome getDownloadOutcome(LinkValidationResult result) {
        return switch (result.getFailureReason()) {
            case EXPIRED -> DownloadOutcome.LINK_EXPIRED;
            case NOT_FOUND -> DownloadOutcome.STATEMENT_NOT_FOUND;
            default -> DownloadOutcome.INVALID_SIGNATURE;
        };
    }

    public record DownloadStreamResult(DownloadOutcome outcome, Optional<InputStream> stream) {}

    private String getReason(LinkValidationResult result) {
        return switch (result.getFailureReason()) {
            case EXPIRED -> DownloadFailureReason.EXPIRED.getValue();
            case NOT_FOUND -> DownloadFailureReason.STATEMENT_NOT_FOUND.getValue();
            default -> DownloadFailureReason.INVALID.getValue();
        };
    }

    private Map<String, Object> getUserAuditDetails(String token, String clientIp, String userAgent, String reason) {
        var details = new HashMap<String, Object>();
        details.put(AUDIT_KEY_IP, clientIp != null ? clientIp : AUDIT_UNKNOWN);
        details.put(AUDIT_KEY_USER_AGENT, userAgent != null ? userAgent : AUDIT_UNKNOWN);
        details.put(AUDIT_KEY_TOKEN, maskToken(token));
        if (reason != null) {
            details.put(AUDIT_KEY_REASON, reason);
        }
        return details;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private void handleInvalidLink(
            LinkValidationResult result, String token, String clientIp, String userAgent, String performedBy) {
        var reason = getReason(result);
        var statementId = result.getLink() != null ? result.getLink().getStatementId() : null;
        var linkId = result.getLink() != null ? result.getLink().getId() : null;

        var accountNumber = fetchAccountNumber(statementId);

        log.warn("Link validation failed - reason: {}, statementId: {}", reason, statementId);
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statementId,
                accountNumber,
                linkId,
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, reason));
    }

    private void handleMissingStatement(
            SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        log.error("Statement not found for link - statementId: {}", link.getStatementId());
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                link.getStatementId(),
                null,
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.STATEMENT_NOT_FOUND.getValue()));
    }

    private void handleMissingFile(
            Statement statement, SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        log.error("File not found - key: {}, statementId: {}", statement.getStorageKey(), statement.getId());
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statement.getId(),
                statement.getAccountNumber(),
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.FILE_MISSING.getValue()));
    }

    private void handleStorageUnavailable(
            Statement statement,
            SignedLink link,
            String token,
            String clientIp,
            String userAgent,
            String performedBy,
            String stage,
            StatementStorageUnavailableException e) {
        log.error(
                "Storage unavailable while {} - key: {}, statementId: {}",
                stage,
                statement.getStorageKey(),
                statement.getId(),
                e);
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statement.getId(),
                statement.getAccountNumber(),
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.STORAGE_UNAVAILABLE.getValue()));
    }

    private DownloadStreamResult decryptAndStream(
            Statement statement, SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        try {
            var decrypted = statementService.openDecryptedFile(statement);

            log.info(
                    "Download successful - statementId: {}, account: {}",
                    statement.getId(),
                    statement.getAccountNumber());

            checkForSuspiciousRedemption(link, clientIp, userAgent);

            try {
                auditService.record(
                        AuditAction.DOWNLOAD_SUCCESS.getValue(),
                        statement.getId(),
                        statement.getAccountNumber(),
                        link.getId(),
                        performedBy,
                        getUserAuditDetails(token, clientIp, userAgent, null));
            } catch (Exception auditEx) {
                // Log but don't fail the download
                log.warn("Failed to record download audit", auditEx);
            }
            return new DownloadStreamResult(DownloadOutcome.OK, Optional.of(decrypted));
        } catch (StatementStorageUnavailableException e) {
            handleStorageUnavailable(statement, link, token, clientIp, userAgent, performedBy, STAGE_OPENING_FILE, e);
            return new DownloadStreamResult(DownloadOutcome.STORAGE_UNAVAILABLE, Optional.empty());
        } catch (FileNotFoundException e) {
            // Object deleted between the exists() check and open().
            handleMissingFile(statement, link, token, clientIp, userAgent, performedBy);
            return new DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
        } catch (Exception e) {
            log.error("Decryption failed - statementId: {}, error: {}", statement.getId(), e.getMessage(), e);
            var errorAuditDetails =
                    getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.DECRYPTION_FAILED.getValue());
            errorAuditDetails.put(AUDIT_KEY_ERROR, e.getMessage());

            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    errorAuditDetails);

            return new DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());
        }
    }

    // Must be called before auditService.record(DOWNLOAD_SUCCESS, ...), not after: that call
    // submits asynchronously, so checking for prior redemptions here first avoids racing against
    // its own not-yet-committed write.
    private void checkForSuspiciousRedemption(SignedLink link, String clientIp, String userAgent) {
        try {
            var priorSuccesses = auditLogRepository.findBySignedLinkIdAndAction(
                    link.getId(), AuditAction.DOWNLOAD_SUCCESS.getValue());
            var suspicious = priorSuccesses.stream()
                    .anyMatch(prior -> !Objects.equals(prior.getDetails().get(AUDIT_KEY_IP), clientIp)
                            || !Objects.equals(prior.getDetails().get(AUDIT_KEY_USER_AGENT), userAgent));
            if (suspicious) {
                log.warn(
                        "Signed link {} redeemed from a different ip/userAgent than a prior successful download",
                        link.getId());
            }
        } catch (Exception e) {
            log.warn("Suspicious-redemption check failed - linkId: {}", link.getId(), e);
        }
    }

    private String fetchAccountNumber(UUID statementId) {
        if (statementId == null) return null;
        return statementService
                .findStatementById(statementId)
                .map(Statement::getAccountNumber)
                .orElse(null);
    }
}
