package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.statement.UploadedFile;
import com.example.statementservice.statement.upload.infrastructure.MultipartFileAdapter;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("ValidationUtil Unit Tests")
class ValidationUtilTest {

    private ValidationUtil validationUtil;

    private UploadedFile validPdfFile;
    private String validAccountNumber;
    private String validDate;
    private String validMessageDigest;

    @BeforeEach
    void setUp() {
        var pdfContent = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        validPdfFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent));
        validAccountNumber = "123456789";
        validDate = "2024-01-15";
        validMessageDigest = Sha256Digest.hexOf(pdfContent);
        validationUtil = new ValidationUtil();
    }

    @Test
    void GivenAllValidInputs_WhenValidatingFileUploadInputs_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateFileUploadInputs(validPdfFile, validAccountNumber, validDate))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenMatchingDigest_WhenValidatingMessageDigest_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateMessageDigest(validMessageDigest, validMessageDigest))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {

        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, null))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenEmptyHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {

        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, ""))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenTooShortHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {

        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, "abc123"))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenTooLongHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {

        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, "a".repeat(65)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenNonHexHeaderDigest_WhenValidatingMessageDigest_ThenThrowsInvalidMessageDigestException() {

        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, "g".repeat(64)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");
    }

    @Test
    void GivenMismatchedDigest_WhenValidatingMessageDigest_ThenThrowsDigestMismatchException() {

        var wrongDigest = "b".repeat(64);
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validMessageDigest, wrongDigest))
                .isInstanceOf(DigestMismatchException.class)
                .hasMessageContaining("X-Message-Digest does not match file contents");
    }

    @Test
    void GivenUppercaseHeaderDigest_WhenValidatingMessageDigest_ThenComparisonIsCaseInsensitive() {

        assertThatCode(() -> validationUtil.validateMessageDigest(validMessageDigest, validMessageDigest.toUpperCase()))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonEmptyFile_WhenValidatingFileNotEmpty_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateFileNotEmpty(validPdfFile)).doesNotThrowAnyException();
    }

    @Test
    void GivenNullFile_WhenValidatingFileNotEmpty_ThenThrowsMissingFileException() {

        assertThatThrownBy(() -> validationUtil.validateFileNotEmpty(null))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void GivenEmptyFile_WhenValidatingFileNotEmpty_ThenThrowsMissingFileException() {

        var emptyFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]));
        assertThatThrownBy(() -> validationUtil.validateFileNotEmpty(emptyFile))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void GivenPdfContentType_WhenValidatingContentType_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateCorrectContentType(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonPdfContentType_WhenValidatingContentType_ThenThrowsUnsupportedContentTypeException() {
        var wrongTypeFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes()));
        assertThatThrownBy(() -> validationUtil.validateCorrectContentType(wrongTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    void GivenNullContentType_WhenValidatingContentType_ThenThrowsUnsupportedContentTypeException() {

        var nullTypeFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", null, "content".getBytes()));

        assertThatThrownBy(() -> validationUtil.validateCorrectContentType(nullTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    void GivenNineDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateAccountNumber("123456789")).doesNotThrowAnyException();
    }

    @Test
    void GivenFifteenDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateAccountNumber("123456789012345"))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenTwelveDigitAccountNumber_WhenValidatingAccountNumber_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateAccountNumber("123456789012"))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNullAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber(null))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenEmptyAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber(""))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenTooShortAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber("12345678"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenTooLongAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber("1234567890123456"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenNonNumericAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber("12345678A"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenWhitespaceAccountNumber_WhenValidatingAccountNumber_ThenThrowsInvalidAccountNumberException() {

        assertThatThrownBy(() -> validationUtil.validateAccountNumber("   "))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    void GivenIsoDate_WhenValidatingDate_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateDate("2024-01-15")).doesNotThrowAnyException();
    }

    @Test
    void GivenLeapYearFebruary29_WhenValidatingDate_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validateDate("2024-02-29")).doesNotThrowAnyException();
    }

    @Test
    void GivenNullDate_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate(null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenEmptyDate_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate(""))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenWrongFormatDate_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate("01/15/2024"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenImpossibleDayOfMonth_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate("2024-02-30"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenInvalidMonth_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate("2024-13-01"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenNonLeapYearFebruary29_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate("2023-02-29"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenWhitespaceDate_WhenValidatingDate_ThenThrowsInvalidDateException() {

        assertThatThrownBy(() -> validationUtil.validateDate("   "))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    void GivenPdfMagicBytes_WhenValidatingPdfMagicNumber_ThenNoExceptionIsThrown() {

        assertThatCode(() -> validationUtil.validatePdfMagicNumber(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenNonPdfBytes_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {

        var nonPdfFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.txt", "text/plain", "This is not a PDF".getBytes()));
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(nonPdfFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenFileSmallerThanMagicNumber_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {

        var smallFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {0x25, 0x50}));
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(smallFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    void GivenEmptyFile_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {

        var emptyFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]));
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(emptyFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    void GivenUnreadableFile_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() throws IOException {

        var rawMockFile = mock(MultipartFile.class);
        when(rawMockFile.getInputStream()).thenThrow(new IOException("IO error"));
        var mockFile = new MultipartFileAdapter(rawMockFile);
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(mockFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("Failed to read file for magic number validation");
    }

    @Test
    void GivenWrongFirstMagicByte_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {

        var wrongMagic = new byte[] {0x00, 0x50, 0x44, 0x46};
        var wrongFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic));
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    void GivenWrongLastMagicByte_WhenValidatingPdfMagicNumber_ThenThrowsPdfValidationException() {

        var wrongMagic = new byte[] {0x25, 0x50, 0x44, 0x00};
        var wrongFile =
                new MultipartFileAdapter(new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic));
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }
}
