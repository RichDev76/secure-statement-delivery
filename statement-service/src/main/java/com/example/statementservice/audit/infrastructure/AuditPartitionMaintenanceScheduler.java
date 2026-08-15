package com.example.statementservice.audit.infrastructure;

import com.example.statementservice.audit.AuditPartitionMaintenanceService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditPartitionMaintenanceScheduler {

    private final AuditPartitionMaintenanceService maintenanceService;

    @Scheduled(cron = "${statement.audit.partition-maintenance.cron}")
    @SchedulerLock(
            name = "statement.audit.partition-maintenance.job",
            lockAtMostFor = "#{@auditPartitionMaintenanceProperties.lockAtMostFor}",
            lockAtLeastFor = "#{@auditPartitionMaintenanceProperties.lockAtLeastFor}")
    public void runMaintenance() {
        maintenanceService.createUpcomingPartitions();
    }
}
