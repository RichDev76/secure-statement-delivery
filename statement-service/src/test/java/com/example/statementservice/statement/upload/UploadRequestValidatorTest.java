package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.statement.UploadedFile;
import com.example.statementservice.statement.upload.infrastructure.MultipartFileAdapter;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("UploadRequestValidator Unit Tests")
class UploadRequestValidatorTest {

    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4

    private UploadRequestValidator uploadRequestValidator;

    private UploadedFile validPdfFile;
    private String validAccountNumber;
    private String validDate;
    private String validMessageDigest;

    @BeforeEach
    void setUp() {
        validPdfFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES));
        validAccountNumber = "123456789";
        validDate = "2024-01-15";
        validMessageDigest = new Sha256ContentDigest().hexOf(PDF_BYTES);
        uploadRequestValidator = new UploadRequestValidator();
    }

    @Test
    void GivenAllValidInputs_WhenValidatingFileUploadInputs_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() ->
                        uploadRequestValidator.validateFileUploadInputs(validPdfFile, validAccountNumber, validDate))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullFile_WhenValidatingFileUploadInputs_ThenThrowsMissingFileException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileUploadInputs(null, validAccountNumber, validDate))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void GivenEmptyFileWithValidName_WhenValidatingFileUploadInputs_ThenThrowsMissingFileException() {
        // Given
        var emptyFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]));

        // When / Then
        assertThatThrownBy(
                        () -> uploadRequestValidator.validateFileUploadInputs(emptyFile, validAccountNumber, validDate))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void
            GivenValidPdfBytesWithWrongContentType_WhenValidatingFileUploadInputs_ThenThrowsUnsupportedContentTypeException() {
        // Given
        var pdfBytesWrongType =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "text/plain", PDF_BYTES));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileUploadInputs(
                        pdfBytesWrongType, validAccountNumber, validDate))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    void GivenTraversalFileName_WhenValidatingFileUploadInputs_ThenThrowsPdfValidationException() {
        // Given
        var traversalFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "../evil.pdf", "application/pdf", PDF_BYTES));

        // When / Then
        assertThatThrownBy(() ->
                        uploadRequestValidator.validateFileUploadInputs(traversalFile, validAccountNumber, validDate))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("path traversal is not allowed");
    }

    @Test
    void
            GivenInvalidAccountNumberAndNonPdfBytes_WhenValidatingFileUploadInputs_ThenThrowsInvalidAccountNumberException() {
        // Given
        // Pins the ordering contract: string checks run before the magic-byte file read.
        var nonPdfFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", "application/pdf", "not a pdf".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileUploadInputs(nonPdfFile, "12AB", validDate))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenNonPdfBytesWithPdfContentType_WhenValidatingFileUploadInputs_ThenThrowsPdfValidationException() {
        // Given
        // Pins that the magic-byte check is wired into the aggregate for spoofed content types.
        var spoofedFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", "application/pdf", "not a pdf".getBytes()));

        // When / Then
        assertThatThrownBy(() ->
                        uploadRequestValidator.validateFileUploadInputs(spoofedFile, validAccountNumber, validDate))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenMatchingDigest_WhenValidatingMessageDigest_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, validMessageDigest))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, null))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenEmptyHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, ""))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenTooShortHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, "abc123"))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenTooLongHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, "a".repeat(65)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenNonHexHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, "g".repeat(64)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenMismatchedDigest_WhenValidatingMessageDigest_ThenThrowsDigestMismatchException() {
        // Given
        var wrongDigest = "b".repeat(64);

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateMessageDigest(validMessageDigest, wrongDigest))
                .isInstanceOf(DigestMismatchException.class)
                .hasMessageContaining("X-Message-Digest does not match file contents");
    }

    @Test
    void GivenUppercaseHeaderDigest_WhenValidatingMessageDigest_ThenComparisonIsCaseInsensitive() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateMessageDigest(
                        validMessageDigest, validMessageDigest.toUpperCase()))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonEmptyFile_WhenValidatingFileNotEmpty_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateFileNotEmpty(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullFile_WhenValidatingFileNotEmpty_ThenThrowsMissingFileException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileNotEmpty(null))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void GivenEmptyFile_WhenValidatingFileNotEmpty_ThenThrowsMissingFileException() {
        // Given
        var emptyFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileNotEmpty(emptyFile))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void GivenPdfContentType_WhenValidatingContentType_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateCorrectContentType(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonPdfContentType_WhenValidatingContentType_ThenThrowsUnsupportedContentTypeException() {
        // Given
        var wrongTypeFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateCorrectContentType(wrongTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    void GivenNullContentType_WhenValidatingContentType_ThenThrowsUnsupportedContentTypeException() {
        // Given
        var nullTypeFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", null, "content".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateCorrectContentType(nullTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    void GivenSafeFileName_WhenValidatingFileName_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateFileName(validPdfFile))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd.pdf", "..\\secrets.pdf", "report..2024.pdf"})
    void GivenFileNameContainingDoubleDots_WhenValidatingFileName_ThenThrowsPdfValidationException(String fileName) {
        // Given
        var traversalFile = new MultipartFileAdapter(
                new MockMultipartFile("file", fileName, "application/pdf", "content".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileName(traversalFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("path traversal is not allowed");
    }

    @Test
    void GivenEmptyFileName_WhenValidatingFileName_ThenThrowsPdfValidationException() {
        // Given
        var namelessFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "", "application/pdf", "content".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileName(namelessFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("Filename must not be empty");
    }

    @Test
    void GivenNullFileName_WhenValidatingFileName_ThenThrowsPdfValidationException() {
        // Given
        var nullNameFile = mock(UploadedFile.class);
        when(nullNameFile.getOriginalFilename()).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateFileName(nullNameFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("Filename must not be empty");
    }

    @Test
    void GivenNineDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateAccountNumber("123456789"))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenFifteenDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateAccountNumber("123456789012345"))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenTwelveDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateAccountNumber("123456789012"))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber(null))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenEmptyAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber(""))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenTooShortAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber("12345678"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenTooLongAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber("1234567890123456"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenNonNumericAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber("12345678A"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenWhitespaceAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateAccountNumber("   "))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenNineDigitAccountNumber_WhenCheckingIsValidAccountNumber_ThenReturnsTrue() {
        // When / Then
        assertThat(uploadRequestValidator.isValidAccountNumber("123456789")).isTrue();
    }

    @Test
    void GivenTooShortAccountNumber_WhenCheckingIsValidAccountNumber_ThenReturnsFalse() {
        // When / Then
        assertThat(uploadRequestValidator.isValidAccountNumber("12345678")).isFalse();
    }

    @Test
    void GivenNullAccountNumber_WhenCheckingIsValidAccountNumber_ThenReturnsFalse() {
        // When / Then
        assertThat(uploadRequestValidator.isValidAccountNumber(null)).isFalse();
    }

    @Test
    void GivenIsoDate_WhenValidatingDate_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateDate("2024-01-15")).doesNotThrowAnyException();
    }

    @Test
    void GivenLeapYearFebruary29_WhenValidatingDate_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validateDate("2024-02-29")).doesNotThrowAnyException();
    }

    @Test
    void GivenNullDate_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate(null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenEmptyDate_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate(""))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenWrongFormatDate_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate("01/15/2024"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenImpossibleDayOfMonth_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate("2024-02-30"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenInvalidMonth_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate("2024-13-01"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenNonLeapYearFebruary29_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate("2023-02-29"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenWhitespaceDate_WhenValidatingDate_ThenThrowsInvalidDateException() {
        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validateDate("   "))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenPdfMagicBytes_WhenValidatingPdfMagicNumber_ThenNoExceptionIsThrown() {
        // When / Then
        assertThatCode(() -> uploadRequestValidator.validatePdfMagicNumber(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonPdfBytes_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {
        // Given
        var nonPdfFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.txt", "text/plain", "This is not a PDF".getBytes()));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(nonPdfFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenFileSmallerThanMagicNumber_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {
        // Given
        var smallFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {0x25, 0x50}));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(smallFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    void GivenEmptyFile_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {
        // Given
        var emptyFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(emptyFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    void GivenUnreadableFile_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() throws IOException {
        // Given
        var rawMockFile = mock(MultipartFile.class);
        when(rawMockFile.getInputStream()).thenThrow(new IOException("IO error"));
        var mockFile = new MultipartFileAdapter(rawMockFile);

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(mockFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("Failed to read file for magic number validation");
    }

    @Test
    void GivenWrongFirstMagicByte_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {
        // Given
        var wrongMagic = new byte[] {0x00, 0x50, 0x44, 0x46};
        var wrongFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenWrongLastMagicByte_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {
        // Given
        var wrongMagic = new byte[] {0x25, 0x50, 0x44, 0x00};
        var wrongFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic));

        // When / Then
        assertThatThrownBy(() -> uploadRequestValidator.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenStreamReturningOneBytePerRead_WhenValidatingPdfMagicNumber_ThenNoExceptionIsThrown() throws IOException {
        // Given
        var tricklingFile = mock(UploadedFile.class);
        when(tricklingFile.getInputStream())
                .thenAnswer(invocation -> new FilterInputStream(new ByteArrayInputStream(PDF_BYTES)) {
                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        return super.read(buffer, offset, Math.min(length, 1));
                    }
                });

        // When / Then
        assertThatCode(() -> uploadRequestValidator.validatePdfMagicNumber(tricklingFile))
                .doesNotThrowAnyException();
    }
}
