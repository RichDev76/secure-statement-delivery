package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.exception.StatementUploadException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService Unit Tests")
class FileStorageServiceTest {

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    private UUID testId;
    private String testAccountNumber;
    private LocalDate testStatementDate;
    private MultipartFile testFile;
    private byte[] testIv;
    private String testAccountHash;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAccountNumber = "123456789";
        testStatementDate = LocalDate.of(2024, 1, 15);
        testFile = new MockMultipartFile("file", "statement.pdf", "application/pdf", "test content".getBytes());
        testIv = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        testAccountHash = "abcdef1234567890";

        // Set base directory to temp directory
        ReflectionTestUtils.setField(fileStorageService, "baseDir", tempDir.toString());
    }

    @Test
    @DisplayName("storeEncrypted - should successfully store encrypted file")
    void storeEncrypted_Success() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate);
        assertThat(result).isNotNull();
        assertThat(result.file()).isNotNull();
        assertThat(result.file().getName()).endsWith(".pdf.enc");
        assertThat(result.initializationVector()).isEqualTo(testIv);
        verify(encryptionService).computeAccountNumberHash(testAccountNumber);
        verify(encryptionService).generateInitializationVector();
        verify(encryptionService).encryptToFile(any(), any(), eq(testIv));
    }

    @Test
    @DisplayName("storeEncrypted - should create directory structure")
    void storeEncrypted_CreatesDirectories() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate);
        var parentDir = result.file().getParentFile();
        assertThat(parentDir).exists();
        assertThat(parentDir).isDirectory();
        assertThat(parentDir.getPath()).contains("statements");
        assertThat(parentDir.getPath()).contains(testAccountHash);
        assertThat(parentDir.getPath()).contains("2024");
        assertThat(parentDir.getPath()).contains("01");
    }

    @Test
    @DisplayName("storeEncrypted - should use correct file naming convention")
    void storeEncrypted_CorrectFileName() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate);
        String fileName = result.file().getName();
        assertThat(fileName).isEqualTo(testId + ".pdf.enc");
    }

    @Test
    @DisplayName("storeEncrypted - should handle different months correctly")
    void storeEncrypted_DifferentMonths() throws IOException {
        var decemberDate = LocalDate.of(2024, 12, 25);
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, decemberDate);
        assertThat(result.file().getPath()).contains("2024");
        assertThat(result.file().getPath()).contains("12");
    }

    @Test
    @DisplayName("storeEncrypted - should handle single digit months with zero padding")
    void storeEncrypted_SingleDigitMonth() throws IOException {
        var januaryDate = LocalDate.of(2024, 1, 1);
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, januaryDate);
        assertThat(result.file().getPath()).contains("01"); // Zero-padded
    }

    @Test
    @DisplayName("storeEncrypted - should throw exception for null account hash")
    void storeEncrypted_NullAccountHash() {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(null);
        assertThatThrownBy(
                        () -> fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("accountNumberHash must be provided");
        verify(encryptionService).computeAccountNumberHash(testAccountNumber);
        verify(encryptionService, never()).generateInitializationVector();
    }

    @Test
    @DisplayName("storeEncrypted - should throw exception for blank account hash")
    void storeEncrypted_BlankAccountHash() {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn("   ");
        assertThatThrownBy(
                        () -> fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("accountNumberHash must be provided");
        verify(encryptionService).computeAccountNumberHash(testAccountNumber);
    }

    @Test
    @DisplayName("storeEncrypted - should throw exception when encryption fails")
    void storeEncrypted_EncryptionFailure() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doThrow(new IOException("Encryption error")).when(encryptionService).encryptToFile(any(), any(), any());
        assertThatThrownBy(
                        () -> fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("Failed to encrypt and store file");
        verify(encryptionService).encryptToFile(any(), any(), any());
    }

    @Test
    @DisplayName("storeEncrypted - should handle different years correctly")
    void storeEncrypted_DifferentYears() throws IOException {
        var futureDate = LocalDate.of(2025, 6, 15);
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, futureDate);
        assertThat(result.file().getPath()).contains("2025");
        assertThat(result.file().getPath()).contains("06");
    }

    @Test
    @DisplayName("storeEncrypted - should reuse existing directory structure")
    void storeEncrypted_ReuseExistingDirectory() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result1 =
                fileStorageService.storeEncrypted(UUID.randomUUID(), testFile, testAccountNumber, testStatementDate);
        FileStorageService.FileStorageResult result2 =
                fileStorageService.storeEncrypted(UUID.randomUUID(), testFile, testAccountNumber, testStatementDate);
        assertThat(result1.file().getParentFile()).isEqualTo(result2.file().getParentFile());
        verify(encryptionService, times(2)).encryptToFile(any(), any(), any());
    }

    @Test
    @DisplayName("storeEncrypted - should handle different account numbers")
    void storeEncrypted_DifferentAccounts() throws IOException {
        String account1 = "111111111";
        String account2 = "222222222";
        String hash1 = "hash1";
        String hash2 = "hash2";
        when(encryptionService.computeAccountNumberHash(account1)).thenReturn(hash1);
        when(encryptionService.computeAccountNumberHash(account2)).thenReturn(hash2);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result1 =
                fileStorageService.storeEncrypted(UUID.randomUUID(), testFile, account1, testStatementDate);
        FileStorageService.FileStorageResult result2 =
                fileStorageService.storeEncrypted(UUID.randomUUID(), testFile, account2, testStatementDate);
        assertThat(result1.file().getPath()).contains(hash1);
        assertThat(result2.file().getPath()).contains(hash2);
        assertThat(result1.file().getParentFile()).isNotEqualTo(result2.file().getParentFile());
    }

    @Test
    @DisplayName("storeEncrypted - should pass input stream to encryption service")
    void storeEncrypted_PassesInputStream() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate);
        verify(encryptionService).encryptToFile(any(), any(), eq(testIv));
    }

    @Test
    @DisplayName("storeEncrypted - FileStorageResult should be immutable record")
    void storeEncrypted_ResultIsImmutable() throws IOException {
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, testStatementDate);
        var file = result.file();
        var iv = result.initializationVector();
        assertThat(file).isNotNull();
        assertThat(iv).isNotNull();
        assertThat(iv).isEqualTo(testIv);
    }

    @Test
    @DisplayName("storeEncrypted - should handle leap year dates")
    void storeEncrypted_LeapYearDate() throws IOException {
        var leapYearDate = LocalDate.of(2024, 2, 29);
        when(encryptionService.computeAccountNumberHash(testAccountNumber)).thenReturn(testAccountHash);
        when(encryptionService.generateInitializationVector()).thenReturn(testIv);
        doNothing().when(encryptionService).encryptToFile(any(), any(), any());
        FileStorageService.FileStorageResult result =
                fileStorageService.storeEncrypted(testId, testFile, testAccountNumber, leapYearDate);
        assertThat(result).isNotNull();
        assertThat(result.file().getPath()).contains("2024");
        assertThat(result.file().getPath()).contains("02");
    }
}
