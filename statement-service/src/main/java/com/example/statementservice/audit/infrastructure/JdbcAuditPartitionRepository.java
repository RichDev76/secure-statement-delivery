package com.example.statementservice.audit.infrastructure;

import com.example.statementservice.audit.AuditPartitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAuditPartitionRepository implements AuditPartitionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void createUpcomingPartitions(int monthsAhead) {
        jdbcTemplate.query("SELECT create_audit_partitions(?)", (ResultSetExtractor<Void>) rs -> null, monthsAhead);
    }

    @Override
    public int countDefaultPartitionRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs_default", Integer.class);
        return count == null ? 0 : count;
    }
}
