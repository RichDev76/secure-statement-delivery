package com.example.statementservice.service;

import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.mapper.AuditApiMapper;
import com.example.statementservice.mapper.AuditLogEntityMapper;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.repository.AuditLogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEntityMapper auditLogEntityMapper;
    private final AuditApiMapper auditApiMapper;

    public AuditLogPage getFilteredAuditLogs(
            String accountNumber, String startDate, String endDate, Integer page, Integer size) {

        log.debug(
                "Querying audit logs: account={}, start={}, end={}, page={}, size={}",
                accountNumber,
                startDate,
                endDate,
                page,
                size);

        var startDateTime = parseDate(startDate, false);
        var endDateTime = parseDate(endDate, true);
        validateDateRange(startDateTime, endDateTime);

        int pageNum = normalizePageNumber(page);
        int pageSize = normalizePageSize(size);

        var pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "performedAt"));

        var auditLogPage = auditLogRepository.findFilteredAuditLogs(
                normalizeAccountNumber(accountNumber), startDateTime, endDateTime, pageable);

        var auditLogDtos = auditLogEntityMapper.toDtos(auditLogPage.getContent());
        var apiPage = auditApiMapper.toPage(auditLogDtos);

        apiPage.page(pageNum);
        apiPage.size(pageSize);
        apiPage.totalElements(auditLogPage.getTotalElements());
        apiPage.totalPages(auditLogPage.getTotalPages());

        log.debug(
                "Retrieved {} audit logs (page {} of {})", auditLogDtos.size(), pageNum, auditLogPage.getTotalPages());

        return apiPage;
    }

    private OffsetDateTime parseDate(String dateString, boolean isEndOfDay) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            var date = LocalDate.parse(dateString.trim());
            if (isEndOfDay) {
                return date.atTime(23, 59, 59, 999999999)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toOffsetDateTime();
            } else {
                return date.atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
            }
        } catch (DateTimeParseException e) {
            var dateType = isEndOfDay ? "end" : "start";
            throw new InvalidDateException(
                    "Invalid " + dateType + " date format. Expected YYYY-MM-DD, got: " + dateString, e);
        }
    }

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidDateException("Start date must be before or equal to end date");
        }
    }

    private String normalizeAccountNumber(String accountNumber) {
        return (accountNumber == null || accountNumber.isBlank()) ? null : accountNumber.trim();
    }

    private int normalizePageNumber(Integer page) {
        return page == null ? DEFAULT_PAGE : Math.max(0, page);
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }
}
