package com.example.statementservice.controller;

import com.example.statementservice.api.AuditApi;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class AuditController implements AuditApi {

    private final AuditQueryService auditQueryService;

    @Override
    public ResponseEntity<AuditLogPage> getFilteredAuditLogs(
            String xCorrelationId, String accountNumber, String startDate, String endDate, Integer page, Integer size) {
        var apiPage = auditQueryService.getFilteredAuditLogs(accountNumber, startDate, endDate, page, size);
        return ResponseEntity.ok(apiPage);
    }
}
