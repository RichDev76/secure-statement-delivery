package com.example.statementservice.controller;

import com.example.statementservice.api.StatementsApi;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.service.DownloadService;
import com.example.statementservice.service.StatementQueryService;
import com.example.statementservice.util.DownloadResponseFactory;
import com.example.statementservice.util.RequestInfoProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementsController implements StatementsApi {

    private final DownloadService downloadService;
    private final StatementQueryService statementQueryService;
    private final RequestInfoProvider requestInfoProvider;
    private final DownloadResponseFactory downloadResponseFactory;

    @Override
    public ResponseEntity<Resource> downloadStatementByFileName(
            String fileName, Long expires, String signature, String xCorrelationId) {
        log.info("downloadStatementByFileName - fileName: {}", fileName);
        var requestInfo = requestInfoProvider.get();
        var result = downloadService.validateAndStreamDetailed(
                signature,
                expires,
                requestInfo.getClientIp(),
                requestInfo.getUserAgent(),
                requestInfo.getPerformedBy());
        return downloadResponseFactory.build(fileName, result);
    }

    @Override
    public ResponseEntity<StatementSummary> getDownloadSignedLinkById(UUID statementId, String xCorrelationId) {
        return statementQueryService
                .getStatementSummaryWithSignedDownloadLinkById(statementId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new StatementNotFoundException(
                        String.format("Statement(s) not found for Id: %s", statementId)));
    }

    @Override
    public ResponseEntity<StatementSummaryPage> searchStatements(
            String accountNumber,
            String startDate,
            String endDate,
            String xCorrelationId,
            Integer page,
            Integer size,
            String sort) {

        var pageResult = statementQueryService.searchPaged(accountNumber, startDate, endDate, page, size, sort);
        return ResponseEntity.ok(pageResult);
    }
}
