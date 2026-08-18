package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;

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
    void GivenEntityWithAllFields_WhenMappingToDto_ThenAllFieldsAreMapped() {
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
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.accountNumber()).isEqualTo("123456789");
        assertThat(result.statementId()).isEqualTo(statementId);
        assertThat(result.action()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(result.performedAt()).isEqualTo(performedAt);
        assertThat(result.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(result.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.details()).containsOnlyKeys("reason");
        assertThat(result.details().get("reason")).isEqualTo("Link expired");
    }

    @Test
    void GivenNullEntity_WhenMappingToDto_ThenReturnsNull() {
        var result = auditLogEntityMapper.toDto(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenIpInDetails_WhenMappingToDto_ThenIpIsExtracted() {
        var details = new HashMap<String, Object>();
        details.put("ip", "10.0.0.1");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.ipAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void GivenUserAgentInDetails_WhenMappingToDto_ThenUserAgentIsExtracted() {
        var details = new HashMap<String, Object>();
        details.put("userAgent", "Chrome/90.0");
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        assertThat(result.userAgent()).isEqualTo("Chrome/90.0");
    }

    @Test
    void GivenNullDetails_WhenMappingToDto_ThenIpUserAgentAreNullAndDetailsEmpty() {
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(null);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.ipAddress()).isNull();
        assertThat(result.userAgent()).isNull();
        assertThat(result.details()).isEmpty();
    }

    @Test
    void GivenEmptyDetails_WhenMappingToDto_ThenIpUserAgentAreNullAndDetailsEmpty() {
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(new HashMap<>());
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.ipAddress()).isNull();
        assertThat(result.userAgent()).isNull();
        assertThat(result.details()).isEmpty();
    }

    @Test
    void GivenOnlyReasonInDetails_WhenMappingToDto_ThenReasonIsExtracted() {
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
        assertThat(result.details()).containsOnlyKeys("reason");
        assertThat(result.details().get("reason")).isEqualTo("Invalid signature");
    }

    @Test
    void GivenDetailsWithoutReason_WhenMappingToDto_ThenReasonIsNull() {
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
        assertThat(result.details()).isEmpty();
    }

    @Test
    void GivenNonStringDetailValues_WhenMappingToDto_ThenValuesAreStringified() {
        var details = new HashMap<String, Object>();
        details.put("ip", 12345); // Integer instead of String
        details.put("userAgent", true); // Boolean instead of String
        var entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());
        var result = auditLogEntityMapper.toDto(entity);
        assertThat(result.ipAddress()).isEqualTo("12345");
        assertThat(result.userAgent()).isEqualTo("true");
    }

    @Test
    void GivenMultipleEntities_WhenMappingToDtos_ThenAllAreMapped() {
        AuditLog entity1 = createAuditLog("ACTION1", "ACC1");
        AuditLog entity2 = createAuditLog("ACTION2", "ACC2");
        AuditLog entity3 = createAuditLog("ACTION3", "ACC3");
        List<AuditLog> entities = Arrays.asList(entity1, entity2, entity3);

        List<AuditLogDto> result = auditLogEntityMapper.toDtos(entities);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).action()).isEqualTo("ACTION1");
        assertThat(result.get(1).action()).isEqualTo("ACTION2");
        assertThat(result.get(2).action()).isEqualTo("ACTION3");
    }

    @Test
    void GivenNullList_WhenMappingToDtos_ThenReturnsNull() {
        var result = auditLogEntityMapper.toDtos(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenEmptyList_WhenMappingToDtos_ThenReturnsEmptyList() {
        List<AuditLog> emptyList = Collections.emptyList();
        var result = auditLogEntityMapper.toDtos(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenSingleEntity_WhenMappingToDtos_ThenOneDtoIsReturned() {
        var entity = createAuditLog("SINGLE_ACTION", "ACC123");
        List<AuditLog> entities = Collections.singletonList(entity);
        var result = auditLogEntityMapper.toDtos(entities);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo("SINGLE_ACTION");
        assertThat(result.get(0).accountNumber()).isEqualTo("ACC123");
    }

    @Test
    void GivenEntities_WhenMappingToDtos_ThenAllPropertiesArePreserved() {
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
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.accountNumber()).isEqualTo("ACC999");
        assertThat(dto.action()).isEqualTo("UPLOAD_SUCCESS");
        assertThat(dto.performedAt()).isEqualTo(timestamp);
        assertThat(dto.ipAddress()).isEqualTo("203.0.113.1");
        assertThat(dto.userAgent()).isEqualTo("Safari");
        assertThat(dto.details()).containsOnlyKeys("reason");
    }

    @Test
    void GivenNullDetails_WhenExtractingDetail_ThenReturnsNull() {
        var log = new AuditLog();
        log.setDetails(null);
        var result = auditLogEntityMapper.extractDetail(log, "ip");
        assertThat(result).isNull();
    }

    @Test
    void GivenMissingKey_WhenExtractingDetail_ThenReturnsNull() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractDetail(log, "nonExistentKey");
        assertThat(result).isNull();
    }

    @Test
    void GivenNonStringValue_WhenExtractingDetail_ThenValueIsStringified() {
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
    void GivenNullLog_WhenExtractingReason_ThenReturnsNull() {
        var result = auditLogEntityMapper.extractReason(null);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenNullDetails_WhenExtractingReason_ThenReturnsNull() {
        var log = new AuditLog();
        log.setDetails(null);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenDetailsWithoutReason_WhenExtractingReason_ThenReturnsNull() {
        var details = new HashMap<String, Object>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenDetailsWithReason_WhenExtractingReason_ThenReasonIsReturned() {
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
    void GivenNonStringReason_WhenExtractingReason_ThenRawValueIsKept() {
        var details = new HashMap<String, Object>();
        details.put("reason", 404);
        var log = new AuditLog();
        log.setDetails(details);
        var result = auditLogEntityMapper.extractReason(log);
        assertThat(result).hasSize(1);
        assertThat(result.get("reason")).isEqualTo(404);
    }

    @Test
    void GivenImmutableDetails_WhenExtractingReason_ThenReasonIsReadWithoutMutation() {
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
