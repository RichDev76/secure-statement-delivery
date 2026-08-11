package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.dto.StatementDto;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("StatementApiMapper Tests")
class StatementApiMapperTest {

    private final StatementApiMapper statementApiMapper = Mappers.getMapper(StatementApiMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(statementApiMapper, "dateMapper", new DateMapper());
    }

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
        var statementId = UUID.randomUUID();
        var statementDate = LocalDate.of(2024, 1, 15);
        var uploadedAt = OffsetDateTime.now();
        var downloadLink = URI.create("https://example.com/download/statement.pdf");
        var dto = new StatementDto();
        dto.setStatementId(statementId);
        dto.setAccountNumber("123456789");
        dto.setStatementDate(statementDate);
        dto.setUploadedAt(uploadedAt);
        dto.setFileSize(2048L);
        dto.setFileName("statement.pdf");
        dto.setDownloadLink(downloadLink);
        var result = statementApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAccountNumber()).isEqualTo("123456789");
        assertThat(result.getDate()).isEqualTo("2024-01-15");
        assertThat(result.getUploadedAt())
                .isEqualTo(uploadedAt
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getDownloadLink()).isEqualTo(downloadLink);
    }

    @Test
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        var result = statementApiMapper.toApi(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileName("test.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getDate()).isNull();
        assertThat(result.getUploadedAt()).isNull();
        assertThat(result.getFileSize()).isNull();
        assertThat(result.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toApi - should convert LocalDate to String")
    void toApi_DateConversion() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setStatementDate(LocalDate.of(2024, 12, 25));
        dto.setFileName("test.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2024-12-25");
    }

    @Test
    @DisplayName("toApi - should handle null statement date")
    void toApi_NullStatementDate() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setStatementDate(null);
        dto.setFileName("test.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isNull();
    }

    @Test
    @DisplayName("toApi - should handle different date formats")
    void toApi_DifferentDates() {
        var dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        var dto2 = createStatementDto(LocalDate.of(2024, 12, 31));
        var dto3 = createStatementDto(LocalDate.of(2024, 2, 29)); // Leap year
        var result1 = statementApiMapper.toApi(dto1);
        var result2 = statementApiMapper.toApi(dto2);
        var result3 = statementApiMapper.toApi(dto3);
        assertThat(result1.getDate()).isEqualTo("2024-01-01");
        assertThat(result2.getDate()).isEqualTo("2024-12-31");
        assertThat(result3.getDate()).isEqualTo("2024-02-29");
    }

    @Test
    @DisplayName("toApi - should handle zero file size")
    void toApi_ZeroFileSize() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileSize(0L);
        dto.setFileName("empty.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toApi - should handle large file size")
    void toApi_LargeFileSize() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileSize(10_737_418_240L); // 10 GB
        dto.setFileName("large.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(10_737_418_240L);
    }

    @Test
    @DisplayName("toApi - should preserve download link URI")
    void toApi_DownloadLink() {
        var downloadLink =
                URI.create("https://example.com/api/v1/statements/download/file.pdf?expires=123&signature=abc");
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setDownloadLink(downloadLink);
        dto.setFileName("file.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDownloadLink()).isEqualTo(downloadLink);
        assertThat(result.getDownloadLink().toString()).contains("expires=123");
        assertThat(result.getDownloadLink().toString()).contains("signature=abc");
    }

    @Test
    @DisplayName("toApis - should map list of DTOs to list of API models")
    void toApis_MultipleItems() {
        var dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        dto1.setAccountNumber("ACC001");
        var dto2 = createStatementDto(LocalDate.of(2024, 2, 1));
        dto2.setAccountNumber("ACC002");
        var dto3 = createStatementDto(LocalDate.of(2024, 3, 1));
        dto3.setAccountNumber("ACC003");
        var dtos = Arrays.asList(dto1, dto2, dto3);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC001");
        assertThat(result.get(0).getDate()).isEqualTo("2024-01-01");
        assertThat(result.get(1).getAccountNumber()).isEqualTo("ACC002");
        assertThat(result.get(1).getDate()).isEqualTo("2024-02-01");
        assertThat(result.get(2).getAccountNumber()).isEqualTo("ACC003");
        assertThat(result.get(2).getDate()).isEqualTo("2024-03-01");
    }

    @Test
    @DisplayName("toApis - should handle null list")
    void toApis_NullList() {
        List<StatementSummary> result = statementApiMapper.toApis(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApis - should handle empty list")
    void toApis_EmptyList() {
        List<StatementDto> emptyList = Collections.emptyList();
        var result = statementApiMapper.toApis(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toApis - should handle single item list")
    void toApis_SingleItem() {
        var dto = createStatementDto(LocalDate.of(2024, 6, 15));
        dto.setAccountNumber("ACC999");
        var dtos = Collections.singletonList(dto);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC999");
        assertThat(result.get(0).getDate()).isEqualTo("2024-06-15");
    }

    @Test
    @DisplayName("toApis - should handle large list")
    void toApis_LargeList() {
        var dtos = new ArrayList<StatementDto>();
        for (int i = 0; i < 100; i++) {
            StatementDto dto = createStatementDto(LocalDate.of(2024, 1, 1).plusDays(i));
            dto.setAccountNumber("ACC" + i);
            dtos.add(dto);
        }
        List<StatementSummary> result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(100);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).getAccountNumber()).isEqualTo("ACC99");
    }

    @Test
    @DisplayName("toApis - should preserve all properties in mapped items")
    void toApis_PreservesAllProperties() {
        var statementId = UUID.randomUUID();
        var date = LocalDate.of(2024, 7, 20);
        var uploadedAt = OffsetDateTime.now();
        var downloadLink = URI.create("https://example.com/download/test.pdf");
        var dto = new StatementDto();
        dto.setStatementId(statementId);
        dto.setAccountNumber("ACC555");
        dto.setStatementDate(date);
        dto.setUploadedAt(uploadedAt);
        dto.setFileSize(4096L);
        dto.setFileName("test.pdf");
        dto.setDownloadLink(downloadLink);
        var dtos = Collections.singletonList(dto);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(1);
        var summary = result.get(0);
        assertThat(summary.getStatementId()).isEqualTo(statementId);
        assertThat(summary.getAccountNumber()).isEqualTo("ACC555");
        assertThat(summary.getDate()).isEqualTo("2024-07-20");
        assertThat(summary.getUploadedAt())
                .isEqualTo(uploadedAt
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(summary.getFileSize()).isEqualTo(4096L);
        assertThat(summary.getFileName()).isEqualTo("test.pdf");
        assertThat(summary.getDownloadLink()).isEqualTo(downloadLink);
    }

    @Test
    @DisplayName("toApis - should handle list with null elements")
    void toApis_WithNullElements() {
        var dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        var dto2 = createStatementDto(LocalDate.of(2024, 2, 1));
        var dtos = Arrays.asList(dto1, null, dto2);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isNotNull();
        assertThat(result.get(1)).isNull();
        assertThat(result.get(2)).isNotNull();
    }

    @Test
    @DisplayName("toApis - should handle list with items having null dates")
    void toApis_WithNullDates() {
        var dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        var dto2 = createStatementDto(null);
        var dto3 = createStatementDto(LocalDate.of(2024, 3, 1));
        var dtos = Arrays.asList(dto1, dto2, dto3);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDate()).isEqualTo("2024-01-01");
        assertThat(result.get(1).getDate()).isNull();
        assertThat(result.get(2).getDate()).isEqualTo("2024-03-01");
    }

    @Test
    @DisplayName("toApi - should handle special characters in file name")
    void toApi_SpecialCharactersInFileName() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileName("statement (copy) [2024].pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("statement (copy) [2024].pdf");
    }

    @Test
    @DisplayName("toApi - should handle Unicode characters in account number")
    void toApi_UnicodeInAccountNumber() {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setAccountNumber("账户123456");
        dto.setFileName("test.pdf");
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getAccountNumber()).isEqualTo("账户123456");
    }

    @Test
    @DisplayName("toApi - should handle past dates")
    void toApi_PastDates() {
        var dto = createStatementDto(LocalDate.of(2020, 1, 1));
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2020-01-01");
    }

    @Test
    @DisplayName("toApi - should handle future dates")
    void toApi_FutureDates() {
        var dto = createStatementDto(LocalDate.of(2030, 12, 31));
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2030-12-31");
    }

    // Helper method
    private StatementDto createStatementDto(LocalDate statementDate) {
        var dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setAccountNumber("123456789");
        dto.setStatementDate(statementDate);
        dto.setUploadedAt(OffsetDateTime.now());
        dto.setFileSize(1024L);
        dto.setFileName("statement.pdf");
        dto.setDownloadLink(URI.create("https://example.com/download/statement.pdf"));
        return dto;
    }
}
