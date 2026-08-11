package com.example.statementservice.service;

import com.example.statementservice.exception.InvalidInputException;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.mapper.StatementApiMapper;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.util.AuditHelper;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementQueryService {

    public static final String SYSTEM_USER = "system";
    private final StatementService statementService;
    private final StatementApiMapper statementApiMapper;
    private final SignedLinkService signedLinkService;
    private final AuditHelper auditHelper;
    private final RequestInfoProvider requestInfoProvider;

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;

    public Optional<StatementSummary> getStatementSummaryWithSignedDownloadLinkById(UUID statementId) {
        var requestInfo = getRequestInfo();
        var performedBy = requestInfo.getPerformedBy();
        var clientIp = requestInfo.getClientIp();
        var userAgent = requestInfo.getUserAgent();

        try {
            var dto = this.statementService.getStatementDtoById(statementId);
            var accountNumber = dto.getAccountNumber();

            try {
                var signedLinkBasePath = this.signedLinkService.getFilesBaseUrl(dto.getFileName());
                var signedLink = this.signedLinkService.createSignedLink(
                        dto.getStatementId(), true, performedBy, signedLinkBasePath);
                var signedDownloadLink = signedLinkService.buildSignedDownloadLink(signedLink, signedLinkBasePath);
                dto.setDownloadLink(signedDownloadLink);
                auditHelper.recordLinkGenerated(
                        statementId, accountNumber, signedLink.getId(), performedBy, clientIp, userAgent);

            } catch (Exception linkException) {
                auditHelper.recordLinkGenerationFailed(
                        statementId, accountNumber, performedBy, linkException, clientIp, userAgent);
            }

            return Optional.of(statementApiMapper.toApi(dto));

        } catch (StatementNotFoundException e) {
            auditHelper.recordStatementNotFound(statementId, performedBy, clientIp, userAgent);
            return Optional.empty();
        } catch (Exception e) {
            auditHelper.recordUnexpectedError(statementId, null, performedBy, e, clientIp, userAgent);
            throw e;
        }
    }

    private RequestInfo getRequestInfo() {
        try {
            return requestInfoProvider.get();
        } catch (Exception e) {
            log.warn("Failed to get request info from context", e);
            return new RequestInfo(null, null, SYSTEM_USER);
        }
    }

    public List<StatementSummary> searchByAccount(String accountNumber, Integer limit, Integer offset) {
        int effectiveLimit = (limit != null) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null) ? offset : DEFAULT_OFFSET;

        try {
            var statements = this.statementService.getStatementsDtoByAccountNumber(accountNumber);
            int fromIndex = Math.min(effectiveOffset, statements.size());
            int toIndex = Math.min(fromIndex + effectiveLimit, statements.size());
            var page = statements.subList(fromIndex, toIndex);
            return this.statementApiMapper.toApis(page);
        } catch (StatementNotFoundException e) {
            return new ArrayList<>();
        }
    }

    public List<StatementSummary> searchByAccountAndDate(String accountNumber, String date) {
        var parsedDate = LocalDate.parse(date);
        Optional<StatementDto> s =
                this.statementService.getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate);
        return s.map(v -> List.of(this.statementApiMapper.toApi(v))).orElseGet(List::of);
    }

    public StatementSummaryPage searchPaged(
            String accountNumber, String startDate, String endDate, Integer page, Integer size, String sort) {

        int effectivePage = (page != null) ? page : DEFAULT_PAGE;
        int effectiveSize = (size != null) ? size : DEFAULT_SIZE;

        var result = new StatementSummaryPage();
        result.page(effectivePage);
        result.size(effectiveSize);

        var sortOrder = parseSort(sort);

        var parsedStartDate = LocalDate.parse(startDate);
        var parsedEndDate = LocalDate.parse(endDate);

        if (parsedStartDate.isAfter(parsedEndDate)) {
            throw new InvalidInputException("startDate cannot be after endDate");
        }

        var statements = this.statementService.getStatementsByAccountNumberAndDateRange(
                accountNumber, parsedStartDate, parsedEndDate, PageRequest.of(effectivePage, effectiveSize, sortOrder));
        var content = statements
                .map(stmt -> statementApiMapper.toBase(statementService.toDto(stmt)))
                .getContent();
        result.setContent(content);
        result.totalElements(statements.getTotalElements());
        result.totalPages(statements.getTotalPages());
        return result;
    }

    private Sort parseSort(String sort) {
        var defaultSort = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }

        try {
            var parts = sort.split(",");
            if (parts.length < 2) {
                log.warn("Invalid sort format '{}', using default sort", sort);
                return defaultSort;
            }

            var property = parts[0].trim();
            var direction = parts[1].trim().toLowerCase();

            if (!isValidSortProperty(property)) {
                log.warn("Invalid sort property '{}', using default sort", property);
                return defaultSort;
            }

            Sort.Direction sortDirection = "asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

            return Sort.by(sortDirection, property);
        } catch (Exception e) {
            log.warn("Failed to parse sort parameter '{}', using default sort: {}", sort, e.getMessage());
            return defaultSort;
        }
    }

    private boolean isValidSortProperty(String property) {
        return Set.of("uploadedAt", "statementDate", "accountNumber", "id", "fileName", "fileSize")
                .contains(property);
    }
}
