package com.example.statementservice.statement.search;

import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.StatementDto;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.signedlink.SignedLinkGenerationException;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final String INVALID_DATE_FORMAT_MSG = "date must be in YYYY-MM-DD format";

    // Keys are the search response's field names; values are the entity property paths.
    private static final Map<String, String> SORTABLE_API_FIELDS_TO_ENTITY_PROPERTIES = Map.of(
            "uploadedAt", "uploadedAt",
            "date", "statementDate",
            "accountNumber", "accountNumber",
            "statementId", "id",
            "fileName", "uploadFileName",
            "fileSize", "sizeBytes");

    private final StatementService statementService;
    private final SignedLinkService signedLinkService;
    private final StatementSearchAuditRecorder auditRecorder;

    public Optional<StatementDto> getStatementWithSignedDownloadLinkById(
            UUID statementId, String accountNumber, RequestInfo requestInfo) {
        var performedBy = requestInfo.performedBy();
        var clientIp = requestInfo.clientIp();
        var userAgent = requestInfo.userAgent();

        try {
            var dto = this.statementService.getStatementDtoById(statementId);
            if (!dto.accountNumber().equals(accountNumber)) {
                auditRecorder.recordStatementNotFound(statementId, performedBy, clientIp, userAgent);
                return Optional.empty();
            }

            try {
                var fileName = dto.fileName();
                var signedLink = this.signedLinkService.createSignedLink(dto.statementId(), performedBy, fileName);
                var signedDownloadLink = signedLinkService.buildSignedDownloadLink(signedLink, fileName);
                dto = dto.withDownloadLink(signedDownloadLink);
                auditRecorder.recordLinkGenerated(
                        statementId, accountNumber, signedLink.getId(), performedBy, clientIp, userAgent);

            } catch (SignedLinkGenerationException | IllegalArgumentException linkException) {
                auditRecorder.recordLinkGenerationFailed(
                        statementId, accountNumber, performedBy, linkException, clientIp, userAgent);
            }

            return Optional.of(dto);

        } catch (StatementNotFoundException e) {
            auditRecorder.recordStatementNotFound(statementId, performedBy, clientIp, userAgent);
            return Optional.empty();
        } catch (Exception e) {
            auditRecorder.recordUnexpectedError(statementId, null, performedBy, e, clientIp, userAgent);
            throw e;
        }
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
            var direction = parts[1].trim().toLowerCase(Locale.ROOT);

            var entityProperty = SORTABLE_API_FIELDS_TO_ENTITY_PROPERTIES.get(property);
            if (entityProperty == null) {
                log.warn("Invalid sort property '{}', using default sort", property);
                return defaultSort;
            }

            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                log.warn("Invalid sort direction '{}', using default sort", direction);
                return defaultSort;
            }

            var sortDirection = "asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

            // Unique tiebreaker keeps paging stable when the sorted column has duplicate values.
            if ("id".equals(entityProperty)) {
                return Sort.by(sortDirection, entityProperty);
            }
            return Sort.by(sortDirection, entityProperty).and(Sort.by(Sort.Direction.DESC, "id"));
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
}
