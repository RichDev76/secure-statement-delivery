package com.example.statementservice.service;

import com.example.statementservice.model.AuditAction;
import com.example.statementservice.model.dto.UploadResponseDto;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import com.example.statementservice.util.ValidationUtil;
import java.time.LocalDate;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StatementUploadService {

    public static final String ADMIN_USER = "admin";
    private final ValidationUtil validationUtil;
    private final RequestInfoProvider requestInfoProvider;
    private final StatementService statementService;
    private final AuditService auditService;

    public UploadResponseDto upload(String xMessageDigest, MultipartFile file, String accountNumber, String date) {
        this.validationUtil.validateFileUploadInputs(file, xMessageDigest, accountNumber, date);
        var requestInfo = requestInfoProvider.get();
        var performedBy = requestInfo.getPerformedBy() != null ? requestInfo.getPerformedBy() : ADMIN_USER;
        var dto = this.statementService.uploadStatement(accountNumber, LocalDate.parse(date), file, performedBy);
        auditUpload(accountNumber, requestInfo, dto, performedBy);
        return dto;
    }

    private void auditUpload(String accountNumber, RequestInfo requestInfo, UploadResponseDto dto, String performedBy) {
        try {
            var details = new HashMap<String, Object>();
            details.put("ip", requestInfo.getClientIp());
            details.put("userAgent", requestInfo.getUserAgent());
            auditService.record(
                    AuditAction.UPLOAD_SUCCESS.getValue(),
                    dto.getStatementId(),
                    accountNumber,
                    null,
                    performedBy,
                    details);
        } catch (Exception ignore) {
            // Avoid impacting upload response if audit logging fails
        }
    }
}
