package com.example.statementservice.statement.upload;

import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.DuplicateStatementException;
import com.example.statementservice.statement.StatementService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementUploadService {

    public static final String ADMIN_USER = "admin";

    private static final String AUDIT_KEY_IP = "ip";
    private static final String AUDIT_KEY_USER_AGENT = "userAgent";
    private static final String AUDIT_KEY_REASON = "reason";
    // Mirrors ValidationUtil's account-number rule: a failure audit must not persist arbitrary
    // attacker or typo input as an account identifier.
    private static final String AUDITABLE_ACCOUNT_NUMBER_PATTERN = "^[0-9]{9,15}$";

    private final ValidationUtil validationUtil;
    private final StatementService statementService;
    private final AuditService auditService;

    public UploadResponseDto upload(
            String xMessageDigest, MultipartFile file, String accountNumber, String date, RequestInfo requestInfo) {
        var performedBy = requestInfo.getPerformedBy() != null ? requestInfo.getPerformedBy() : ADMIN_USER;
        try {
            this.validationUtil.validateFileUploadInputs(file, accountNumber, date);
            var contentHash = computeContentHash(file);
            this.validationUtil.validateMessageDigest(contentHash, xMessageDigest);
            var dto = this.statementService.uploadStatement(
                    accountNumber, LocalDate.parse(date), file, performedBy, contentHash);
            auditUploadSuccess(accountNumber, requestInfo, dto.getStatementId(), performedBy);
            return dto;
        } catch (RuntimeException e) {
            auditUploadFailure(accountNumber, requestInfo, performedBy, e);
            throw e;
        }
    }

    private void auditUploadSuccess(
            String accountNumber, RequestInfo requestInfo, UUID statementId, String performedBy) {
        try {
            auditService.record(
                    AuditAction.UPLOAD_SUCCESS.getValue(),
                    statementId,
                    accountNumber,
                    null,
                    performedBy,
                    buildAuditDetails(requestInfo));
        } catch (Exception auditEx) {
            // Fail-open: audit failures must not fail a successful upload.
            log.warn("Failed to record upload success audit - statementId: {}", statementId, auditEx);
        }
    }

    private void auditUploadFailure(
            String accountNumber, RequestInfo requestInfo, String performedBy, RuntimeException cause) {
        try {
            var details = buildAuditDetails(requestInfo);
            details.put(AUDIT_KEY_REASON, reasonFor(cause).getValue());
            auditService.record(
                    AuditAction.UPLOAD_FAILED.getValue(),
                    null,
                    auditableAccountNumber(accountNumber),
                    null,
                    performedBy,
                    details);
        } catch (Exception auditEx) {
            // Fail-open: the original business failure must propagate, not the audit failure.
            log.warn("Failed to record upload failure audit", auditEx);
        }
    }

    private static String computeContentHash(MultipartFile file) {
        try (var content = file.getInputStream()) {
            return Sha256Digest.hexOf(content);
        } catch (IOException e) {
            throw new DigestComputationException("Failed to compute file digest", e);
        }
    }

    private Map<String, Object> buildAuditDetails(RequestInfo requestInfo) {
        var details = new HashMap<String, Object>();
        details.put(AUDIT_KEY_IP, requestInfo.getClientIp());
        details.put(AUDIT_KEY_USER_AGENT, requestInfo.getUserAgent());
        return details;
    }

    private static UploadFailureReason reasonFor(RuntimeException cause) {
        return switch (cause) {
            case DigestMismatchException ignored -> UploadFailureReason.DIGEST_MISMATCH;
            case UnsupportedContentTypeException ignored -> UploadFailureReason.UNSUPPORTED_MEDIA_TYPE;
            case InvalidMessageDigestException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case MissingFileException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case InvalidAccountNumberException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case InvalidDateException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case PdfValidationException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case DigestComputationException ignored -> UploadFailureReason.VALIDATION_FAILED;
            case StatementUploadException ignored -> UploadFailureReason.UPLOAD_ERROR;
            case DuplicateStatementException ignored -> UploadFailureReason.DUPLICATE_STATEMENT;
            default -> UploadFailureReason.UNEXPECTED;
        };
    }

    private static String auditableAccountNumber(String accountNumber) {
        return accountNumber != null && accountNumber.matches(AUDITABLE_ACCOUNT_NUMBER_PATTERN) ? accountNumber : null;
    }
}
