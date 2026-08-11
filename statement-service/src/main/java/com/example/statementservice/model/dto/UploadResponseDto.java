package com.example.statementservice.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponseDto {
    private UUID statementId;
    private OffsetDateTime uploadedAt;
    private Long fileSize;
    private String fileName;
}
