package com.example.statementservice.model.dto;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementDto {
    private UUID statementId;
    private String accountNumber;
    private LocalDate statementDate;
    private OffsetDateTime uploadedAt;
    private Long fileSize;
    private String fileName;
    private URI downloadLink;
}
