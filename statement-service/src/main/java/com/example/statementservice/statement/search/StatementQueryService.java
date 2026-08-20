package com.example.statementservice.statement.search;

import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.StatementDto;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final String INVALID_DATE_FORMAT_MSG = "date must be in YYYY-MM-DD format";

    private final StatementService statementService;
    private final SignedLinkService signedLinkService;
    private final AuditHelper auditHelper;

    public Optional<StatementDto> getStatementWithSignedDownloadLinkById(
            UUID statementId, String accountNumber, RequestInfo requestInfo) {
        var performedBy = requestInfo.getPerformedBy();
        var clientIp = requestInfo.getClientIp();
        var userAgent = requestInfo.getUserAgent();

        try {
            var dto = this.statementService.getStatementDtoById(statementId);
            if (!dto.accountNumber().equals(accountNumber)) {
                auditHelper.recordStatementNotFound(statementId, performedBy, clientIp, userAgent);
                return Optional.empty();
            }

            try {
                var fileName = dto.fileName();
                var signedLink = this.signedLinkService.createSignedLink(dto.statementId(), performedBy, fileName);
                var signedDownloadLink = signedLinkService.buildSignedDownloadLink(signedLink, fileName);
                dto = dto.withDownloadLink(signedDownloadLink);
                auditHelper.recordLinkGenerated(
                        statementId, accountNumber, signedLink.getId(), performedBy, clientIp, userAgent);

            } catch (Exception linkException) {
                auditHelper.recordLinkGenerationFailed(
                        statementId, accountNumber, performedBy, linkException, clientIp, userAgent);
            }

            return Optional.of(dto);

        } catch (StatementNotFoundException e) {
            auditHelper.recordStatementNotFound(statementId, performedBy, clientIp, userAgent);
            return Optional.empty();
        } catch (Exception e) {
            auditHelper.recordUnexpectedError(statementId, null, performedBy, e, clientIp, userAgent);
            throw e;
        }
    }

    public List<StatementDto> searchByAccount(String accountNumber, Integer limit, Integer offset) {
        int effectiveLimit = (limit != null) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null) ? offset : DEFAULT_OFFSET;

        try {
            var statements = this.statementService.getStatementsDtoByAccountNumber(accountNumber);
            int fromIndex = Math.min(effectiveOffset, statements.size());
            int toIndex = Math.min(fromIndex + effectiveLimit, statements.size());
            return new ArrayList<>(statements.subList(fromIndex, toIndex));
        } catch (StatementNotFoundException e) {
            return new ArrayList<>();
        }
    }

    public List<StatementDto> searchByAccountAndDate(String accountNumber, String date) {
        var parsedDate = parseDate(date);
        return this.statementService
                .getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate)
                .map(List::of)
                .orElseGet(List::of);
    }

    public Page<StatementDto> searchPaged(
            String accountNumber, String startDate, String endDate, Integer page, Integer size, String sort) {

        int effectivePage = (page != null) ? page : DEFAULT_PAGE;
        int effectiveSize = (size != null) ? size : DEFAULT_SIZE;

        var sortOrder = parseSort(sort);

        var parsedStartDate = parseDate(startDate);
        var parsedEndDate = parseDate(endDate);

        if (parsedStartDate.isAfter(parsedEndDate)) {
            throw new InvalidInputException("startDate cannot be after endDate");
        }

        var pageRequest = PageRequest.of(effectivePage, effectiveSize, sortOrder);
        var statements = this.statementService.getStatementsByAccountNumberAndDateRange(
                accountNumber, parsedStartDate, parsedEndDate, pageRequest);
        var content = statements.map(statementService::toDto).getContent();
        return new PageImpl<>(content, pageRequest, statements.getTotalElements());
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

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new InvalidDateException(INVALID_DATE_FORMAT_MSG, e);
        }
    }

    private boolean isValidSortProperty(String property) {
        return Set.of("uploadedAt", "statementDate", "accountNumber", "id", "fileName", "fileSize")
                .contains(property);
    }
}
