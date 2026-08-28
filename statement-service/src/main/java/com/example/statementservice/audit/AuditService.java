package com.example.statementservice.audit;

import com.example.statementservice.shared.IdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    // Alertable signal that audit entries are actually being lost.
    public static final String AUDIT_DROPPED_METRIC = "statement.audit.dropped";

    private static final String ACTION_TAG = "action";

    private final AuditLogRepository auditLogRepository;
    private final ExecutorService auditExecutor;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public void record(
            String action,
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            Map<String, Object> details) {
        var auditLog = buildAuditLog(action, statementId, accountNumber, signedLinkId, performedBy, details);
        try {
            auditExecutor.submit(() -> {
                try {
                    auditLogRepository.save(auditLog);
                } catch (Exception e) {
                    recordDroppedAudit(auditLog);
                    log.error(
                            "Failed to save audit log: id={}, action={}, statementId={}",
                            auditLog.getId(),
                            auditLog.getAction(),
                            auditLog.getStatementId(),
                            e);
                }
            });
        } catch (RejectedExecutionException e) {
            recordDroppedAudit(auditLog);
            // A shutting-down executor must not turn in-flight requests into 500s.
            log.warn(
                    "Audit executor rejected task - action={}, statementId={}",
                    auditLog.getAction(),
                    auditLog.getStatementId(),
                    e);
        }
    }

    // Query side of the audit trail's detail schema - keeps callers from reaching into
    // AuditLogRepository directly and re-interpreting the details map themselves.
    public boolean hasPriorSuccessfulDownloadFromDifferentContext(
            UUID signedLinkId, String clientIp, String userAgent) {
        var priorSuccesses =
                auditLogRepository.findBySignedLinkIdAndAction(signedLinkId, AuditAction.DOWNLOAD_SUCCESS.getValue());
        return priorSuccesses.stream()
                .anyMatch(prior -> !Objects.equals(prior.getDetails().get(AuditDetailKeys.IP), clientIp)
                        || !Objects.equals(prior.getDetails().get(AuditDetailKeys.USER_AGENT), userAgent));
    }

    private void recordDroppedAudit(AuditLog auditLog) {
        meterRegistry
                .counter(AUDIT_DROPPED_METRIC, ACTION_TAG, auditLog.getAction())
                .increment();
    }

    private AuditLog buildAuditLog(
            String action,
            UUID statementId,
            String accountNumber,
            UUID signedLinkId,
            String performedBy,
            Map<String, Object> details) {
        var auditLog = new AuditLog();
        auditLog.setId(idGenerator.newId());
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
