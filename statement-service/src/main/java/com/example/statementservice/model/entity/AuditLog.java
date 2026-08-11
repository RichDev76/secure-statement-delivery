package com.example.statementservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    private UUID id;

    private String action;

    @Column(name = "statement_id")
    private UUID statementId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "signed_link_id")
    private UUID signedLinkId;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "performed_at")
    private OffsetDateTime performedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;
}
