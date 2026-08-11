package com.example.statementservice.service;

import com.example.statementservice.model.entity.AuditLog;
import com.example.statementservice.repository.AuditLogRepository;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ExecutorService executor;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    public void record(
            String action,
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            Map<String, Object> details) {
        var auditLog = buildAuditLog(action, statementId, accountNumber, signedLinkId, performedBy, details);
        executor.submit(() -> {
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
        auditLog.setPerformedAt(OffsetDateTime.now());
        auditLog.setDetails(details);
        return auditLog;
    }
}
