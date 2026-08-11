package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.AuditLogEntry;
import com.example.statementservice.model.dto.AuditLogDto;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("AuditApiMapper Tests")
class AuditApiMapperTest {

    private final AuditApiMapper auditApiMapper = Mappers.getMapper(AuditApiMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditApiMapper, "dateMapper", new DateMapper());
    }

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
        var id = UUID.randomUUID();
        var statementId = UUID.randomUUID();
        var performedAt = OffsetDateTime.now();
        var details = new HashMap<String, Object>();
        details.put("key1", "value1");
        details.put("key2", 123);

        var dto = new AuditLogDto();
        dto.setId(id);
        dto.setAccountNumber("123456789");
        dto.setStatementId(statementId);
        dto.setAction("DOWNLOAD_SUCCESS");
        dto.setPerformedAt(performedAt);
        dto.setIpAddress("192.168.1.1");
        dto.setUserAgent("Mozilla/5.0");
        dto.setDetails(details);

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
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        var result = auditApiMapper.toApi(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
        var dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");

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
    @DisplayName("toApi - should handle empty details map")
    void toApi_EmptyDetails() {
        var dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");
        dto.setDetails(new HashMap<>());

        var result = auditApiMapper.toApi(dto);

        assertThat(result).isNotNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toApi - should map performedAt to timestamp")
    void toApi_TimestampMapping() {
        var now = OffsetDateTime.now();
        var dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");
        dto.setPerformedAt(now);

        var result = auditApiMapper.toApi(dto);

        assertThat(result.getTimestamp())
                .isEqualTo(
                        now.atZoneSameInstant(ZoneId.of("Africa/Johannesburg")).toOffsetDateTime());
    }

    @Test
    @DisplayName("toPage - should map list of DTOs to page with content")
    void toPage_WithContent() {
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
    @DisplayName("toPage - should handle null list")
    void toPage_NullList() {
        var result = auditApiMapper.toPage(null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("toPage - should handle empty list")
    void toPage_EmptyList() {
        List<AuditLogDto> emptyList = Collections.emptyList();

        var result = auditApiMapper.toPage(emptyList);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("toPage - should handle single item list")
    void toPage_SingleItem() {

        var dto = createAuditLogDto("SINGLE_ACTION", "ACC123");
        List<AuditLogDto> dtos = Collections.singletonList(dto);

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("SINGLE_ACTION");
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo("ACC123");
    }

    @Test
    @DisplayName("toPage - should handle large list")
    void toPage_LargeList() {
        var dtos = new ArrayList<AuditLogDto>();
        for (int i = 0; i < 100; i++) {
            dtos.add(createAuditLogDto("ACTION" + i, "ACC" + i));
        }

        var result = auditApiMapper.toPage(dtos);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(100);
    }

    @Test
    @DisplayName("toPage - should preserve all DTO properties in mapped entries")
    void toPage_PreservesAllProperties() {
        var id = UUID.randomUUID();
        var statementId = UUID.randomUUID();
        var timestamp = OffsetDateTime.now();
        Map<String, Object> details = Collections.singletonMap("reason", "test");

        var dto = new AuditLogDto();
        dto.setId(id);
        dto.setAccountNumber("ACC999");
        dto.setStatementId(statementId);
        dto.setAction("UPLOAD_SUCCESS");
        dto.setPerformedAt(timestamp);
        dto.setIpAddress("10.0.0.1");
        dto.setUserAgent("TestAgent");
        dto.setDetails(details);

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
    @DisplayName("toPage - should handle list with null elements")
    void toPage_WithNullElements() {
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
    @DisplayName("toApi - should handle complex details map")
    void toApi_ComplexDetails() {

        var complexDetails = new HashMap<String, Object>();
        complexDetails.put("string", "value");
        complexDetails.put("number", 42);
        complexDetails.put("boolean", true);
        complexDetails.put("nested", Collections.singletonMap("key", "value"));
        complexDetails.put("list", Arrays.asList(1, 2, 3));

        var dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("COMPLEX_ACTION");
        dto.setDetails(complexDetails);

        var result = auditApiMapper.toApi(dto);

        assertThat(result.getDetails()).isEqualTo(complexDetails);
        assertThat(result.getDetails().get("string")).isEqualTo("value");
        assertThat(result.getDetails().get("number")).isEqualTo(42);
        assertThat(result.getDetails().get("boolean")).isEqualTo(true);
    }

    private AuditLogDto createAuditLogDto(String action, String accountNumber) {
        var dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction(action);
        dto.setAccountNumber(accountNumber);
        dto.setPerformedAt(OffsetDateTime.now());
        dto.setIpAddress("192.168.1.1");
        dto.setUserAgent("TestAgent");
        return dto;
    }
}
