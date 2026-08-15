package com.example.statementservice.audit;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> filter(
            String accountNumber, OffsetDateTime startDate, OffsetDateTime endDate) {
        return Specification.allOf(
                accountNumberEquals(accountNumber), performedAtFrom(startDate), performedAtTo(endDate));
    }

    private static Specification<AuditLog> accountNumberEquals(String accountNumber) {
        return (root, query, cb) -> accountNumber == null ? null : cb.equal(root.get("accountNumber"), accountNumber);
    }

    private static Specification<AuditLog> performedAtFrom(OffsetDateTime startDate) {
        return (root, query, cb) ->
                startDate == null ? null : cb.greaterThanOrEqualTo(root.get("performedAt"), startDate);
    }

    private static Specification<AuditLog> performedAtTo(OffsetDateTime endDate) {
        return (root, query, cb) -> endDate == null ? null : cb.lessThanOrEqualTo(root.get("performedAt"), endDate);
    }
}
