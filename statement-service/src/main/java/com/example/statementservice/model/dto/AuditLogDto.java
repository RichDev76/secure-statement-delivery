package com.example.statementservice.model.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private UUID id;
    private String accountNumber;
    private OffsetDateTime performedAt;
    private String performedBy;
    private UUID statementId;
    private Map<String, Object> details;
    private String action;
    private String ipAddress;
    private String userAgent;
}
