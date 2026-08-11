package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.dto.AuditLogDto;
import com.example.statementservice.model.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("AuditLogEntityMapper Tests")
class AuditLogEntityMapperTest {

    private final AuditLogEntityMapper auditLogEntityMapper = Mappers.getMapper(AuditLogEntityMapper.class);

    @Test
    @DisplayName("toDto - should map all fields from entity to DTO")
    void toDto_AllFields() {
        var id = UUID.randomUUID();
        var statementId = UUID.randomUUID();
        var signedLinkId = UUID.randomUUID();
        var performedAt = OffsetDateTime.now();
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Link expired");
        details.put("extraKey", "extraValue");
        var entity = new AuditLog();
        entity.setId(id);
        entity.setAccountNumber("123456789");
        entity.setStatementId(statementId);
        entity.setSignedLinkId(signedLinkId);
        entity.setAction("DOWNLOAD_FAILED");
        entity.setPerformedAt(performedAt);
        entity.setPerformedBy("testUser");
        entity.setDetails(details);
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("123456789");
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAction()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(result.getPerformedAt()).isEqualTo(performedAt);
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.getDetails()).containsOnlyKeys("reason");
        assertThat(result.getDetails().get("reason")).isEqualTo("Link expired");
    }

    @Test
    @DisplayName("toDto - should handle null entity")
    void toDto_NullEntity() {
        var result = auditLogEntityMapper.toDto(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDto - should extract IP address from details")
    void toDto_ExtractIpAddress() {
        var details = new HashMap<String, Object>();
        details.put("ip", "10.0.0.1");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("toDto - should extract userAgent from details")
    void toDto_ExtractUserAgent() {
        var details = new HashMap<String, Object>();
        details.put("userAgent", "Chrome/90.0");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        assertThat(result.getUserAgent()).isEqualTo("Chrome/90.0");
    }

    @Test
    @DisplayName("toDto - should handle null details")
    void toDto_NullDetails() {
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(null);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should handle empty details map")
    void toDto_EmptyDetails() {
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(new HashMap<>());
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should only include reason in details output")
    void toDto_OnlyReasonInDetails() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Invalid signature");
        details.put("token", "abc123");
        details.put("extraField", "extraValue");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getDetails()).containsOnlyKeys("reason");
        assertThat(result.getDetails().get("reason")).isEqualTo("Invalid signature");
    }

    @Test
    @DisplayName("toDto - should return empty details when reason is missing")
    void toDto_NoReason() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("token", "abc123");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should handle non-string detail values")
    void toDto_NonStringDetailValues() {
        var details = new HashMap<String, Object>();
        details.put("ip", 12345); // Integer instead of String
        details.put("userAgent", true); // Boolean instead of String
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.getIpAddress()).isEqualTo("12345");
        assertThat(result.getUserAgent()).isEqualTo("true");
    }

    @Test
    @DisplayName("toDtos - should map list of entities to list of DTOs")
    void toDtos_MultipleEntities() {
        AuditLog entity1 = createAuditLog("ACTION1", "ACC1");
        AuditLog entity2 = createAuditLog("ACTION2", "ACC2");
        AuditLog entity3 = createAuditLog("ACTION3", "ACC3");
        List<AuditLog> entities = Arrays.asList(entity1, entity2, entity3);

        List<AuditLogDto> result = auditLogEntityMapper.toDtos(entities);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAction()).isEqualTo("ACTION1");
        assertThat(result.get(1).getAction()).isEqualTo("ACTION2");
        assertThat(result.get(2).getAction()).isEqualTo("ACTION3");
    }

    @Test
    @DisplayName("toDtos - should handle null list")
    void toDtos_NullList() {
        var result = auditLogEntityMapper.toDtos(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDtos - should handle empty list")
    void toDtos_EmptyList() {
        List<AuditLog> emptyList = Collections.emptyList();
        var result = auditLogEntityMapper.toDtos(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toDtos - should handle single item list")
    void toDtos_SingleItem() {
        var entity = createAuditLog("SINGLE_ACTION", "ACC123");
        List<AuditLog> entities = Collections.singletonList(entity);
        var result = auditLogEntityMapper.toDtos(entities);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SINGLE_ACTION");
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC123");
    }

    @Test
    @DisplayName("toDtos - should preserve all properties in mapped DTOs")
    void toDtos_PreservesAllProperties() {
        var id = UUID.randomUUID();
        var timestamp = OffsetDateTime.now();
        var details = new HashMap<String, Object>();
        details.put("ip", "203.0.113.1");
        details.put("userAgent", "Safari");
        details.put("reason", "Test reason");
        var entity = new AuditLog();
        entity.setId(id);
        entity.setAccountNumber("ACC999");
        entity.setAction("UPLOAD_SUCCESS");
        entity.setPerformedAt(timestamp);
        entity.setDetails(details);
        var entities = Collections.singletonList(entity);
        var result = auditLogEntityMapper.toDtos(entities);
        assertThat(result).hasSize(1);
        var dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getAccountNumber()).isEqualTo("ACC999");
        assertThat(dto.getAction()).isEqualTo("UPLOAD_SUCCESS");
        assertThat(dto.getPerformedAt()).isEqualTo(timestamp);
        assertThat(dto.getIpAddress()).isEqualTo("203.0.113.1");
        assertThat(dto.getUserAgent()).isEqualTo("Safari");
        assertThat(dto.getDetails()).containsOnlyKeys("reason");
    }

    @Test
    @DisplayName("extractDetail - should return null for null details")
    void extractDetail_NullDetails() {
        var log = new AuditLog();
        log.setDetails(null);
        var result = auditLogEntityMapper.extractDetail(log, "ip");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractDetail - should return null for missing key")
    void extractDetail_MissingKey() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractDetail(log, "nonExistentKey");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractDetail - should convert value to string")
    void extractDetail_ConvertToString() {
        var details = new HashMap<String, Object>();
        details.put("count", 42);
        details.put("flag", true);
        var log = new AuditLog();
        log.setDetails(details);
        var countResult = auditLogEntityMapper.extractDetail(log, "count");
        var flagResult = auditLogEntityMapper.extractDetail(log, "flag");
        assertThat(countResult).isEqualTo("42");
        assertThat(flagResult).isEqualTo("true");
    }

    @Test
    @DisplayName("extractReason - should return empty map for null log")
    void extractReason_NullLog() {
        var result = auditLogEntityMapper.extractReason(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return empty map for null details")
    void extractReason_NullDetails() {
        var log = new AuditLog();
        log.setDetails(null);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return empty map when reason is missing")
    void extractReason_MissingReason() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return map with only reason when present")
    void extractReason_WithReason() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Invalid token");
        details.put("extraKey", "extraValue");
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyKeys("reason");
        assertThat(result.get("reason")).isEqualTo("Invalid token");
    }

    @Test
    @DisplayName("extractReason - should handle non-string reason values")
    void extractReason_NonStringReason() {
        var details = new HashMap<String, Object>();
        details.put("reason", 404);
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).hasSize(1);
        assertThat(result.get("reason")).isEqualTo(404);
    }

    @Test
    @DisplayName("extractReason - should return immutable map")
    void extractReason_ImmutableMap() {
        Map<String, Object> details = Collections.singletonMap("reason", "Test");
        var log = new AuditLog();
        log.setDetails(details);
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);
        assertThat(result).containsKey("reason");
        assertThat(result).isInstanceOf(Map.class);
    }

    private AuditLog createAuditLog(String action, String accountNumber) {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "TestAgent");
        var log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction(action);
        log.setAccountNumber(accountNumber);
        log.setPerformedAt(OffsetDateTime.now());
        log.setDetails(details);
        return log;
    }
}
