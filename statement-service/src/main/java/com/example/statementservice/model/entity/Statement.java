package com.example.statementservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "statements")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Statement {

    @Id
    private UUID id;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "upload_file_name")
    private String uploadFileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_iv")
    private byte[] fileIv;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "encrypted")
    private boolean encrypted;

    @Column(name = "size_bytes")
    private Long sizeBytes;
}
