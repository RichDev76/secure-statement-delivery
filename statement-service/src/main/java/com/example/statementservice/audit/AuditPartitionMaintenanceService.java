package com.example.statementservice.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitionMaintenanceService {

    private final AuditPartitionRepository auditPartitionRepository;
    private final AuditPartitionMaintenanceProperties properties;

    public void createUpcomingPartitions() {
        if (!properties.isEnabled()) {
            log.debug("Audit partition maintenance is disabled");
            return;
        }

        auditPartitionRepository.createUpcomingPartitions(properties.getMonthsAhead());
        log.info("Audit partition maintenance ran (monthsAhead={})", properties.getMonthsAhead());

        checkDefaultPartitionIsEmpty();
    }

    private void checkDefaultPartitionIsEmpty() {
        var strayRows = auditPartitionRepository.countDefaultPartitionRows();
        if (strayRows > 0) {
            log.error(
                    "audit_logs_default contains {} row(s) - partition creation has fallen behind"
                            + " and audit rows are landing in the safety-net partition",
                    strayRows);
        }
    }
}
