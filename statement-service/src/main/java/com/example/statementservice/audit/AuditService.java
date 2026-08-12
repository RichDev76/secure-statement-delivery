package com.example.statementservice.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ExecutorService auditExecutor;
    private final Clock clock;

    public void record(
            String action,
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            Map<String, Object> details) {
        var auditLog = buildAuditLog(action, statementId, accountNumber, signedLinkId, performedBy, details);
        auditExecutor.submit(() -> {
            try {
                auditLogRepository.save(auditLog);
            } catch (Exception e) {
                log.error("Failed to save audit log: {}", auditLog, e);
            }
        });
    }

    public List<AuditLog> getAllAuditLogs() {
        return this.auditLogRepository.findAll();
    }

    private AuditLog buildAuditLog(
            String action,
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            Map<String, Object> details) {
        var auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID());
        auditLog.setAction(action);
        auditLog.setStatementId(statementId);
        auditLog.setAccountNumber(accountNumber);
        auditLog.setSignedLinkId(signedLinkId);
        auditLog.setPerformedBy(performedBy);
        auditLog.setPerformedAt(OffsetDateTime.now(clock));
        auditLog.setDetails(details);
        return auditLog;
    }
}
