package com.example.statementservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "signed_links")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SignedLink {

    @Id
    private UUID id;

    @Column(name = "statement_id")
    private UUID statementId;

    private String token;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "single_use")
    private boolean singleUse;

    private boolean used;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;
}
