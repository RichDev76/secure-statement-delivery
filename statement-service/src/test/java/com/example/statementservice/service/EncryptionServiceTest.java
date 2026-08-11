package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statementservice.config.MasterKeyProvider;
import com.example.statementservice.exception.DigestComputationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EncryptionService Unit Tests")
class EncryptionServiceTest {

    @Mock
    private MasterKeyProvider masterKeyProvider;

    private EncryptionService encryptionService;

    @TempDir
    Path tempDir;

    private byte[] testMasterKey;

    @BeforeEach
    void setUp() {
        testMasterKey = new byte[32];
        new SecureRandom().nextBytes(testMasterKey);

        when(masterKeyProvider.getKey()).thenReturn(testMasterKey);
        encryptionService = new EncryptionService(masterKeyProvider);
    }

    @Test
    @DisplayName("generateInitializationVector - should generate 12-byte IV")
    void generateInitializationVector_ValidLength() {
        var iv = encryptionService.generateInitializationVector();
        assertThat(iv).isNotNull();
        assertThat(iv).hasSize(12);
    }

    @Test
    @DisplayName("generateInitializationVector - should generate different IVs on each call")
    void generateInitializationVector_Uniqueness() {
        var iv1 = encryptionService.generateInitializationVector();
        var iv2 = encryptionService.generateInitializationVector();
        var iv3 = encryptionService.generateInitializationVector();
        assertThat(iv1).isNotEqualTo(iv2);
        assertThat(iv2).isNotEqualTo(iv3);
        assertThat(iv1).isNotEqualTo(iv3);
    }

    @Test
    @DisplayName("generateInitializationVector - should not generate all zeros")
    void generateInitializationVector_NotAllZeros() {
        var iv = encryptionService.generateInitializationVector();
        var hasNonZero = false;
        for (byte b : iv) {
            if (b != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertThat(hasNonZero).isTrue();
    }

    @Test
    @DisplayName("encryptToFile - should successfully encrypt data to file")
    void encryptToFile_Success() throws IOException {
        var plaintext = "This is sensitive data that needs encryption";
        var iv = encryptionService.generateInitializationVector();
        var outputFile = tempDir.resolve("encrypted.dat").toFile();
        var inputStream = new ByteArrayInputStream(plaintext.getBytes());
        encryptionService.encryptToFile(inputStream, outputFile, iv);
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
        var fileContent = Files.readAllBytes(outputFile.toPath());
        assertThat(fileContent.length).isGreaterThan(12);
        var fileIv = new byte[12];
        System.arraycopy(fileContent, 0, fileIv, 0, 12);
        assertThat(fileIv).isEqualTo(iv);
    }

    @Test
    @DisplayName("encryptToFile and decryptFileToStream - round trip should preserve data")
    void encryptDecrypt_RoundTrip() throws IOException {

        String originalText = "Secret message for encryption test";
        byte[] iv = encryptionService.generateInitializationVector();
        File encryptedFile = tempDir.resolve("test-encrypted.enc").toFile();
        InputStream inputStream = new ByteArrayInputStream(originalText.getBytes());

        encryptionService.encryptToFile(inputStream, encryptedFile, iv);

        InputStream decryptedStream = encryptionService.decryptFileToStream(encryptedFile);
        String decryptedText = new String(decryptedStream.readAllBytes());
        decryptedStream.close();

        assertThat(decryptedText).isEqualTo(originalText);
    }

    @Test
    @DisplayName("encryptToFile - should encrypt empty input")
    void encryptToFile_EmptyInput() throws IOException {

        byte[] iv = encryptionService.generateInitializationVector();
        File outputFile = tempDir.resolve("empty-encrypted.dat").toFile();
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        encryptionService.encryptToFile(emptyStream, outputFile, iv);

        assertThat(outputFile).exists();

        assertThat(outputFile.length()).isGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("encryptToFile - should encrypt large data")
    void encryptToFile_LargeData() throws IOException {

        byte[] largeData = new byte[100000]; // 100KB
        new SecureRandom().nextBytes(largeData);
        byte[] iv = encryptionService.generateInitializationVector();
        File outputFile = tempDir.resolve("large-encrypted.dat").toFile();
        InputStream inputStream = new ByteArrayInputStream(largeData);

        encryptionService.encryptToFile(inputStream, outputFile, iv);

        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(largeData.length);
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for missing IV")
    void decryptFileToStream_MissingIv() throws IOException {

        File invalidFile = tempDir.resolve("invalid.enc").toFile();
        Files.write(invalidFile.toPath(), new byte[] {1, 2, 3, 4, 5});

        assertThatThrownBy(() -> encryptionService.decryptFileToStream(invalidFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for corrupted data")
    void decryptFileToStream_CorruptedData() throws IOException {
        File corruptedFile = tempDir.resolve("corrupted.enc").toFile();
        byte[] corruptedData = new byte[50];
        new SecureRandom().nextBytes(corruptedData);
        Files.write(corruptedFile.toPath(), corruptedData);

        assertThatThrownBy(() -> {
                    InputStream stream = encryptionService.decryptFileToStream(corruptedFile);
                    stream.readAllBytes(); // Trigger decryption
                    stream.close();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for non-existent file")
    void decryptFileToStream_FileNotFound() {

        File nonExistentFile = tempDir.resolve("does-not-exist.enc").toFile();

        assertThatThrownBy(() -> encryptionService.decryptFileToStream(nonExistentFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("computeSha256Hex - should compute correct SHA-256 hash")
    void computeSha256Hex_ValidHash() {

        String content = "test content";
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content.getBytes());

        String hash = encryptionService.computeSha256Hex(file);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("computeSha256Hex - should produce consistent hash for same content")
    void computeSha256Hex_Consistency() {

        String content = "consistent content";
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", content.getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", content.getBytes());

        String hash1 = encryptionService.computeSha256Hex(file1);
        String hash2 = encryptionService.computeSha256Hex(file2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeSha256Hex - should produce different hash for different content")
    void computeSha256Hex_DifferentContent() {

        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", "content1".getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", "content2".getBytes());

        String hash1 = encryptionService.computeSha256Hex(file1);
        String hash2 = encryptionService.computeSha256Hex(file2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("computeSha256Hex - should handle empty file")
    void computeSha256Hex_EmptyFile() {

        MultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        String hash = encryptionService.computeSha256Hex(emptyFile);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        // SHA-256 of empty input is a known value
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("computeSha256Hex - should throw exception when file access fails")
    void computeSha256Hex_FileAccessError() throws IOException {

        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getBytes()).thenThrow(new IOException("File read error"));

        assertThatThrownBy(() -> encryptionService.computeSha256Hex(mockFile))
                .isInstanceOf(DigestComputationException.class)
                .hasMessageContaining("Failed to compute file digest");
    }

    @Test
    @DisplayName("computeAccountNumberHash - should compute correct hash")
    void computeAccountNumberHash_ValidHash() {

        String accountNumber = "123456789";

        String hash = encryptionService.computeAccountNumberHash(accountNumber);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("computeAccountNumberHash - should produce consistent hash")
    void computeAccountNumberHash_Consistency() {

        String accountNumber = "987654321";

        String hash1 = encryptionService.computeAccountNumberHash(accountNumber);
        String hash2 = encryptionService.computeAccountNumberHash(accountNumber);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should produce different hashes for different accounts")
    void computeAccountNumberHash_DifferentAccounts() {

        String account1 = "111111111";
        String account2 = "222222222";

        String hash1 = encryptionService.computeAccountNumberHash(account1);
        String hash2 = encryptionService.computeAccountNumberHash(account2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should trim whitespace")
    void computeAccountNumberHash_TrimWhitespace() {

        String account1 = "123456789";
        String account2 = "  123456789  ";

        String hash1 = encryptionService.computeAccountNumberHash(account1);
        String hash2 = encryptionService.computeAccountNumberHash(account2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle special characters")
    void computeAccountNumberHash_SpecialCharacters() {

        String accountNumber = "ACC-123-456-789";

        String hash = encryptionService.computeAccountNumberHash(accountNumber);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle long account numbers")
    void computeAccountNumberHash_LongAccountNumber() {

        String longAccount = "1234567890".repeat(5); // 50 digits

        String hash = encryptionService.computeAccountNumberHash(longAccount);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle Unicode characters")
    void computeAccountNumberHash_Unicode() {

        String unicodeAccount = "账户123456789";

        String hash = encryptionService.computeAccountNumberHash(unicodeAccount);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }
}
