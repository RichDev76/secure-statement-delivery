package com.example.statementservice.service;

import java.io.File;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final String FILE_EXTENSION_PDF_ENC = ".pdf.enc";
    public static final String STATEMENTS_FOLDER = "statements";
    private final EncryptionService encryptionService;

    @Value("${statement.storage.base-dir:/data/files}")
    private String baseDir;

    public FileStorageResult storeEncrypted(
            java.util.UUID id,
            org.springframework.web.multipart.MultipartFile file,
            String accountNumber,
            java.time.LocalDate statementDate) {
        var accountNumberHash = this.encryptionService.computeAccountNumberHash(accountNumber);
        var storageDirectory = getStorageDirectory(accountNumberHash, statementDate);
        var encryptedFileOutput = new java.io.File(storageDirectory, id + FILE_EXTENSION_PDF_ENC);
        var initializationVector = this.encryptionService.generateInitializationVector();
        try {
            this.encryptionService.encryptToFile(file.getInputStream(), encryptedFileOutput, initializationVector);
        } catch (java.io.IOException e) {
            throw new com.example.statementservice.exception.StatementUploadException(
                    "Failed to encrypt and store file", e);
        }
        return new FileStorageResult(encryptedFileOutput, initializationVector);
    }

    private java.io.File getStorageDirectory(String accountNumberHash, java.time.LocalDate statementDate) {
        if (accountNumberHash == null || accountNumberHash.isBlank()) {
            throw new com.example.statementservice.exception.StatementUploadException(
                    "accountNumberHash must be provided");
        }

        var year = Integer.toString(statementDate.getYear());
        var month = String.format(java.util.Locale.ROOT, "%02d", statementDate.getMonthValue());
        var fullFileStorageDirectory = buildFullFilePath(accountNumberHash, year, month);

        if (!fullFileStorageDirectory.exists() && !fullFileStorageDirectory.mkdirs()) {
            throw new com.example.statementservice.exception.StatementUploadException(
                    "Failed to create storage directory: " + fullFileStorageDirectory.getAbsolutePath());
        }
        return fullFileStorageDirectory;
    }

    private File buildFullFilePath(String accountNumberHash, String year, String month) {
        return new File(
                new File(new File(new File(this.baseDir), STATEMENTS_FOLDER), accountNumberHash),
                new File(year, month).getPath());
    }

    public record FileStorageResult(File file, byte[] initializationVector) {}
}
