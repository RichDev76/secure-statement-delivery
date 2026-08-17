package com.example.statementservice.statement.upload.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.shared.DateMapper;
import com.example.statementservice.statement.upload.UploadResponseDto;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UploadResponseApiMapper Tests")
class UploadResponseApiMapperTest {

    private final UploadResponseApiMapper uploadResponseApiMapper = new UploadResponseApiMapperImpl(new DateMapper());

    @Test
    void GivenDtoWithAllFields_WhenMappingToApi_ThenAllFieldsAreMapped() {
        var statementId = UUID.randomUUID();
        var uploadedAt = OffsetDateTime.now();
        var dto = UploadResponseDto.builder()
                .statementId(statementId)
                .uploadedAt(uploadedAt)
                .fileSize(2048L)
                .fileName("statement.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getUploadedAt())
                .isEqualTo(uploadedAt
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
    }

    @Test
    void GivenNullDto_WhenMappingToApi_ThenReturnsNull() {
        var result = uploadResponseApiMapper.toApi(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenDtoWithNullFields_WhenMappingToApi_ThenNullsArePreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .fileName("test.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getUploadedAt()).isNull();
        assertThat(result.getFileSize()).isNull();
    }

    @Test
    void GivenZeroFileSize_WhenMappingToApi_ThenZeroIsMapped() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(0L)
                .fileName("empty.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    void GivenLargeFileSize_WhenMappingToApi_ThenExactSizeIsMapped() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(10_737_418_240L) // 10 GB
                .fileName("large.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(10_737_418_240L);
    }

    @Test
    void GivenPreciseTimestamp_WhenMappingToApi_ThenPrecisionIsPreserved() {
        var now = OffsetDateTime.now();
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(now)
                .fileSize(1024L)
                .fileName("test.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getUploadedAt()).isEqualTo(now);
        assertThat(result.getUploadedAt().getNano()).isEqualTo(now.getNano());
    }

    @Test
    void GivenSpecialCharacterFileName_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement (copy) [2024].pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("statement (copy) [2024].pdf");
    }

    @Test
    void GivenUnicodeFileName_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("报表-2024.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("报表-2024.pdf");
    }

    @Test
    void GivenLongFileName_WhenMappingToApi_ThenFileNameIsPreserved() {
        String longFileName = "very_long_statement_file_name_with_many_characters_"
                + "and_underscores_and_numbers_12345678901234567890.pdf";
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName(longFileName)
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo(longFileName);
    }

    @Test
    void GivenDifferentStatementIds_WhenMappingToApi_ThenEachIdIsMapped() {
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();
        var dto1 = UploadResponseDto.builder()
                .statementId(uuid1)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("file1.pdf")
                .build();
        var dto2 = UploadResponseDto.builder()
                .statementId(uuid2)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(2048L)
                .fileName("file2.pdf")
                .build();
        var result1 = uploadResponseApiMapper.toApi(dto1);
        var result2 = uploadResponseApiMapper.toApi(dto2);
        assertThat(result1.getStatementId()).isEqualTo(uuid1);
        assertThat(result2.getStatementId()).isEqualTo(uuid2);
        assertThat(result1.getStatementId()).isNotEqualTo(result2.getStatementId());
    }

    @Test
    void GivenDifferentTimestamps_WhenMappingToApi_ThenEachTimestampIsMapped() {
        var time1 = OffsetDateTime.now();
        var time2 = time1.plusHours(1);
        var dto1 = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(time1)
                .fileSize(1024L)
                .fileName("file1.pdf")
                .build();
        var dto2 = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(time2)
                .fileSize(1024L)
                .fileName("file2.pdf")
                .build();
        var result1 = uploadResponseApiMapper.toApi(dto1);
        var result2 = uploadResponseApiMapper.toApi(dto2);
        assertThat(result1.getUploadedAt()).isEqualTo(time1);
        assertThat(result2.getUploadedAt()).isEqualTo(time2);
        assertThat(result1.getUploadedAt()).isBefore(result2.getUploadedAt());
    }

    @Test
    void GivenPastTimestamp_WhenMappingToApi_ThenTimestampIsMapped() {
        var pastTime = OffsetDateTime.now().minusDays(30);
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(pastTime)
                .fileSize(1024L)
                .fileName("old-statement.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getUploadedAt()).isEqualTo(pastTime);
    }

    @Test
    void GivenFileNameWithDots_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement.backup.2024.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("statement.backup.2024.pdf");
    }

    @Test
    void GivenFileNameWithSpaces_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("monthly statement 2024.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("monthly statement 2024.pdf");
    }

    @Test
    void GivenMinimalDto_WhenMappingToApi_ThenRequiredFieldsAreMapped() {
        var statementId = UUID.randomUUID();
        var dto = UploadResponseDto.builder()
                .statementId(statementId)
                .fileName("minimal.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getFileName()).isEqualTo("minimal.pdf");
    }

    @Test
    void GivenPopulatedDto_WhenMappingToApi_ThenValuesArePreservedExactly() {
        var statementId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var uploadedAt = OffsetDateTime.parse("2024-01-15T10:30:45.123456789+00:00");
        var fileSize = 123456789L;
        var fileName = "exact-test.pdf";

        var dto = UploadResponseDto.builder()
                .statementId(statementId)
                .uploadedAt(uploadedAt)
                .fileSize(fileSize)
                .fileName(fileName)
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getUploadedAt())
                .isEqualTo(uploadedAt
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(result.getFileSize()).isEqualTo(fileSize);
        assertThat(result.getFileName()).isEqualTo(fileName);
    }

    @Test
    void GivenTinyFileSize_WhenMappingToApi_ThenExactSizeIsMapped() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1L)
                .fileName("tiny.pdf")
                .build();

        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(1L);
    }

    @Test
    void GivenBuilderConstructedDto_WhenMappingToApi_ThenAllFieldsAreMapped() {
        var id = UUID.randomUUID();
        var time = OffsetDateTime.now();
        var dto = UploadResponseDto.builder()
                .statementId(id)
                .uploadedAt(time)
                .fileSize(4096L)
                .fileName("builder-test.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getUploadedAt()).isEqualTo(time);
        assertThat(result.getFileSize()).isEqualTo(4096L);
        assertThat(result.getFileName()).isEqualTo("builder-test.pdf");
    }

    @Test
    void GivenFileNameWithPathSeparators_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("2024/01/statement.pdf")
                .build();
        var result = uploadResponseApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("2024/01/statement.pdf");
    }
}
