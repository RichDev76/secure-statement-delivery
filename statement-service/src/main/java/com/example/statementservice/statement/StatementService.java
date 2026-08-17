package com.example.statementservice.statement;

import com.example.statementservice.shared.IdGeneratorPort;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.upload.UploadResponseDto;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
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
    private final StatementFileStore fileStore;
    private final FileCipher fileCipher;
    private final StatementEntityMapper statementEntityMapper;
    private final EncryptedFileFetcher encryptedFileFetcher;
    private final IdGeneratorPort idGenerator;
    private final Clock clock;

    @Transactional
    public UploadResponseDto uploadStatement(
            String accountNumber, LocalDate statementDate, MultipartFile file, String uploadedBy, String contentHash) {
        var id = idGenerator.newId();
        var initializationVector = fileCipher.generateInitializationVector();
        byte[] dek;
        byte[] wrappedDek;
        try {
            dek = fileCipher.generateDek();
            wrappedDek = fileCipher.wrapDek(dek);
        } catch (FileCipherException e) {
            throw new StatementUploadException("Failed to prepare encryption key", e);
        }

        String reference;
        try {
            reference = fileStore.store(
                    id,
                    accountNumber,
                    statementDate,
                    out -> fileCipher.encrypt(file.getInputStream(), out, initializationVector, dek));
        } catch (IOException e) {
            throw new StatementUploadException("Failed to encrypt and store file", e);
        }

        var statement = buildStatement(
                accountNumber,
                statementDate,
                file,
                uploadedBy,
                id,
                reference,
                initializationVector,
                wrappedDek,
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

    public Optional<Statement> findStatementById(UUID id) {
        return this.statementRepository.findStatementById(id);
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

    public boolean fileExists(Statement statement) {
        return fileStore.exists(statement.getStorageKey());
    }

    public InputStream openDecryptedFile(Statement statement) throws IOException {
        var dek = fileCipher.unwrapDek(statement.getEncryptedDek());
        var ciphertext = encryptedFileFetcher.fetch(statement.getStorageKey());
        return new ByteArrayInputStream(fileCipher.decrypt(ciphertext, dek));
    }

    private Statement buildStatement(
            String accountNumber,
            LocalDate statementDate,
            MultipartFile file,
            String uploadedBy,
            UUID id,
            String fileReference,
            byte[] iv,
            byte[] wrappedDek,
            String contentHash) {
        var stmt = new Statement();
        stmt.setId(id);
        stmt.setAccountNumber(accountNumber);
        stmt.setStatementDate(statementDate);
        stmt.setUploadFileName(sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename())));
        stmt.setStorageKey(fileReference);
        stmt.setFileIv(iv);
        stmt.setEncryptedDek(wrappedDek);
        stmt.setContentHash(contentHash);
        stmt.setEncrypted(true);
        stmt.setSizeBytes(file.getSize());
        stmt.setUploadedAt(OffsetDateTime.now(clock));
        stmt.setUploadedBy(uploadedBy == null ? "admin" : uploadedBy);
        return stmt;
    }

    private UploadResponseDto getUploadResponse(MultipartFile file, UUID id) {
        return UploadResponseDto.builder()
                .statementId(id)
                .uploadedAt(OffsetDateTime.now(clock))
                .fileSize(file.getSize())
                .fileName(file.getOriginalFilename())
                .build();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll(FILE_NAME_SANITIZATION_REGEX, "_");
    }
}
