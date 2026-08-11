package com.example.statementservice.util;

import com.example.statementservice.model.AuditAction;
import com.example.statementservice.service.AuditService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditHelper {
    private static final String AUDIT_KEY_MESSAGE = "message";
    private static final String AUDIT_KEY_ERROR = "error";
    private static final String AUDIT_KEY_IP = "ip";
    private static final String AUDIT_KEY_USER_AGENT = "userAgent";
    private static final String AUDIT_UNKNOWN = "unknown";

    private final AuditService auditService;

    /**
     * Records successful link generation audit event with request context
     */
    public void recordLinkGenerated(
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            String clientIp,
            String userAgent) {
        log.info(
                "Link generated successfully - statementId: {}, accountNumber: {}, signedLinkId: {}, performedBy: {}",
                statementId,
                accountNumber,
                signedLinkId,
                performedBy);

        auditService.record(
                AuditAction.LINK_GENERATED.getValue(),
                statementId,
                accountNumber,
                signedLinkId,
                performedBy,
                buildDetails("Link generated successfully", null, clientIp, userAgent));
    }

    /**
     * Records link generation failure due to link creation error
     */
    public void recordLinkGenerationFailed(
            UUID statementId,
            String accountNumber,
            String performedBy,
            Exception ex,
            String clientIp,
            String userAgent) {
        log.error(
                "Failed to generate download link - statementId: {}, accountNumber: {}, performedBy: {}",
                statementId,
                accountNumber,
                performedBy,
                ex);

        auditService.record(
                AuditAction.LINK_GENERATION_FAILED.getValue(),
                statementId,
                accountNumber,
                null,
                performedBy,
                buildDetails("Failed to generate download link", ex.getMessage(), clientIp, userAgent));
    }

    /**
     * Records link generation failure due to statement not found
     */
    public void recordStatementNotFound(UUID statementId, String performedBy, String clientIp, String userAgent) {
        log.warn("Statement not found - statementId: {}, performedBy: {}", statementId, performedBy);

        auditService.record(
                AuditAction.LINK_GENERATION_FAILED.getValue(),
                statementId,
                null,
                null,
                performedBy,
                buildDetails("Statement not found", null, clientIp, userAgent));
    }

    /**
     * Records link generation failure due to unexpected error
     */
    public void recordUnexpectedError(
            UUID statementId,
            String accountNumber,
            String performedBy,
            Exception ex,
            String clientIp,
            String userAgent) {
        log.error(
                "Unexpected error during link generation - statementId: {}, accountNumber: {}, performedBy: {}",
                statementId,
                accountNumber,
                performedBy,
                ex);

        auditService.record(
                AuditAction.LINK_GENERATION_FAILED.getValue(),
                statementId,
                accountNumber,
                null,
                performedBy,
                buildDetails("Unexpected error during link generation", ex.getMessage(), clientIp, userAgent));
    }

    private Map<String, Object> buildDetails(String message, String errorMessage, String clientIp, String userAgent) {
        var details = new HashMap<String, Object>();
        details.put(AUDIT_KEY_MESSAGE, message);
        details.put(AUDIT_KEY_IP, clientIp != null ? clientIp : AUDIT_UNKNOWN);
        details.put(AUDIT_KEY_USER_AGENT, userAgent != null ? userAgent : AUDIT_UNKNOWN);
        if (errorMessage != null) {
            details.put(AUDIT_KEY_ERROR, errorMessage);
        }
        return details;
    }
}
