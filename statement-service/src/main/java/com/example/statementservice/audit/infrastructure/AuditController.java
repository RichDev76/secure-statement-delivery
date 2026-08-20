package com.example.statementservice.audit.infrastructure;

import com.example.statementservice.api.AuditApi;
import com.example.statementservice.audit.AuditQueryService;
import com.example.statementservice.infrastructure.security.AppRole;
import com.example.statementservice.model.api.AuditLogPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class AuditController implements AuditApi {

    private final AuditQueryService auditQueryService;
    private final AuditApiMapper auditApiMapper;

    @Override
    @PreAuthorize("hasRole('" + AppRole.AUDIT_LOGS_SEARCH + "')")
    public ResponseEntity<AuditLogPage> getFilteredAuditLogs(
            String accountNumber, String xCorrelationId, String startDate, String endDate, Integer page, Integer size) {
        var dtoPage = auditQueryService.getFilteredAuditLogs(accountNumber, startDate, endDate, page, size);
        var apiPage = auditApiMapper.toPage(dtoPage.getContent());
        apiPage.page(dtoPage.getNumber());
        apiPage.size(dtoPage.getSize());
        apiPage.totalElements(dtoPage.getTotalElements());
        apiPage.totalPages(dtoPage.getTotalPages());
        return ResponseEntity.ok(apiPage);
    }
}
