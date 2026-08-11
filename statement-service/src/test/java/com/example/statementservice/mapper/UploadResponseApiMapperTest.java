package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.dto.UploadResponseDto;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("UploadResponseApiMapper Tests")
class UploadResponseApiMapperTest {

    private final UploadResponseApiMapper uploadResponseApiMapper = Mappers.getMapper(UploadResponseApiMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadResponseApiMapper, "dateMapper", new DateMapper());
    }

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
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
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        var result = uploadResponseApiMapper.toApi(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
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
    @DisplayName("toApi - should handle zero file size")
    void toApi_ZeroFileSize() {
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
    @DisplayName("toApi - should handle large file size")
    void toApi_LargeFileSize() {
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
    @DisplayName("toApi - should preserve timestamp precision")
    void toApi_TimestampPrecision() {
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
    @DisplayName("toApi - should handle special characters in file name")
    void toApi_SpecialCharactersInFileName() {
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
    @DisplayName("toApi - should handle Unicode characters in file name")
    void toApi_UnicodeInFileName() {
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
    @DisplayName("toApi - should handle long file name")
    void toApi_LongFileName() {
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
    @DisplayName("toApi - should handle different UUID values")
    void toApi_DifferentUUIDs() {
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
    @DisplayName("toApi - should handle different timestamps")
    void toApi_DifferentTimestamps() {
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
    @DisplayName("toApi - should handle past timestamps")
    void toApi_PastTimestamp() {
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
    @DisplayName("toApi - should handle file name with dots")
    void toApi_FileNameWithDots() {
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
    @DisplayName("toApi - should handle file name with spaces")
    void toApi_FileNameWithSpaces() {
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
    @DisplayName("toApi - should handle minimal DTO with only required fields")
    void toApi_MinimalDto() {
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
    @DisplayName("toApi - should preserve all field values exactly")
    void toApi_PreservesExactValues() {
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
    @DisplayName("toApi - should handle very small file size")
    void toApi_VerySmallFileSize() {
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
    @DisplayName("toApi - should map DTO built with all builder methods")
    void toApi_BuilderPattern() {
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
    @DisplayName("toApi - should handle file name with path separators")
    void toApi_FileNameWithPathSeparators() {
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
