package com.example.statementservice.statement.upload;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UploadResponseDto(UUID statementId, OffsetDateTime uploadedAt, Long fileSize, String fileName) {}
