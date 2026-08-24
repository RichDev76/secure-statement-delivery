package com.example.statementservice.statement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.shared.DateMapper;
import com.example.statementservice.statement.StatementDto;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StatementApiMapper Tests")
class StatementApiMapperTest {

    private final StatementApiMapper statementApiMapper =
            new StatementApiMapperImpl(new DateMapper(Clock.system(ZoneId.of("Africa/Johannesburg"))));

    @Test
    void GivenDtoWithAllFields_WhenMappingToApi_ThenAllFieldsAreMapped() {
        var statementId = UUID.randomUUID();
        var statementDate = LocalDate.of(2024, 1, 15);
        var uploadedAt = OffsetDateTime.now();
        var downloadLink = URI.create("https://example.com/download/statement.pdf");
        var dto = StatementDto.builder()
                .statementId(statementId)
                .accountNumber("123456789")
                .statementDate(statementDate)
                .uploadedAt(uploadedAt)
                .fileSize(2048L)
                .fileName("statement.pdf")
                .downloadLink(downloadLink)
                .build();
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
    void GivenNullDto_WhenMappingToApi_ThenReturnsNull() {
        var result = statementApiMapper.toApi(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenDtoWithNullFields_WhenMappingToApi_ThenNullsArePreserved() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .fileName("test.pdf")
                .build();
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
    void GivenStatementDate_WhenMappingToApi_ThenDateIsConvertedToString() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .statementDate(LocalDate.of(2024, 12, 25))
                .fileName("test.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2024-12-25");
    }

    @Test
    void GivenNullStatementDate_WhenMappingToApi_ThenDateIsNull() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .statementDate(null)
                .fileName("test.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isNull();
    }

    @Test
    void GivenDifferentDates_WhenMappingToApi_ThenEachDateIsMapped() {
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
    void GivenZeroFileSize_WhenMappingToApi_ThenZeroIsMapped() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .fileSize(0L)
                .fileName("empty.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    void GivenLargeFileSize_WhenMappingToApi_ThenExactSizeIsMapped() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .fileSize(10_737_418_240L) // 10 GB
                .fileName("large.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileSize()).isEqualTo(10_737_418_240L);
    }

    @Test
    void GivenDownloadLink_WhenMappingToApi_ThenLinkIsMapped() {
        var downloadLink =
                URI.create("https://example.com/api/v1/statements/download/file.pdf?expires=123&signature=abc");
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .downloadLink(downloadLink)
                .fileName("file.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDownloadLink()).isEqualTo(downloadLink);
        assertThat(result.getDownloadLink().toString()).contains("expires=123");
        assertThat(result.getDownloadLink().toString()).contains("signature=abc");
    }

    @Test
    void GivenMultipleDtos_WhenMappingToApis_ThenAllItemsAreMapped() {
        var dto1 = createStatementDto(LocalDate.of(2024, 1, 1)).toBuilder()
                .accountNumber("ACC001")
                .build();
        var dto2 = createStatementDto(LocalDate.of(2024, 2, 1)).toBuilder()
                .accountNumber("ACC002")
                .build();
        var dto3 = createStatementDto(LocalDate.of(2024, 3, 1)).toBuilder()
                .accountNumber("ACC003")
                .build();
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
    void GivenNullList_WhenMappingToApis_ThenReturnsNull() {
        List<StatementSummary> result = statementApiMapper.toApis(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenEmptyList_WhenMappingToApis_ThenReturnsEmptyList() {
        List<StatementDto> emptyList = Collections.emptyList();
        var result = statementApiMapper.toApis(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenSingleDto_WhenMappingToApis_ThenOneItemIsMapped() {
        var dto = createStatementDto(LocalDate.of(2024, 6, 15)).toBuilder()
                .accountNumber("ACC999")
                .build();
        var dtos = Collections.singletonList(dto);
        var result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC999");
        assertThat(result.get(0).getDate()).isEqualTo("2024-06-15");
    }

    @Test
    void GivenLargeList_WhenMappingToApis_ThenAllItemsAreMapped() {
        var dtos = new ArrayList<StatementDto>();
        for (int i = 0; i < 100; i++) {
            StatementDto dto = createStatementDto(LocalDate.of(2024, 1, 1).plusDays(i)).toBuilder()
                    .accountNumber("ACC" + i)
                    .build();
            dtos.add(dto);
        }
        List<StatementSummary> result = statementApiMapper.toApis(dtos);
        assertThat(result).hasSize(100);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).getAccountNumber()).isEqualTo("ACC99");
    }

    @Test
    void GivenDtos_WhenMappingToApis_ThenAllPropertiesArePreserved() {
        var statementId = UUID.randomUUID();
        var date = LocalDate.of(2024, 7, 20);
        var uploadedAt = OffsetDateTime.now();
        var downloadLink = URI.create("https://example.com/download/test.pdf");
        var dto = StatementDto.builder()
                .statementId(statementId)
                .accountNumber("ACC555")
                .statementDate(date)
                .uploadedAt(uploadedAt)
                .fileSize(4096L)
                .fileName("test.pdf")
                .downloadLink(downloadLink)
                .build();
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
    void GivenListWithNullElements_WhenMappingToApis_ThenNullsAreMappedAsNull() {
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
    void GivenListWithNullDates_WhenMappingToApis_ThenNullDatesArePreserved() {
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
    void GivenSpecialCharacterFileName_WhenMappingToApi_ThenFileNameIsPreserved() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .fileName("statement (copy) [2024].pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getFileName()).isEqualTo("statement (copy) [2024].pdf");
    }

    @Test
    void GivenUnicodeAccountNumber_WhenMappingToApi_ThenValueIsPreserved() {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .accountNumber("账户123456")
                .fileName("test.pdf")
                .build();
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getAccountNumber()).isEqualTo("账户123456");
    }

    @Test
    void GivenPastDate_WhenMappingToApi_ThenDateIsMapped() {
        var dto = createStatementDto(LocalDate.of(2020, 1, 1));
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2020-01-01");
    }

    @Test
    void GivenFutureDate_WhenMappingToApi_ThenDateIsMapped() {
        var dto = createStatementDto(LocalDate.of(2030, 12, 31));
        var result = statementApiMapper.toApi(dto);
        assertThat(result.getDate()).isEqualTo("2030-12-31");
    }

    // Helper method
    private StatementDto createStatementDto(LocalDate statementDate) {
        var dto = StatementDto.builder()
                .statementId(UUID.randomUUID())
                .accountNumber("123456789")
                .statementDate(statementDate)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement.pdf")
                .downloadLink(URI.create("https://example.com/download/statement.pdf"))
                .build();
        return dto;
    }
}
