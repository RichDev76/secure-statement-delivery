package com.example.statementservice.repository;

import com.example.statementservice.model.entity.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementRepository extends JpaRepository<Statement, UUID> {

    Page<Statement> findByAccountNumber(String accountNumber, Pageable pageable);

    Optional<Statement> findByAccountNumberAndStatementDate(String accountNumber, LocalDate statementDate);

    Optional<Statement> findStatementById(UUID id);

    @Query("SELECT s FROM Statement s WHERE s.accountNumber = :accountNumber ORDER BY s.statementDate DESC")
    Optional<List<Statement>> findAllByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("SELECT s FROM Statement s WHERE s.accountNumber = :accountNumber "
            + "AND (s.statementDate >= :startDate) "
            + "AND (s.statementDate <= :endDate)")
    Page<Statement> findByAccountNumberAndDateRange(
            @Param("accountNumber") String accountNumber,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
