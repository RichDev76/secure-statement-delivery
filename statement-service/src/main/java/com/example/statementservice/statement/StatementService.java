package com.example.statementservice.statement;

import com.example.statementservice.shared.IdGenerator;
import com.example.statementservice.statement.upload.UploadResponseDto;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    public static final String FILE_NAME_SANITIZATION_REGEX = "[^a-zA-Z0-9_-]";
    public static final String ADMIN_USER = "admin";

    private static final String PDF_EXTENSION = ".pdf";
    private static final String FALLBACK_FILE_NAME_STEM = "statement";
    private static final String ACCOUNT_DATE_UNIQUE_INDEX = "idx_statements_account_date";

    private final StatementRepository statementRepository;
    private final StatementFileStore fileStore;
    private final FileCipher fileCipher;
    private final StatementEntityMapper statementEntityMapper;
    private final EncryptedFileFetcher encryptedFileFetcher;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public UploadResponseDto uploadStatement(
            String accountNumber, LocalDate statementDate, UploadedFile file, String uploadedBy, String contentHash) {
        rejectDuplicateUpload(accountNumber, statementDate);
        var id = idGenerator.newId();
        var encryptionMaterial = prepareEncryptionMaterial();
        var reference = encryptAndStore(id, accountNumber, statementDate, file, encryptionMaterial);
        persistCompensatingOnFailure(
                accountNumber, statementDate, file, uploadedBy, id, reference, encryptionMaterial, contentHash);
        return getUploadResponse(file, id);
    }

    // Pre-check avoids orphaning an S3 object on the common duplicate path.
    private void rejectDuplicateUpload(String accountNumber, LocalDate statementDate) {
        if (statementRepository.existsByAccountNumberAndStatementDate(accountNumber, statementDate)) {
            throw new DuplicateStatementException();
        }
    }

    private EncryptionMaterial prepareEncryptionMaterial() {
        try {
            var initializationVector = fileCipher.generateInitializationVector();
            var dek = fileCipher.generateDek();
            return new EncryptionMaterial(initializationVector, dek, fileCipher.wrapDek(dek));
        } catch (FileCipherException e) {
            throw new StatementUploadException("Failed to prepare encryption key", e);
        }
    }

    private String encryptAndStore(
            UUID id, String accountNumber, LocalDate statementDate, UploadedFile file, EncryptionMaterial material) {
        var contentLength = fileCipher.ciphertextLength(file.getSize());
        try {
            return fileStore.store(
                    id,
                    accountNumber,
                    statementDate,
                    contentLength,
                    () -> fileCipher.encryptingStream(
                            file.getInputStream(), material.initializationVector(), material.dek()));
        } catch (IOException e) {
            throw new StatementUploadException("Failed to encrypt and store file", e);
        }
    }

    // Any failure after a successful store must delete the stored object, or it is orphaned.
    private void persistCompensatingOnFailure(
            String accountNumber,
            LocalDate statementDate,
            UploadedFile file,
            String uploadedBy,
            UUID id,
            String reference,
            EncryptionMaterial material,
            String contentHash) {
        try {
            var statement = buildStatement(
                    accountNumber, statementDate, file, uploadedBy, id, reference, material, contentHash);
            this.statementRepository.saveAndFlush(statement);
        } catch (DataIntegrityViolationException e) {
            deleteStoredFileBestEffort(reference);
            if (isAccountDateUniqueViolation(e)) {
                throw new DuplicateStatementException(e);
            }
            throw new StatementUploadException("Failed to persist statement metadata", e);
        } catch (RuntimeException e) {
            deleteStoredFileBestEffort(reference);
            throw new StatementUploadException("Failed to persist statement metadata", e);
        }
    }

    public Statement getStatementById(UUID id) {
        return this.statementRepository
                .findStatementById(id)
                .orElseThrow(() -> new StatementNotFoundException("Statement not found for id: " + id));
    }

    public Optional<Statement> findStatementById(UUID id) {
        return this.statementRepository.findStatementById(id);
    }

    public StatementDto toDto(Statement s) {
        return statementEntityMapper.toDto(s);
    }

    public StatementDto getStatementDtoById(UUID id) {
        return toDto(getStatementById(id));
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
            UploadedFile file,
            String uploadedBy,
            UUID id,
            String fileReference,
            EncryptionMaterial material,
            String contentHash) {
        var stmt = new Statement();
        stmt.setId(id);
        stmt.setAccountNumber(accountNumber);
        stmt.setStatementDate(statementDate);
        stmt.setUploadFileName(sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename())));
        stmt.setStorageKey(fileReference);
        stmt.setFileIv(material.initializationVector());
        stmt.setEncryptedDek(material.wrappedDek());
        stmt.setContentHash(contentHash);
        stmt.setEncrypted(true);
        stmt.setSizeBytes(file.getSize());
        stmt.setUploadedAt(OffsetDateTime.now(clock));
        stmt.setUploadedBy(uploadedBy == null ? ADMIN_USER : uploadedBy);
        return stmt;
    }

    private record EncryptionMaterial(byte[] initializationVector, byte[] dek, byte[] wrappedDek) {}

    private UploadResponseDto getUploadResponse(UploadedFile file, UUID id) {
        return UploadResponseDto.builder()
                .statementId(id)
                .uploadedAt(OffsetDateTime.now(clock))
                .fileSize(file.getSize())
                .fileName(file.getOriginalFilename())
                .build();
    }

    private static boolean isAccountDateUniqueViolation(DataIntegrityViolationException e) {
        return String.valueOf(e.getMostSpecificCause().getMessage()).contains(ACCOUNT_DATE_UNIQUE_INDEX);
    }

    // Best-effort: the persistence failure must propagate, not the cleanup failure.
    private void deleteStoredFileBestEffort(String reference) {
        try {
            fileStore.delete(reference);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to delete orphaned statement object - storageKey: {}", reference, e);
        }
    }

    // Output must match the download contract's fileName pattern.
    static String sanitizeFileName(String fileName) {
        var stem = fileName.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)
                ? fileName.substring(0, fileName.length() - PDF_EXTENSION.length())
                : fileName;
        var sanitizedStem = stem.replaceAll(FILE_NAME_SANITIZATION_REGEX, "_");
        return (sanitizedStem.isEmpty() ? FALLBACK_FILE_NAME_STEM : sanitizedStem) + PDF_EXTENSION;
    }
}
