package com.example.statementservice.statement.upload;

import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditDetailKeys;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.shared.ContentDigest;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.DuplicateStatementException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.StatementUploadException;
import com.example.statementservice.statement.UploadedFile;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementUploadService {

    private final UploadRequestValidator uploadRequestValidator;
    private final StatementService statementService;
    private final AuditService auditService;
    private final ContentDigest contentDigest;

    public UploadResponseDto upload(
            String xMessageDigest, UploadedFile file, String accountNumber, String date, RequestInfo requestInfo) {
        var performedBy = requestInfo.performedBy() != null ? requestInfo.performedBy() : StatementService.ADMIN_USER;
        try {
            this.uploadRequestValidator.validateFileUploadInputs(file, accountNumber, date);
            var contentHash = computeContentHash(file);
            this.uploadRequestValidator.validateMessageDigest(contentHash, xMessageDigest);
            var dto = this.statementService.uploadStatement(
                    accountNumber, LocalDate.parse(date), file, performedBy, contentHash);
            auditUploadSuccess(accountNumber, requestInfo, dto.statementId(), performedBy);
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
            details.put(AuditDetailKeys.REASON, reasonFor(cause).getValue());
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

    private String computeContentHash(UploadedFile file) {
        try (var content = file.getInputStream()) {
            return contentDigest.hexOf(content);
        } catch (IOException e) {
            throw new DigestComputationException("Failed to compute file digest", e);
        }
    }

    private Map<String, Object> buildAuditDetails(RequestInfo requestInfo) {
        var details = new HashMap<String, Object>();
        details.put(AuditDetailKeys.IP, requestInfo.clientIp());
        details.put(AuditDetailKeys.USER_AGENT, requestInfo.userAgent());
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

    private String auditableAccountNumber(String accountNumber) {
        return uploadRequestValidator.isValidAccountNumber(accountNumber) ? accountNumber : null;
    }
}
