package com.example.statementservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

class ApplicationContextSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void GivenBootedContext_WhenSerializingOffsetDateTime_ThenIso8601StringIsProduced() {
        // Given
        var timestamp = OffsetDateTime.of(2026, 8, 11, 12, 30, 15, 0, ZoneOffset.UTC);

        // When
        var json = jsonMapper.writeValueAsString(timestamp);

        // Then
        assertThat(json).startsWith("\"2026-08-11T12:30:15").endsWith("Z\"");
    }

    @Test
    void GivenFlywayMigrations_WhenContextStarts_ThenSchemaHistoryRecordsSuccessfulMigrations() {
        // Given / When: context startup ran Flyway against the Testcontainers database

        // Then
        var applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void GivenSecurityConfiguration_WhenContextStarts_ThenSecurityFilterChainBeanIsWired() {
        // Given / When: context startup wired SecurityConfig

        // Then
        assertThat(securityFilterChain).isNotNull();
    }
}
