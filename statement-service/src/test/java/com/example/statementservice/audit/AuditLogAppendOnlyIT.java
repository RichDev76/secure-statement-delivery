package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statementservice.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogAppendOnlyIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID rowId;

    @BeforeEach
    void insertAuditRow() {
        rowId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO audit_logs (id, action, performed_by, performed_at) VALUES (?, ?, ?, now())",
                rowId,
                "DOWNLOAD_SUCCESS",
                "append-only-test");
    }

    @Test
    void GivenPersistedAuditRow_WhenUpdated_ThenDatabaseRejectsMutation() {
        // When / Then
        assertThatThrownBy(() ->
                        jdbcTemplate.update("UPDATE audit_logs SET performed_by = 'tampered' WHERE id = ?", rowId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void GivenPersistedAuditRow_WhenDeleted_ThenDatabaseRejectsMutation() {
        // When / Then
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", rowId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void GivenAppendOnlyTrigger_WhenTruncating_ThenDatabaseRejectsMutation() {
        // When / Then
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void GivenAppendOnlyTrigger_WhenInserting_ThenInsertsStillSucceed() {
        // When
        var inserted = jdbcTemplate.update(
                "INSERT INTO audit_logs (id, action, performed_by, performed_at) VALUES (?, ?, ?, now())",
                UUID.randomUUID(),
                "UPLOAD_SUCCESS",
                "append-only-test");

        // Then
        assertThat(inserted).isEqualTo(1);
    }
}
