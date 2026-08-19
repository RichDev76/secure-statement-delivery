package com.example.statementservice.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementEntityMapper Tests")
class StatementEntityMapperTest {

    private final StatementEntityMapper statementEntityMapper = Mappers.getMapper(StatementEntityMapper.class);

    @BeforeEach
    void setUp() {}

    @Test
    void GivenEntityWithAllFields_WhenMappingToDto_ThenAllFieldsAreMapped() {
        var id = UUID.randomUUID();
        var statementDate = LocalDate.of(2024, 1, 15);
        var uploadedAt = OffsetDateTime.now();
        var entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("123456789");
        entity.setStatementDate(statementDate);
        entity.setUploadFileName("statement.pdf");
        entity.setSizeBytes(2048L);
        entity.setUploadedAt(uploadedAt);
        var result = statementEntityMapper.toDto(entity);
        assertThat(result).isNotNull();
        assertThat(result.statementId()).isEqualTo(id);
        assertThat(result.accountNumber()).isEqualTo("123456789");
        assertThat(result.statementDate()).isEqualTo(statementDate);
        assertThat(result.fileName()).isEqualTo("statement.pdf");
        assertThat(result.fileSize()).isEqualTo(2048L);
        assertThat(result.uploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.downloadLink()).isNull();
    }

    @Test
    void GivenNullEntity_WhenMappingToDto_ThenReturnsNull() {
        var result = statementEntityMapper.toDto(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenEntityId_WhenMappingToDto_ThenIdIsMapped() {
        var id = UUID.randomUUID();
        var entity = new Statement();
        entity.setId(id);
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result.statementId()).isEqualTo(id);
    }

    @Test
    void GivenUploadFileName_WhenMappingToDto_ThenFileNameIsMapped() {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("original-name.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result.fileName()).isEqualTo("original-name.pdf");
    }

    @Test
    void GivenSizeBytes_WhenMappingToDto_ThenFileSizeIsMapped() {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(4096L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result.fileSize()).isEqualTo(4096L);
    }

    @Test
    void GivenMultipleEntities_WhenMappingToDtos_ThenAllAreMapped() {
        var entity1 = createStatement("ACC001", "file1.pdf", 1024L);
        var entity2 = createStatement("ACC002", "file2.pdf", 2048L);
        var entity3 = createStatement("ACC003", "file3.pdf", 4096L);
        var entities = Arrays.asList(entity1, entity2, entity3);
        var result = statementEntityMapper.toDtos(entities);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).accountNumber()).isEqualTo("ACC001");
        assertThat(result.get(0).fileName()).isEqualTo("file1.pdf");
        assertThat(result.get(0).downloadLink()).isNull();
        assertThat(result.get(1).accountNumber()).isEqualTo("ACC002");
        assertThat(result.get(1).fileName()).isEqualTo("file2.pdf");
        assertThat(result.get(1).downloadLink()).isNull();
        assertThat(result.get(2).accountNumber()).isEqualTo("ACC003");
        assertThat(result.get(2).fileName()).isEqualTo("file3.pdf");
        assertThat(result.get(2).downloadLink()).isNull();
    }

    @Test
    void GivenNullList_WhenMappingToDtos_ThenReturnsNull() {
        var result = statementEntityMapper.toDtos(null);
        assertThat(result).isNull();
    }

    @Test
    void GivenEmptyList_WhenMappingToDtos_ThenReturnsEmptyList() {
        List<Statement> emptyList = Collections.emptyList();
        var result = statementEntityMapper.toDtos(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    void GivenSingleEntity_WhenMappingToDtos_ThenOneDtoIsReturned() {
        var entity = createStatement("ACC123", "single.pdf", 1024L);
        var entities = Collections.singletonList(entity);
        var result = statementEntityMapper.toDtos(entities);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountNumber()).isEqualTo("ACC123");
        assertThat(result.get(0).fileName()).isEqualTo("single.pdf");
        assertThat(result.get(0).downloadLink()).isNull();
    }

    @Test
    void GivenEntities_WhenMappingToDtos_ThenAllPropertiesArePreserved() {
        var id = UUID.randomUUID();
        var date = LocalDate.of(2024, 5, 15);
        var uploadedAt = OffsetDateTime.now();
        var entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("ACC777");
        entity.setStatementDate(date);
        entity.setUploadFileName("full.pdf");
        entity.setSizeBytes(16384L);
        entity.setUploadedAt(uploadedAt);
        var entities = Collections.singletonList(entity);
        var result = statementEntityMapper.toDtos(entities);
        assertThat(result).hasSize(1);
        var dto = result.get(0);
        assertThat(dto.statementId()).isEqualTo(id);
        assertThat(dto.accountNumber()).isEqualTo("ACC777");
        assertThat(dto.statementDate()).isEqualTo(date);
        assertThat(dto.fileName()).isEqualTo("full.pdf");
        assertThat(dto.fileSize()).isEqualTo(16384L);
        assertThat(dto.uploadedAt()).isEqualTo(uploadedAt);
        assertThat(dto.downloadLink()).isNull();
    }

    @Test
    void GivenLargeList_WhenMappingToDtos_ThenAllAreMapped() {
        var entities = new ArrayList<Statement>();
        for (int i = 0; i < 100; i++) {
            entities.add(createStatement("ACC" + i, "file" + i + ".pdf", 1024L * i));
        }
        var result = statementEntityMapper.toDtos(entities);
        assertThat(result).hasSize(100);
        assertThat(result.get(0).accountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).accountNumber()).isEqualTo("ACC99");
    }

    @Test
    void GivenEntityWithNullOptionalFields_WhenMappingToDto_ThenNullsArePreserved() {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result).isNotNull();
        assertThat(result.statementId()).isNotNull();
        assertThat(result.fileName()).isEqualTo("test.pdf");
        assertThat(result.accountNumber()).isNull();
        assertThat(result.statementDate()).isNull();
    }

    @Test
    void GivenZeroSizeBytes_WhenMappingToDto_ThenZeroIsMapped() {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("empty.pdf");
        entity.setSizeBytes(0L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result.fileSize()).isEqualTo(0L);
    }

    @Test
    void GivenSpecialCharacterFileName_WhenMappingToDto_ThenFileNameIsPreserved() {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("statement (2024) [final].pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());
        var result = statementEntityMapper.toDto(entity);
        assertThat(result.fileName()).isEqualTo("statement (2024) [final].pdf");
    }

    private Statement createStatement(String accountNumber, String fileName, Long fileSize) {
        var entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setAccountNumber(accountNumber);
        entity.setStatementDate(LocalDate.of(2024, 1, 15));
        entity.setUploadFileName(fileName);
        entity.setSizeBytes(fileSize);
        entity.setUploadedAt(OffsetDateTime.now());
        entity.setUploadedBy("testUser");
        entity.setEncrypted(true);
        entity.setContentHash("hash123");
        entity.setStorageKey("statements/hash/2026/07/file.pdf.enc");
        return entity;
    }
}
