package com.example.statementservice.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AuditLogDto(
        UUID id,
        String accountNumber,
        OffsetDateTime performedAt,
        String performedBy,
        UUID statementId,
        Map<String, Object> details,
        String action,
        String ipAddress,
        String userAgent) {}
