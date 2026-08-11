package com.example.statementservice.service;

import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.exception.StatementUploadException;
import com.example.statementservice.mapper.StatementEntityMapper;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.dto.UploadResponseDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import java.io.File;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    public static final String FILE_NAME_SANITIZATION_REGEX = "[^a-zA-Z0-9._-]";
    private final StatementRepository statementRepository;
    private final FileStorageService fileStorageService;
    private final EncryptionService encryptionService;
    private final StatementEntityMapper statementEntityMapper;

    @Transactional
    public UploadResponseDto uploadStatement(
            String accountNumber, java.time.LocalDate statementDate, MultipartFile file, String uploadedBy) {
        var id = UUID.randomUUID();

        var stored = this.fileStorageService.storeEncrypted(id, file, accountNumber, statementDate);

        var contentHash = this.encryptionService.computeSha256Hex(file);
        log.info("Message Digest {}", contentHash);

        var statement = buildStatement(
                accountNumber,
                statementDate,
                file,
                uploadedBy,
                id,
                stored.file(),
                stored.initializationVector(),
                contentHash);
        try {
            this.statementRepository.saveAndFlush(statement);
        } catch (RuntimeException e) {
            throw new StatementUploadException("Failed to persist statement metadata", e);
        }
        return getUploadResponse(file, id);
    }

    public Statement getStatementById(UUID id) {
        return this.statementRepository
                .findStatementById(id)
                .orElseThrow(() -> new StatementNotFoundException("Statement not found for id: " + id));
    }

    public Page<Statement> getStatementsByAccountNumber(String accountNumber, Pageable pageable) {
        return statementRepository.findByAccountNumber(accountNumber, pageable);
    }

    public List<Statement> getStatementsByAccountNumber(String accountNumber) {
        return this.statementRepository
                .findAllByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new StatementNotFoundException("Statement(s) not found for account number: " + accountNumber));
    }

    public Optional<Statement> getStatementByAccountNumberAndStatementDate(
            String accountNumber, LocalDate statementDate) {
        return this.statementRepository.findByAccountNumberAndStatementDate(accountNumber, statementDate);
    }

    public StatementDto toDto(Statement s) {
        return statementEntityMapper.toDto(s);
    }

    public StatementDto getStatementDtoById(UUID id) {
        return toDto(getStatementById(id));
    }

    public List<StatementDto> getStatementsDtoByAccountNumber(String accountNumber) {
        return statementEntityMapper.toDtos(getStatementsByAccountNumber(accountNumber));
    }

    public Optional<StatementDto> getStatementDtoByAccountNumberAndStatementDate(
            String accountNumber, LocalDate statementDate) {
        return getStatementByAccountNumberAndStatementDate(accountNumber, statementDate)
                .map(this::toDto);
    }

    public Page<Statement> getStatementsByAccountNumberAndDateRange(
            String accountNumber, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return statementRepository.findByAccountNumberAndDateRange(accountNumber, startDate, endDate, pageable);
    }

    private Statement buildStatement(
            String accountNumber,
            LocalDate statementDate,
            MultipartFile file,
            String uploadedBy,
            UUID id,
            File out,
            byte[] iv,
            String contentHash) {
        var stmt = new Statement();
        stmt.setId(id);
        stmt.setAccountNumber(accountNumber);
        stmt.setStatementDate(statementDate);
        stmt.setUploadFileName(sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename())));
        stmt.setFilePath(out.getAbsolutePath());
        stmt.setFileIv(iv);
        stmt.setContentHash(contentHash);
        stmt.setEncrypted(true);
        stmt.setSizeBytes(file.getSize());
        stmt.setUploadedAt(OffsetDateTime.now());
        stmt.setUploadedBy(uploadedBy == null ? "admin" : uploadedBy);
        return stmt;
    }

    private UploadResponseDto getUploadResponse(MultipartFile file, UUID id) {
        return UploadResponseDto.builder()
                .statementId(id)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(file.getSize())
                .fileName(file.getOriginalFilename())
                .build();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll(FILE_NAME_SANITIZATION_REGEX, "_");
    }
}
