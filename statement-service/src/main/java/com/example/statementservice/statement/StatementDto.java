package com.example.statementservice.statement;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record StatementDto(
        UUID statementId,
        String accountNumber,
        LocalDate statementDate,
        OffsetDateTime uploadedAt,
        Long fileSize,
        String fileName,
        URI downloadLink) {

    public StatementDto withDownloadLink(URI downloadLink) {
        return toBuilder().downloadLink(downloadLink).build();
    }
}
