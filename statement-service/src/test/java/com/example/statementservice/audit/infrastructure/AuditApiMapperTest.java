package com.example.statementservice.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.audit.AuditLogDto;
import com.example.statementservice.model.api.AuditLogEntry;
import com.example.statementservice.shared.DateMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuditApiMapper Tests")
class AuditApiMapperTest {

    private final AuditApiMapper auditApiMapper =
            new AuditApiMapperImpl(new DateMapper(Clock.system(ZoneId.of("Africa/Johannesburg"))));

    @Test
    void GivenDtoWithAllFields_WhenMappingToApi_ThenAllFieldsAreMapped() {
        var id = UUID.randomUUID();
        var statementId = UUID.randomUUID();
        var performedAt = OffsetDateTime.now();
        var details = new HashMap<String, Object>();
        details.put("key1", "value1");
        details.put("key2", 123);

        var dto = AuditLogDto.builder()
                .id(id)
                .accountNumber("123456789")
                .statementId(statementId)
                .action("DOWNLOAD_SUCCESS")
                .performedAt(performedAt)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .details(details)
                .build();

        var result = auditApiMapper.toApi(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("123456789");
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAction()).isEqualTo("DOWNLOAD_SUCCESS");
        assertThat(result.getTimestamp())
                .isEqualTo(performedAt
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.getDetails()).isEqualTo(details);
    }

    @Test
    void GivenNullDto_WhenMappingToApi_ThenReturnsNull() {
        var result = auditApiMapper.toApi(null);

        assertThat(result).isNull();
    }

    @Test
    void GivenDtoWithNullFields_WhenMappingToApi_ThenNullsArePreserved() {
        var dto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .action("TEST_ACTION")
                .build();

        var result = auditApiMapper.toApi(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getAction()).isEqualTo("TEST_ACTION");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getStatementId()).isNull();
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();

        if (result.getDetails() != null) {
            assertThat(result.getDetails()).isEmpty();
        }
    }

    @Test
    void GivenEmptyDetails_WhenMappingToApi_ThenDetailsAreEmpty() {
        var dto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .action("TEST_ACTION")
                .details(new HashMap<>())
                .build();

        var result = auditApiMapper.toApi(dto);

        assertThat(result).isNotNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    void GivenPerformedAtTimestamp_WhenMappingToApi_ThenTimestampIsMapped() {
        var now = OffsetDateTime.now();
        var dto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .action("TEST_ACTION")
                .performedAt(now)
                .build();

        var result = auditApiMapper.toApi(dto);

        assertThat(result.getTimestamp())
                .isEqualTo(
                        now.atZoneSameInstant(ZoneId.of("Africa/Johannesburg")).toOffsetDateTime());
    }

    @Test
    void GivenPageWithContent_WhenMappingToPage_ThenContentAndMetadataAreMapped() {
        var dto1 = createAuditLogDto("ACTION1", "ACC1");
        var dto2 = createAuditLogDto("ACTION2", "ACC2");
        var dto3 = createAuditLogDto("ACTION3", "ACC3");
        var dtos = Arrays.asList(dto1, dto2, dto3);

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("ACTION1");
        assertThat(result.getContent().get(1).getAction()).isEqualTo("ACTION2");
        assertThat(result.getContent().get(2).getAction()).isEqualTo("ACTION3");
    }

    @Test
    void GivenNullContentList_WhenMappingToPage_ThenContentIsEmpty() {
        var result = auditApiMapper.toPage(null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void GivenEmptyPage_WhenMappingToPage_ThenContentIsEmpty() {
        List<AuditLogDto> emptyList = Collections.emptyList();

        var result = auditApiMapper.toPage(emptyList);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void GivenSingleItemPage_WhenMappingToPage_ThenOneEntryIsMapped() {

        var dto = createAuditLogDto("SINGLE_ACTION", "ACC123");
        List<AuditLogDto> dtos = Collections.singletonList(dto);

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("SINGLE_ACTION");
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo("ACC123");
    }

    @Test
    void GivenLargePage_WhenMappingToPage_ThenAllEntriesAreMapped() {
        var dtos = new ArrayList<AuditLogDto>();
        for (int i = 0; i < 100; i++) {
            dtos.add(createAuditLogDto("ACTION" + i, "ACC" + i));
        }

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(100);
    }

    @Test
    void GivenPage_WhenMappingToPage_ThenAllPropertiesArePreserved() {
        var id = UUID.randomUUID();
        var statementId = UUID.randomUUID();
        var timestamp = OffsetDateTime.now();
        Map<String, Object> details = Collections.singletonMap("reason", "test");

        var dto = AuditLogDto.builder()
                .id(id)
                .accountNumber("ACC999")
                .statementId(statementId)
                .action("UPLOAD_SUCCESS")
                .performedAt(timestamp)
                .ipAddress("10.0.0.1")
                .userAgent("TestAgent")
                .details(details)
                .build();

        List<AuditLogDto> dtos = Collections.singletonList(dto);

        var result = auditApiMapper.toPage(dtos);

        assertThat(result.getContent()).hasSize(1);
        AuditLogEntry entry = result.getContent().get(0);
        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getAccountNumber()).isEqualTo("ACC999");
        assertThat(entry.getStatementId()).isEqualTo(statementId);
        assertThat(entry.getAction()).isEqualTo("UPLOAD_SUCCESS");
        assertThat(entry.getTimestamp())
                .isEqualTo(timestamp
                        .atZoneSameInstant(ZoneId.of("Africa/Johannesburg"))
                        .toOffsetDateTime());
        assertThat(entry.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(entry.getUserAgent()).isEqualTo("TestAgent");
        assertThat(entry.getDetails()).isEqualTo(details);
    }

    @Test
    void GivenPageWithNullElements_WhenMappingToPage_ThenNullsAreMappedAsNull() {
        var dto1 = createAuditLogDto("ACTION1", "ACC1");
        List<AuditLogDto> dtos = Arrays.asList(dto1, null, createAuditLogDto("ACTION2", "ACC2"));

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0)).isNotNull();
        assertThat(result.getContent().get(1)).isNull();
        assertThat(result.getContent().get(2)).isNotNull();
    }

    @Test
    void GivenComplexDetails_WhenMappingToApi_ThenDetailsAreMapped() {

        var complexDetails = new HashMap<String, Object>();
        complexDetails.put("string", "value");
        complexDetails.put("number", 42);
        complexDetails.put("boolean", true);
        complexDetails.put("nested", Collections.singletonMap("key", "value"));
        complexDetails.put("list", Arrays.asList(1, 2, 3));

        var dto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .action("COMPLEX_ACTION")
                .details(complexDetails)
                .build();

        var result = auditApiMapper.toApi(dto);

        assertThat(result.getDetails()).isEqualTo(complexDetails);
        assertThat(result.getDetails().get("string")).isEqualTo("value");
        assertThat(result.getDetails().get("number")).isEqualTo(42);
        assertThat(result.getDetails().get("boolean")).isEqualTo(true);
    }

    private AuditLogDto createAuditLogDto(String action, String accountNumber) {
        var dto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .action(action)
                .accountNumber(accountNumber)
                .performedAt(OffsetDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("TestAgent")
                .build();
        return dto;
    }
}
