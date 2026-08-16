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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLog;
import com.example.statementservice.audit.AuditLogRepository;
import com.example.statementservice.audit.AuditService;
import com.example.statementservice.statement.FileCipherException;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.signedlink.LinkValidationResult;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiterPort;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.slf4j.LoggerFactory;

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
    private SignedLinkRateLimiterPort rateLimiter;

    @Mock
    private AuditLogRepository auditLogRepository;

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
                .when(auditLogRepository.findBySignedLinkIdAndAction(any(), any()))
                .thenReturn(List.of());
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).contains(mockStream);
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
    void GivenLinkNotFound_WhenValidateAndStreamDetailed_ThenReturnsStatementNotFoundAndAuditsWithoutStatementLookup() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.INVALID_SIGNATURE);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.FILE_MISSING);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).isPresent();
    }

    @Test
    void GivenNullClientIp_WhenValidateAndStreamDetailed_ThenAuditIsStillRecorded() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, null, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    void GivenNullUserAgent_WhenValidateAndStreamDetailed_ThenAuditIsStillRecorded() {
        // Given
        var notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validate(testToken, testExpires, testLinkId, FILE_NAME))
                .thenReturn(notFoundResult);

        // When
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, null, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
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
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.RATE_LIMITED);
        assertThat(result.stream()).isEmpty();
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
        var result = downloadService.validateAndStreamDetailed(
                testToken, testExpires, null, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.INVALID_SIGNATURE);
        verifyNoInteractions(rateLimiter);
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

        var priorDetails = Map.<String, Object>of("ip", "10.0.0.99", "userAgent", "curl/8.0");
        var priorLog = new AuditLog();
        priorLog.setDetails(priorDetails);
        when(auditLogRepository.findBySignedLinkIdAndAction(testLinkId, AuditAction.DOWNLOAD_SUCCESS.getValue()))
                .thenReturn(List.of(priorLog));

        var downloadServiceLogger = (Logger) LoggerFactory.getLogger(DownloadService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        downloadServiceLogger.addAppender(appender);

        try {
            // When
            var result = downloadService.validateAndStreamDetailed(
                    testToken, testExpires, testLinkId, FILE_NAME, testClientIp, testUserAgent, testPerformedBy);

            // Then
            assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("different ip/userAgent")
                            .contains(testLinkId.toString()));
        } finally {
            downloadServiceLogger.detachAppender(appender);
        }
    }
}
