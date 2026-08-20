package com.example.statementservice.statement.infrastructure;

import com.example.statementservice.api.StatementsApi;
import com.example.statementservice.infrastructure.security.AppRole;
import com.example.statementservice.infrastructure.security.PublicEndpoint;
import com.example.statementservice.infrastructure.web.RequestInfoProvider;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.download.DownloadService;
import com.example.statementservice.statement.download.infrastructure.DownloadResponseFactory;
import com.example.statementservice.statement.search.StatementQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementsController implements StatementsApi {

    private final DownloadService downloadService;
    private final StatementQueryService statementQueryService;
    private final StatementApiMapper statementApiMapper;
    private final RequestInfoProvider requestInfoProvider;
    private final DownloadResponseFactory downloadResponseFactory;

    @Override
    @PublicEndpoint(reason = "The HMAC-signed, expiring link is the authorization - see ADR 0003")
    public ResponseEntity<Resource> downloadStatementByFileName(
            String fileName, Long expires, UUID linkId, String signature, String xCorrelationId) {
        var requestInfo = requestInfoProvider.get();
        var result = downloadService.validateAndStreamDetailed(
                signature,
                expires,
                linkId,
                fileName,
                requestInfo.getClientIp(),
                requestInfo.getUserAgent(),
                requestInfo.getPerformedBy());
        return downloadResponseFactory.build(fileName, result);
    }

    @Override
    @PreAuthorize("hasRole('" + AppRole.GENERATE_SIGNED_LINK + "')")
    public ResponseEntity<StatementSummary> getDownloadSignedLinkById(UUID statementId, String xCorrelationId) {
        var requestInfo = requestInfoProvider.get();
        return statementQueryService
                .getStatementWithSignedDownloadLinkById(statementId, requestInfo)
                .map(statementApiMapper::toApi)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new StatementNotFoundException(
                        String.format("Statement(s) not found for Id: %s", statementId)));
    }

    @Override
    @PreAuthorize("hasRole('" + AppRole.SEARCH + "')")
    public ResponseEntity<StatementSummaryPage> searchStatements(
            String accountNumber,
            String startDate,
            String endDate,
            String xCorrelationId,
            Integer page,
            Integer size,
            String sort) {

        var dtoPage = statementQueryService.searchPaged(accountNumber, startDate, endDate, page, size, sort);
        var apiPage = new StatementSummaryPage();
        apiPage.page(dtoPage.getNumber());
        apiPage.size(dtoPage.getSize());
        apiPage.setContent(statementApiMapper.toBases(dtoPage.getContent()));
        apiPage.totalElements(dtoPage.getTotalElements());
        apiPage.totalPages(dtoPage.getTotalPages());
        return ResponseEntity.ok(apiPage);
    }
}
