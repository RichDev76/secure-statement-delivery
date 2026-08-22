package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditPartitionMaintenanceServiceIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditPartitionRepository auditPartitionRepository;

    @Autowired
    private AuditPartitionMaintenanceProperties properties;

    private AuditPartitionMaintenanceService service() {
        return new AuditPartitionMaintenanceService(auditPartitionRepository, properties);
    }

    private boolean partitionExists(String name) {
        var count = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_class WHERE relname = ?", Integer.class, name);
        return count != null && count > 0;
    }

    @Test
    void Given_MonthsAheadOfThree_When_MaintenanceRuns_Then_ThreeFuturePartitionsExist() {
        // Given
        properties.setMonthsAhead(3);

        // When
        service().createUpcomingPartitions();

        // Then: V7 already created 2026_09/2026_10; the frontier-anchored function should now
        // also reach 2026_11.
        assertThat(partitionExists("audit_logs_2026_11")).isTrue();
    }

    @Test
    void Given_MaintenanceAlreadyRan_When_RunningAgain_Then_ReRunIsIdempotent() {
        // Given
        service().createUpcomingPartitions();

        // When / Then: a second run must not throw (partitions already exist for the frontier)
        service().createUpcomingPartitions();
    }

    @Test
    void Given_MaintenanceHasRun_When_InspectingDefaultPartition_Then_ItRemainsEmpty() {
        // When
        service().createUpcomingPartitions();

        // Then
        var strayRows = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs_default", Integer.class);
        assertThat(strayRows).isZero();
    }
}
