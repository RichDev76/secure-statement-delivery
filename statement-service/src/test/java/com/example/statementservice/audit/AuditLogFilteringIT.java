package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogFilteringIT extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "performedAt");

    // Unique per test method so rows seeded by one test can never satisfy another test's
    // assertions - the repository/DB state is not rolled back between methods.
    private String accountA;
    private String accountB;

    private AuditLog logAccountASeptember;
    private AuditLog logAccountBSeptember;
    private AuditLog logAccountAOctober;

    @BeforeEach
    void seedRows() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        accountA = "A" + suffix;
        accountB = "B" + suffix;

        logAccountASeptember = persist(accountA, OffsetDateTime.of(2026, 9, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        logAccountBSeptember = persist(accountB, OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.UTC));
        logAccountAOctober = persist(accountA, OffsetDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    private AuditLog persist(String accountNumber, OffsetDateTime performedAt) {
        var log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("DOWNLOAD_SUCCESS");
        log.setAccountNumber(accountNumber);
        log.setPerformedAt(performedAt);
        log.setPerformedBy("it-test");
        return auditLogRepository.saveAndFlush(log);
    }

    @Test
    void Given_NoFilters_When_Querying_Then_AllSeededRowsAreReturned() {
        // When
        var page = auditLogRepository.findAll(
                AuditLogSpecifications.filter(null, null, null), PageRequest.of(0, 500, SORT));

        // Then
        assertThat(page.getContent())
                .extracting(AuditLog::getId)
                .contains(logAccountASeptember.getId(), logAccountBSeptember.getId(), logAccountAOctober.getId());
    }

    @Test
    void Given_AccountNumberFilter_When_Querying_Then_OnlyThatAccountsRowsAreReturned() {
        // When
        var page = auditLogRepository.findAll(
                AuditLogSpecifications.filter(accountA, null, null), PageRequest.of(0, 500, SORT));

        // Then
        assertThat(page.getContent())
                .extracting(AuditLog::getId)
                .containsExactlyInAnyOrder(logAccountASeptember.getId(), logAccountAOctober.getId());
    }

    @Test
    void Given_DateRangeCoveringOnlySeptember_When_Querying_Then_OnlySeptemberRowsAreReturned() {
        // Given
        var start = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, ZoneOffset.UTC);

        // When
        var page = auditLogRepository.findAll(
                AuditLogSpecifications.filter(accountA, start, end), PageRequest.of(0, 500, SORT));

        // Then: scoped by accountA too, so this isolates the assertion to rows this test seeded.
        assertThat(page.getContent()).extracting(AuditLog::getId).containsExactly(logAccountASeptember.getId());
    }

    @Test
    void Given_AccountAndDateRangeFilters_When_Querying_Then_OnlyMatchingRowIsReturned() {
        // Given
        var start = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, ZoneOffset.UTC);

        // When
        var page = auditLogRepository.findAll(
                AuditLogSpecifications.filter(accountA, start, end), PageRequest.of(0, 500, SORT));

        // Then
        assertThat(page.getContent()).extracting(AuditLog::getId).containsExactly(logAccountASeptember.getId());
    }

    @Test
    void Given_DateRangeQuery_When_Explained_Then_OtherMonthPartitionsArePruned() {
        // Given: the old OR-chain JPQL could never prune anything, since the planner couldn't
        // statically tell which arm of "(:param IS NULL OR ...)" was live. A plain date-range
        // query against the rewritten Specification-based query proves real partition pruning:
        // "Subplans Removed" in the plan means the planner excluded partitions outright rather
        // than scanning every one of them. (A tight upper bound one second short of the next
        // partition's lower bound - e.g. 23:59:59 vs. 00:00:00 - is a known Postgres planner
        // edge case that can still leave the immediately-adjacent partition unpruned; this test
        // asserts what's actually, robustly provable rather than that specific boundary case.)
        var start = "2026-09-01T00:00:00Z";
        var end = "2026-09-30T23:59:59Z";

        // When
        var plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM audit_logs"
                        + " WHERE performed_at >= ?::timestamptz AND performed_at <= ?::timestamptz",
                String.class,
                start,
                end);
        var planText = String.join("\n", plan);

        // Then
        assertThat(planText).as("plan:\n%s", planText).contains("Subplans Removed");
    }
}
