package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.statementservice.model.AuditAction;
import com.example.statementservice.model.DownloadOutcome;
import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import com.example.statementservice.util.LinkValidationResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadService Unit Tests")
class DownloadServiceTest {

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DownloadService downloadService;

    @TempDir
    Path tempDir;

    private SignedLink testLink;
    private Statement testStatement;
    private String testToken;
    private Long testExpires;
    private String testClientIp;
    private String testUserAgent;
    private String testPerformedBy;
    private UUID testStatementId;
    private UUID testLinkId;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testLinkId = UUID.randomUUID();
        testToken = "test-token-1234567890";
        testClientIp = "192.168.1.1";
        testUserAgent = "Mozilla/5.0";
        testPerformedBy = "testUser";

        testLink = new SignedLink();
        testLink.setId(testLinkId);
        testLink.setStatementId(testStatementId);
        testLink.setToken(testToken);
        testLink.setCreatedAt(OffsetDateTime.now());
        testLink.setExpiresAt(OffsetDateTime.now().plusHours(1));
        testLink.setUsed(false);

        testExpires = testLink.getExpiresAt().toEpochSecond();

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber("123456789");
        testStatement.setStatementDate(LocalDate.of(2024, 1, 1));
        testStatement.setUploadFileName("statement.pdf");
        testStatement.setFilePath("/test/path/statement.pdf");
        testStatement.setSizeBytes(1024L);
        testStatement.setEncrypted(true);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should successfully download when all validations pass")
    void validateAndStreamDetailed_Success() throws Exception {

        var tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());

        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        var mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(encryptionService.decryptFileToStream(any(File.class))).thenReturn(mockStream);

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).isPresent();
        assertThat(result.stream().get()).isEqualTo(mockStream);

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(statementRepository).findById(testStatementId);
        verify(encryptionService).decryptFileToStream(any(File.class));
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_SUCCESS.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName(
            "validateAndStreamDetailed - should return INVALID_SIGNATURE when link validation fails with NOT_FOUND")
    void validateAndStreamDetailed_InvalidSignature() {

        var invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(invalidResult);

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(statementRepository, encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return LINK_EXPIRED_OR_USED when link is expired")
    void validateAndStreamDetailed_ExpiredLink() {

        var expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(expiredResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED_OR_USED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return LINK_EXPIRED_OR_USED when link is already used")
    void validateAndStreamDetailed_UsedLink() {

        var usedResult = LinkValidationResult.used(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(usedResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED_OR_USED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return STATEMENT_NOT_FOUND when link not found")
    void validateAndStreamDetailed_LinkNotFound() {

        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(notFoundResult);

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return STATEMENT_NOT_FOUND when statement not in database")
    void validateAndStreamDetailed_StatementNotFound() {

        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.empty());

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        isNull(),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return FILE_MISSING when file does not exist")
    void validateAndStreamDetailed_FileMissing() {

        testStatement.setFilePath("/non/existent/path/file.pdf");
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.FILE_MISSING);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return DECRYPTION_FAILED on decryption error")
    void validateAndStreamDetailed_DecryptionFailed() throws Exception {

        var tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());

        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(encryptionService.decryptFileToStream(any(File.class)))
                .thenThrow(new RuntimeException("Decryption error"));

        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);

        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken, testExpires);
        verify(statementRepository).findById(testStatementId);
        verify(encryptionService).decryptFileToStream(any(File.class));
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should still return stream even if audit recording fails")
    void validateAndStreamDetailed_AuditFailureDoesNotPreventDownload() throws Exception {

        var tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));
        var mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(encryptionService.decryptFileToStream(any(File.class))).thenReturn(mockStream);
        doThrow(new RuntimeException("Audit failure"))
                .when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).isPresent();
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should handle null clientIp gracefully")
    void validateAndStreamDetailed_NullClientIp() {

        var invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(invalidResult);
        var result =
                downloadService.validateAndStreamDetailed(testToken, testExpires, null, testUserAgent, testPerformedBy);
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should handle null userAgent gracefully")
    void validateAndStreamDetailed_NullUserAgent() {

        var invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(invalidResult);
        var result =
                downloadService.validateAndStreamDetailed(testToken, testExpires, testClientIp, null, testPerformedBy);
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should fetch account number when statement exists")
    void validateAndStreamDetailed_FetchesAccountNumber() {

        var expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validateAndConsume(testToken, testExpires)).thenReturn(expiredResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));
        downloadService.validateAndStreamDetailed(testToken, testExpires, testClientIp, testUserAgent, testPerformedBy);
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }
}
