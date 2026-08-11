package com.example.statementservice.service;

import com.example.statementservice.model.AuditAction;
import com.example.statementservice.model.DownloadFailureReason;
import com.example.statementservice.model.DownloadOutcome;
import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import com.example.statementservice.util.LinkValidationResult;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
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

    private final SignedLinkService signedLinkService;
    private final StatementRepository statementRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    public DownloadStreamResult validateAndStreamDetailed(
            String token, Long expires, String clientIp, String userAgent, String performedBy) {
        log.debug("Download request (detailed) - token: {}, ip: {}, user: {}", maskToken(token), clientIp, performedBy);

        // Step 1: Validate link
        var result = signedLinkService.validateAndConsume(token, expires);
        if (!result.isValid()) {
            handleInvalidLink(result, token, clientIp, userAgent, performedBy);
            var outcome = getDownloadOutcome(result);
            return new DownloadStreamResult(outcome, Optional.empty());
        }

        // Step 2: Fetch statement
        var link = result.getLink();
        Optional<Statement> statementOpt = statementRepository.findById(link.getStatementId());
        if (statementOpt.isEmpty()) {
            handleMissingStatement(link, token, clientIp, userAgent, performedBy);
            return new DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        }

        // Step 3: Verify file exists
        var statement = statementOpt.get();
        var storedFile = new File(statement.getFilePath());
        if (!storedFile.exists()) {
            handleMissingFile(statement, link, token, clientIp, userAgent, performedBy);
            return new DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
        }

        // Step 4: Decrypt and stream
        Optional<InputStream> streamResult =
                decryptAndStream(statement, link, storedFile, token, clientIp, userAgent, performedBy);
        if (streamResult.isPresent()) {
            return new DownloadStreamResult(DownloadOutcome.OK, streamResult);
        } else {
            return new DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());
        }
    }

    private DownloadOutcome getDownloadOutcome(LinkValidationResult result) {
        return switch (result.getFailureReason()) {
            case USED, EXPIRED -> DownloadOutcome.LINK_EXPIRED_OR_USED;
            case NOT_FOUND -> DownloadOutcome.STATEMENT_NOT_FOUND;
            default -> DownloadOutcome.INVALID_SIGNATURE;
        };
    }

    public record DownloadStreamResult(DownloadOutcome outcome, Optional<InputStream> stream) {}

    private String getReason(LinkValidationResult result) {
        return switch (result.getFailureReason()) {
            case USED -> DownloadFailureReason.USED.getValue();
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
        log.error("File not found - path: {}, statementId: {}", statement.getFilePath(), statement.getId());
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statement.getId(),
                statement.getAccountNumber(),
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.FILE_MISSING.getValue()));
    }

    private Optional<InputStream> decryptAndStream(
            Statement statement,
            SignedLink link,
            File storedFile,
            String token,
            String clientIp,
            String userAgent,
            String performedBy) {
        try {
            var decrypted = encryptionService.decryptFileToStream(storedFile);

            log.info(
                    "Download successful - statementId: {}, account: {}",
                    statement.getId(),
                    statement.getAccountNumber());

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
            return Optional.of(decrypted);
        } catch (Exception e) {
            log.error("Decryption failed - statementId: {}, error: {}", statement.getId(), e.getMessage(), e);
            var errorAuditDetails = new HashMap<>(getUserAuditDetails(
                    token, clientIp, userAgent, DownloadFailureReason.DECRYPTION_FAILED.getValue()));
            errorAuditDetails.put(AUDIT_KEY_ERROR, e.getMessage());

            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    errorAuditDetails);

            return Optional.empty();
        }
    }

    private String fetchAccountNumber(UUID statementId) {
        if (statementId == null) return null;
        return statementRepository
                .findById(statementId)
                .map(Statement::getAccountNumber)
                .orElse(null);
    }
}
