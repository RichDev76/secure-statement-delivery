package com.example.statementservice.audit;

import com.example.statementservice.shared.InvalidDateException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
public class AuditQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEntityMapper auditLogEntityMapper;
    private final Clock clock;

    public Page<AuditLogDto> getFilteredAuditLogs(
            String accountNumber, String startDate, String endDate, Integer page, Integer size) {

        log.debug("Querying audit logs: start={}, end={}, page={}, size={}", startDate, endDate, page, size);

        var startDateTime = parseDate(startDate, false);
        var endDateTime = parseDate(endDate, true);
        validateDateRange(startDateTime, endDateTime);

        int pageNum = normalizePageNumber(page);
        int pageSize = normalizePageSize(size);

        var pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "performedAt"));

        var specification = AuditLogSpecifications.filter(accountNumber.trim(), startDateTime, endDateTime);
        var auditLogPage = auditLogRepository.findAll(specification, pageable);

        var auditLogDtos = auditLogEntityMapper.toDtos(auditLogPage.getContent());

        log.debug(
                "Retrieved {} audit logs (page {} of {})", auditLogDtos.size(), pageNum, auditLogPage.getTotalPages());

        return new PageImpl<>(auditLogDtos, pageable, auditLogPage.getTotalElements());
    }

    private OffsetDateTime parseDate(String dateString, boolean isEndOfDay) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            var date = LocalDate.parse(dateString.trim());
            if (isEndOfDay) {
                return date.atTime(LocalTime.MAX).atZone(clock.getZone()).toOffsetDateTime();
            } else {
                return date.atStartOfDay(clock.getZone()).toOffsetDateTime();
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
