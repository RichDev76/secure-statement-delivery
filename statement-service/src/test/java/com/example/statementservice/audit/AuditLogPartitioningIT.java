package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogPartitioningIT extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void Given_MigrationsHaveRun_When_InspectingCatalog_Then_AuditLogsIsDeclaredPartitioned() {
        // When
        var isPartitioned = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = 'audit_logs'::regclass", Integer.class);

        // Then
        assertThat(isPartitioned).isEqualTo(1);
    }

    @Test
    void Given_MigrationsHaveRun_When_InspectingPartitions_Then_SeededAndDefaultPartitionsExist() {
        // When
        var partitionNames = jdbcTemplate.queryForList("""
                SELECT c.relname FROM pg_inherits pi
                JOIN pg_class c ON c.oid = pi.inhrelid
                WHERE pi.inhparent = 'audit_logs'::regclass
                """, String.class);

        // Then
        assertThat(partitionNames)
                .contains("audit_logs_2026_08", "audit_logs_2026_09", "audit_logs_2026_10", "audit_logs_default");
    }

    @Test
    void Given_RowWithCurrentTimestamp_When_Inserted_Then_ItLandsInTheCurrentMonthPartition() {
        // Given
        var performedAt = OffsetDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        var log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("DOWNLOAD_SUCCESS");
        log.setPerformedAt(performedAt);
        log.setPerformedBy("it-test");

        // When
        auditLogRepository.saveAndFlush(log);
        List<String> partitionOfRow = jdbcTemplate.queryForList(
                "SELECT tableoid::regclass::text FROM audit_logs WHERE id = ?", String.class, log.getId());

        // Then
        assertThat(partitionOfRow).containsExactly("audit_logs_2026_09");
    }

    @Test
    void Given_RowWithTimestampBeforeSeededPartitions_When_Inserted_Then_ItLandsInTheDefaultPartition() {
        // Given
        var performedAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("DOWNLOAD_SUCCESS");
        log.setPerformedAt(performedAt);
        log.setPerformedBy("it-test");

        // When
        auditLogRepository.saveAndFlush(log);
        List<String> partitionOfRow = jdbcTemplate.queryForList(
                "SELECT tableoid::regclass::text FROM audit_logs WHERE id = ?", String.class, log.getId());

        // Then
        assertThat(partitionOfRow).containsExactly("audit_logs_default");

        // Cleanup: other ITs (AuditPartitionMaintenanceServiceIT) assert audit_logs_default stays
        // empty against the same shared, non-rolled-back Testcontainers database.
        // audit_logs is append-only; cleanup needs the trigger escape hatch.
        jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_append_only");
        try {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", log.getId());
        } finally {
            jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER audit_logs_append_only");
        }
    }
}
