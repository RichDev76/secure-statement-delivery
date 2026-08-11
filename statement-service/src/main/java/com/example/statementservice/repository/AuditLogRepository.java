package com.example.statementservice.repository;

import com.example.statementservice.model.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query(
            """
        SELECT a FROM AuditLog a
        WHERE (:accountNumber IS NULL OR a.accountNumber = :accountNumber)
        AND (CAST(:startDate AS timestamp) IS NULL OR a.performedAt >= :startDate)
        AND (CAST(:endDate AS timestamp) IS NULL OR a.performedAt <= :endDate)
        ORDER BY a.performedAt DESC
        """)
    Page<AuditLog> findFilteredAuditLogs(
            @Param("accountNumber") String accountNumber,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);
}
