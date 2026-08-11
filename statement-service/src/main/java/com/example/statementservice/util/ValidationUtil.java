package com.example.statementservice.util;

import com.example.statementservice.exception.DigestMismatchException;
import com.example.statementservice.exception.InvalidAccountNumberException;
import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.exception.InvalidMessageDigestException;
import com.example.statementservice.exception.MissingFileException;
import com.example.statementservice.exception.PdfValidationException;
import com.example.statementservice.exception.UnsupportedContentTypeException;
import com.example.statementservice.service.EncryptionService;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ValidationUtil {

    // Message Digest Constants
    private static final String MESSAGE_DIGEST_PATTERN = "^[A-Fa-f0-9]{64}$";
    private static final String INVALID_DIGEST_FORMAT_MSG = "X-Message-Digest must be a 64-character hex string";
    private static final String DIGEST_MISMATCH_MSG = "X-Message-Digest does not match file contents";

    // File Constants
    private static final String MISSING_FILE_MSG = "file is required";
    private static final String UNSUPPORTED_CONTENT_TYPE_MSG = "Unsupported Media Type";

    // Account Number Constants
    private static final String ACCOUNT_NUMBER_PATTERN = "^[0-9]{9,15}$";
    private static final String INVALID_ACCOUNT_NUMBER_MSG = "Invalid account number";

    // Date Constants
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final String INVALID_DATE_FORMAT_MSG = "date must be in YYYY-MM-DD format";

    // PDF Magic Number Constants
    private static final int PDF_MAGIC_NUMBER_SIZE = 4;
    private static final byte PDF_MAGIC_BYTE_1 = 0x25; // %
    private static final byte PDF_MAGIC_BYTE_2 = 0x50; // P
    private static final byte PDF_MAGIC_BYTE_3 = 0x44; // D
    private static final byte PDF_MAGIC_BYTE_4 = 0x46; // F
    private static final String PDF_TOO_SMALL_MSG = "File is too small to be a valid PDF";
    private static final String INVALID_PDF_MSG = "File is not a valid PDF";
    private static final String PDF_READ_ERROR_MSG = "Failed to read file for magic number validation";

    private final EncryptionService encryptionService;

    public void validateFileUploadInputs(MultipartFile file, String xMessageDigest, String accountNumber, String date) {
        validateFileName(file);
        validatePdfMagicNumber(file);
        validateCorrectContentType(file);
        validateMessageDigest(file, xMessageDigest);
        validateFileNotEmpty(file);
        validateAccountNumber(accountNumber);
        validateDate(date);
    }

    public void validateMessageDigest(MultipartFile file, String xMessageDigest) {
        if (!StringUtils.hasText(xMessageDigest) || !xMessageDigest.matches(MESSAGE_DIGEST_PATTERN)) {
            throw new InvalidMessageDigestException(INVALID_DIGEST_FORMAT_MSG);
        }

        var computedDigest = this.encryptionService.computeSha256Hex(file);
        if (!computedDigest.equalsIgnoreCase(xMessageDigest)) {
            throw new DigestMismatchException(DIGEST_MISMATCH_MSG);
        }
    }

    public void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MissingFileException(MISSING_FILE_MSG);
        }
    }

    public void validateCorrectContentType(MultipartFile file) {
        var contentType = file.getContentType();
        if (!MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
            throw new UnsupportedContentTypeException(UNSUPPORTED_CONTENT_TYPE_MSG);
        }
    }

    public void validateFileName(MultipartFile file) {
        var originalFileName = getOriginalFileName(file);
        if (originalFileName.contains("..")) {
            throw new PdfValidationException("Invalid filename: path traversal is not allowed");
        }
    }

    private String getOriginalFileName(MultipartFile file) {
        var originalFileName = file.getOriginalFilename();
        if (org.apache.commons.lang3.StringUtils.isEmpty(originalFileName)) {
            throw new PdfValidationException("Filename must not be empty");
        }
        return originalFileName;
    }

    public void validateAccountNumber(String accountNumber) {
        if (!StringUtils.hasText(accountNumber) || !accountNumber.matches(ACCOUNT_NUMBER_PATTERN)) {
            throw new InvalidAccountNumberException(INVALID_ACCOUNT_NUMBER_MSG);
        }
    }

    public void validateDate(String date) {
        try {
            if (!StringUtils.hasText(date) || !date.matches(DATE_PATTERN)) {
                throw new InvalidDateException(INVALID_DATE_FORMAT_MSG);
            }
            java.time.LocalDate.parse(date);
        } catch (java.time.format.DateTimeParseException e) {
            throw new InvalidDateException(INVALID_DATE_FORMAT_MSG);
        }
    }

    public void validatePdfMagicNumber(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            var magicBytes = new byte[PDF_MAGIC_NUMBER_SIZE];
            int bytesRead = inputStream.read(magicBytes);

            if (bytesRead < PDF_MAGIC_NUMBER_SIZE) {
                throw new PdfValidationException(PDF_TOO_SMALL_MSG);
            }

            if (magicBytes[0] != PDF_MAGIC_BYTE_1
                    || magicBytes[1] != PDF_MAGIC_BYTE_2
                    || magicBytes[2] != PDF_MAGIC_BYTE_3
                    || magicBytes[3] != PDF_MAGIC_BYTE_4) {
                throw new PdfValidationException(INVALID_PDF_MSG);
            }
        } catch (IOException e) {
            throw new PdfValidationException(PDF_READ_ERROR_MSG, e);
        }
    }
}
