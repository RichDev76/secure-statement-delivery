package com.example.statementservice.statement.download;

import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditDetailKeys;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.StatementStorageUnavailableException;
import com.example.statementservice.statement.signedlink.LinkValidationResult;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiter;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadService {

    private static final String AUDIT_KEY_TOKEN = "token";
    private static final String STAGE_EXISTENCE_CHECK = "checking file existence";
    private static final String STAGE_OPENING_FILE = "opening file";

    private final SignedLinkService signedLinkService;
    private final StatementService statementService;
    private final AuditService auditService;
    private final SignedLinkRateLimiter rateLimiter;

    public DownloadOutcome validateAndStreamDetailed(
            String token,
            Long expires,
            UUID linkId,
            String fileName,
            String clientIp,
            String userAgent,
            String performedBy,
            Consumer<InputStream> onSuccess) {
        Objects.requireNonNull(onSuccess, "onSuccess must not be null");
        log.debug("Download request (detailed) - token: {}, ip: {}, user: {}", maskToken(token), clientIp, performedBy);

        var rateLimited = enforceRateLimit(linkId, token, clientIp, userAgent, performedBy);
        if (rateLimited.isPresent()) {
            return rateLimited.get();
        }

        var validation = signedLinkService.validate(token, expires, linkId, fileName);
        var invalidLink = requireValidLink(validation, token, clientIp, userAgent, performedBy);
        if (invalidLink.isPresent()) {
            return invalidLink.get();
        }

        var link = validation.link();
        var statementOpt = statementService.findStatementById(link.getStatementId());
        if (statementOpt.isEmpty()) {
            handleMissingStatement(link, token, clientIp, userAgent, performedBy);
            return DownloadOutcome.STATEMENT_NOT_FOUND;
        }
        var statement = statementOpt.get();

        var missingOrUnavailable = ensureFileExists(statement, link, token, clientIp, userAgent, performedBy);
        if (missingOrUnavailable.isPresent()) {
            return missingOrUnavailable.get();
        }

        return decryptAndStream(statement, link, token, clientIp, userAgent, performedBy, onSuccess);
    }

    // Ahead of signature validation deliberately: a signature-guessing flood against a known
    // real linkId is throttled too, not just genuinely valid requests.
    private Optional<DownloadOutcome> enforceRateLimit(
            UUID linkId, String token, String clientIp, String userAgent, String performedBy) {
        if (linkId == null || rateLimiter.tryConsume(linkId)) {
            return Optional.empty();
        }
        log.warn("Rate limit exceeded - linkId: {}", linkId);
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                null,
                null,
                linkId,
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.RATE_LIMITED.getValue()));
        return Optional.of(DownloadOutcome.RATE_LIMITED);
    }

    private Optional<DownloadOutcome> requireValidLink(
            LinkValidationResult result, String token, String clientIp, String userAgent, String performedBy) {
        if (result.valid()) {
            return Optional.empty();
        }
        handleInvalidLink(result, token, clientIp, userAgent, performedBy);
        return Optional.of(getDownloadOutcome(result));
    }

    private Optional<DownloadOutcome> ensureFileExists(
            Statement statement, SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        try {
            if (statementService.fileExists(statement)) {
                return Optional.empty();
            }
            handleMissingFile(statement, link, token, clientIp, userAgent, performedBy);
            return Optional.of(DownloadOutcome.FILE_MISSING);
        } catch (StatementStorageUnavailableException e) {
            handleStorageUnavailable(
                    statement, link, token, clientIp, userAgent, performedBy, STAGE_EXISTENCE_CHECK, e);
            return Optional.of(DownloadOutcome.STORAGE_UNAVAILABLE);
        }
    }

    private DownloadOutcome getDownloadOutcome(LinkValidationResult result) {
        return switch (result.failureReason()) {
            case EXPIRED -> DownloadOutcome.LINK_EXPIRED;
            case NOT_FOUND -> DownloadOutcome.STATEMENT_NOT_FOUND;
            default -> DownloadOutcome.INVALID_SIGNATURE;
        };
    }

    private String getReason(LinkValidationResult result) {
        return switch (result.failureReason()) {
            case EXPIRED -> DownloadFailureReason.EXPIRED.getValue();
            case NOT_FOUND -> DownloadFailureReason.STATEMENT_NOT_FOUND.getValue();
            default -> DownloadFailureReason.INVALID.getValue();
        };
    }

    private Map<String, Object> getUserAuditDetails(String token, String clientIp, String userAgent, String reason) {
        var details = new HashMap<String, Object>();
        details.put(AuditDetailKeys.IP, clientIp != null ? clientIp : AuditDetailKeys.UNKNOWN);
        details.put(AuditDetailKeys.USER_AGENT, userAgent != null ? userAgent : AuditDetailKeys.UNKNOWN);
        details.put(AUDIT_KEY_TOKEN, maskToken(token));
        if (reason != null) {
            details.put(AuditDetailKeys.REASON, reason);
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
        var statementId = result.link() != null ? result.link().getStatementId() : null;
        var linkId = result.link() != null ? result.link().getId() : null;

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

    private DownloadOutcome decryptAndStream(
            Statement statement,
            SignedLink link,
            String token,
            String clientIp,
            String userAgent,
            String performedBy,
            Consumer<InputStream> onSuccess) {
        InputStream decrypted;
        try {
            decrypted = statementService.openDecryptedFile(statement);
        } catch (StatementStorageUnavailableException e) {
            handleStorageUnavailable(statement, link, token, clientIp, userAgent, performedBy, STAGE_OPENING_FILE, e);
            return DownloadOutcome.STORAGE_UNAVAILABLE;
        } catch (FileNotFoundException e) {
            // Object deleted between the exists() check and open().
            handleMissingFile(statement, link, token, clientIp, userAgent, performedBy);
            return DownloadOutcome.FILE_MISSING;
        } catch (Exception e) {
            log.error("Decryption failed - statementId: {}, error: {}", statement.getId(), e.getMessage(), e);
            var errorAuditDetails =
                    getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.DECRYPTION_FAILED.getValue());
            errorAuditDetails.put(AuditDetailKeys.ERROR, e.getMessage());

            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    errorAuditDetails);

            return DownloadOutcome.DECRYPTION_FAILED;
        }

        log.info("Download successful - statementId: {}", statement.getId());

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
        onSuccess.accept(decrypted);
        return DownloadOutcome.OK;
    }

    // Must be called before auditService.record(DOWNLOAD_SUCCESS, ...), not after: that call
    // submits asynchronously, so checking for prior redemptions here first avoids racing against
    // its own not-yet-committed write.
    private void checkForSuspiciousRedemption(SignedLink link, String clientIp, String userAgent) {
        try {
            if (auditService.hasPriorSuccessfulDownloadFromDifferentContext(link.getId(), clientIp, userAgent)) {
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
