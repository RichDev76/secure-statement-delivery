package com.example.statementservice.statement.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.statement.FileCipherException;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.StatementStorageUnavailableException;
import com.example.statementservice.statement.signedlink.LinkValidationResult;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiter;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import com.example.statementservice.support.LogCapture;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadService Unit Tests")
class DownloadServiceTest {

    private static final String FILE_NAME = "statement.pdf";

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private StatementService statementService;

    @Mock
    private AuditService auditService;

    @Mock
    private SignedLinkRateLimiter rateLimiter;

    @InjectMocks
    private DownloadService downloadService;

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

        testExpires = testLink.getExpiresAt().toEpochSecond();

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber("123456789");
        testStatement.setStatementDate(LocalDate.of(2024, 1, 1));
        testStatement.setUploadFileName(FILE_NAME);
        testStatement.setStorageKey("statements/hash/2026/07/statement.pdf.enc");
        testStatement.setSizeBytes(1024L);
        testStatement.setEncrypted(true);

        // Most test paths reach the rate-limit check first (one test passes a null linkId, which
        // skips it entirely) and only the OK-outcome tests reach the anomaly check - both stubs
        // are lenient to avoid UnnecessaryStubbingException in the tests that don't use them.
        lenient().when(rateLimiter.tryConsume(testLinkId)).thenReturn(true);
        lenient()
                .when(auditService.hasPriorSuccessfulDownloadFromDifferentContext(any(), any(), any()))
                .thenReturn(false);
    }

    @Test
    void GivenValidLink_WhenValidateAndStreamDetailed_ThenReturnsOkWithDecryptedStreamAndRecordsSuccessAudit()
            throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        var mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(statementService.openDecryptedFile(testStatement)).thenReturn(mockStream);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(attempt.result()).isSameAs(mockStream);
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
    void GivenValidLink_WhenValidateAndStreamDetailed_ThenSuccessLogNeverContainsAccountNumber() throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement))
                .thenReturn(new ByteArrayInputStream("decrypted content".getBytes()));

        try (var logs = LogCapture.forClass(DownloadService.class)) {
            // When
            downloadService.validateAndStreamDetailed(
                    testToken,
                    testExpires,
                    testLinkId,
                    FILE_NAME,
                    testClientIp,
                    testUserAgent,
                    testPerformedBy,
                    stream -> stream);

            // Then
            assertThat(logs.lines())
                    .as("the success path must still log something - a guard only checking absence "
                            + "would pass trivially if the log statement were deleted")
                    .isNotEmpty()
                    .as("the account number must never reach a log line")
                    .noneMatch(line -> line.contains(testStatement.getAccountNumber()))
                    .as("statementId is the non-sensitive join key that replaces it")
                    .anyMatch(line -> line.contains(testStatementId.toString()));
        }
    }

    @Test
    void GivenLinkNotFound_WhenValidateAndStreamDetailed_ThenReturnsStatementNotFoundAndAuditsWithoutStatementLookup() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(attempt.result()).isNull();
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(statementService);
    }

    @Test
    void GivenExpiredLink_WhenValidateAndStreamDetailed_ThenReturnsLinkExpiredAndRecordsFailureAudit() {
        // Given
        var expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(expiredResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED);
        assertThat(attempt.result()).isNull();
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
    void GivenInvalidSignature_WhenValidateAndStreamDetailed_ThenReturnsInvalidSignatureOutcome() {
        // Given
        var invalidResult = LinkValidationResult.invalidSignature(null);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(invalidResult);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.INVALID_SIGNATURE);
        assertThat(attempt.result()).isNull();
    }

    @Test
    void GivenValidLinkButStatementMissingFromDatabase_WhenValidateAndStreamDetailed_ThenReturnsStatementNotFound()
            throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.empty());

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(attempt.result()).isNull();
        verify(statementService, never()).fileExists(any());
        verify(statementService, never()).openDecryptedFile(any());
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        isNull(),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    void GivenFileMissingFromStorage_WhenValidateAndStreamDetailed_ThenReturnsFileMissing() {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(false);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.FILE_MISSING);
        assertThat(attempt.result()).isNull();
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
    void GivenDecryptionErrors_WhenValidateAndStreamDetailed_ThenReturnsDecryptionFailed() throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement)).thenThrow(new RuntimeException("Decryption error"));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(attempt.result()).isNull();
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
    void GivenDekUnwrapFails_WhenValidateAndStreamDetailed_ThenReturnsDecryptionFailed() throws Exception {
        // Given: a corrupted/tampered encrypted_dek surfaces as a FileCipherException from
        // StatementService.openDecryptedFile, which must be caught by the same generic
        // decryption-failure handling as a stream-level decrypt error.
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement))
                .thenThrow(new FileCipherException("Unrecognised DEK wrap format"));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(attempt.result()).isNull();
    }

    @Test
    void GivenTransientIoFailureOnOpen_WhenValidateAndStreamDetailed_ThenReturnsStorageUnavailable() throws Exception {
        // Given: a mid-open network reset is an outage, not a crypto failure - retryable 503
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement)).thenThrow(new IOException("connection reset"));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STORAGE_UNAVAILABLE);
        assertThat(attempt.result()).isNull();
    }

    @Test
    void GivenAuditRecordingFails_WhenValidateAndStreamDetailed_ThenDownloadStillSucceeds() throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        var mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(statementService.openDecryptedFile(testStatement)).thenReturn(mockStream);
        doThrow(new RuntimeException("Audit failure"))
                .when(auditService)
                .record(any(), any(), any(), any(), any(), any());

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(attempt.result()).isNotNull();
    }

    @Test
    void GivenNullClientIp_WhenValidateAndStreamDetailed_ThenAuditIsStillRecorded() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, null, testUserAgent, testPerformedBy, stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    void GivenNullUserAgent_WhenValidateAndStreamDetailed_ThenAuditIsStillRecorded() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, null, testPerformedBy, stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    void GivenExpiredLinkWithKnownStatement_WhenValidateAndStreamDetailed_ThenAccountNumberIsFetchedForAudit() {
        // Given
        var expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(expiredResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
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
    void GivenRateLimitExceeded_WhenValidateAndStreamDetailed_ThenReturnsRateLimitedWithoutCallingValidate() {
        // Given: overrides the lenient default stub from setUp()
        when(rateLimiter.tryConsume(testLinkId)).thenReturn(false);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.RATE_LIMITED);
        assertThat(attempt.result()).isNull();
        verifyNoInteractions(signedLinkService, statementService);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    void GivenNullLinkId_WhenValidateAndStreamDetailed_ThenRateLimiterIsNeverConsulted() {
        // Given: SignedLinkService.validate() already rejects a null linkId as an invalid
        // signature - the rate limiter has nothing meaningful to key on in that case.
        var invalidResult = LinkValidationResult.invalidSignature(null);
        when(signedLinkService.validate(testToken, testExpires, null, FILE_NAME))
                .thenReturn(invalidResult);

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                null,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.INVALID_SIGNATURE);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void
            GivenFileExistenceCheckThrowsStorageUnavailable_WhenValidateAndStreamDetailed_ThenReturnsStorageUnavailableAndAuditsFailure()
                    throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement))
                .thenThrow(new StatementStorageUnavailableException("S3 outage", new RuntimeException("cause")));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STORAGE_UNAVAILABLE);
        assertThat(attempt.result()).isNull();
        verify(statementService, never()).openDecryptedFile(any());
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
    void
            GivenStorageUnavailableWhileOpeningFile_WhenValidateAndStreamDetailed_ThenReturnsStorageUnavailableAndAuditsFailure()
                    throws Exception {
        // Given: exists() succeeded but the S3 GetObject failed mid-download
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement))
                .thenThrow(new StatementStorageUnavailableException("S3 outage", new RuntimeException("cause")));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.STORAGE_UNAVAILABLE);
        assertThat(attempt.result()).isNull();
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("123456789"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
        verify(auditService, never())
                .record(eq(AuditAction.DOWNLOAD_SUCCESS.getValue()), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    void GivenFileMissingBetweenExistsAndOpen_WhenValidateAndStreamDetailed_ThenReturnsFileMissingAndAuditsFailure()
            throws Exception {
        // Given: the object was deleted between the exists() check and open()
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement))
                .thenThrow(new FileNotFoundException("No object found for the requested reference"));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.FILE_MISSING);
        assertThat(attempt.result()).isNull();
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
    void GivenSuspiciousRedemptionCheckThrows_WhenValidateAndStreamDetailed_ThenDownloadStillSucceeds()
            throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        var mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(statementService.openDecryptedFile(testStatement)).thenReturn(mockStream);
        when(auditService.hasPriorSuccessfulDownloadFromDifferentContext(testLinkId, testClientIp, testUserAgent))
                .thenThrow(new RuntimeException("audit query failed"));

        // When
        var attempt = downloadService.validateAndStreamDetailed(
                testToken,
                testExpires,
                testLinkId,
                FILE_NAME,
                testClientIp,
                testUserAgent,
                testPerformedBy,
                stream -> stream);

        // Then
        assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(attempt.result()).isSameAs(mockStream);
    }

    @Test
    void GivenPriorSuccessfulRedemptionFromDifferentIp_WhenValidateAndStreamDetailed_ThenLogsWarnAndStillSucceeds()
            throws Exception {
        // Given
        var validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(validResult);
        when(statementService.findStatementById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(statementService.fileExists(testStatement)).thenReturn(true);
        when(statementService.openDecryptedFile(testStatement))
                .thenReturn(new ByteArrayInputStream("decrypted content".getBytes()));
        when(auditService.hasPriorSuccessfulDownloadFromDifferentContext(testLinkId, testClientIp, testUserAgent))
                .thenReturn(true);

        try (var logs = LogCapture.forClass(DownloadService.class)) {
            // When
            var attempt = downloadService.validateAndStreamDetailed(
                    testToken,
                    testExpires,
                    testLinkId,
                    FILE_NAME,
                    testClientIp,
                    testUserAgent,
                    testPerformedBy,
                    stream -> stream);

            // Then
            assertThat(attempt.outcome()).isEqualTo(DownloadOutcome.OK);
            assertThat(logs.lines()).anySatisfy(message -> assertThat(message)
                    .contains("different ip/userAgent")
                    .contains(testLinkId.toString()));
        }
    }
}
