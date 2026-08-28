package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.audit.AuditService;
import com.example.statementservice.shared.ContentDigest;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.DuplicateStatementException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.StatementUploadException;
import com.example.statementservice.statement.UploadedFile;
import com.example.statementservice.statement.upload.infrastructure.MultipartFileAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementUploadService Unit Tests")
class StatementUploadServiceTest {

    @Mock
    private UploadRequestValidator uploadRequestValidator;

    @Mock
    private StatementService statementService;

    @Mock
    private AuditService auditService;

    @Mock
    private ContentDigest contentDigest;

    @InjectMocks
    private StatementUploadService statementUploadService;

    private String testMessageDigest;
    private UploadedFile testFile;
    private String testAccountNumber;
    private String testDate;
    private RequestInfo testRequestInfo;
    private UploadResponseDto testUploadResponse;

    private String expectedContentHash;

    @BeforeEach
    void setUp() throws IOException {
        testMessageDigest = "a".repeat(64);
        testFile = new MultipartFileAdapter(
                new MockMultipartFile("file", "statement.pdf", "application/pdf", "test content".getBytes()));
        expectedContentHash = "c".repeat(64);
        lenient().when(contentDigest.hexOf(any(InputStream.class))).thenReturn(expectedContentHash);
        lenient().when(uploadRequestValidator.isValidAccountNumber(any())).thenCallRealMethod();
        testAccountNumber = "123456789";
        testDate = "2024-01-15";
        testRequestInfo = new RequestInfo("192.168.1.1", "Mozilla/5.0", "testUser");
        testUploadResponse = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .fileName("statement.pdf")
                .fileSize(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void GivenValidUpload_WhenUpload_ThenReturnsResponseAndRecordsSuccessAudit() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        doNothing().when(auditService).record(any(), any(), any(), any(), any(), any());
        UploadResponseDto result = statementUploadService.upload(
                testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUploadResponse);
        verify(uploadRequestValidator).validateFileUploadInputs(testFile, testAccountNumber, testDate);
        verify(statementService)
                .uploadStatement(
                        eq(testAccountNumber),
                        eq(LocalDate.parse(testDate)),
                        eq(testFile),
                        eq("testUser"),
                        eq(expectedContentHash));
        verify(auditService).record(any(), any(), eq(testAccountNumber), isNull(), eq("testUser"), any(Map.class));
    }

    @Test
    void GivenNullPerformedBy_WhenUpload_ThenAdminIsUsedAsActor() {
        RequestInfo infoWithNullUser = new RequestInfo("192.168.1.1", "Mozilla/5.0", null);
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, infoWithNullUser);
        verify(statementService).uploadStatement(any(), any(), any(), eq("admin"), any());
        verify(auditService).record(any(), any(), any(), any(), eq("admin"), any());
    }

    @Test
    void GivenUploadRequest_WhenUpload_ThenInputsAreValidatedBeforeProcessing() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(uploadRequestValidator).validateFileUploadInputs(testFile, testAccountNumber, testDate);
    }

    @Test
    void GivenValidationFailure_WhenUpload_ThenRethrowsAndRecordsFailureAudit() {
        doThrow(new InvalidMessageDigestException("Invalid digest"))
                .when(uploadRequestValidator)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("Invalid digest");
        verify(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
                .record(
                        eq("UPLOAD_FAILED"),
                        isNull(),
                        eq(testAccountNumber),
                        isNull(),
                        eq("testUser"),
                        detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "validation_failed");
    }

    @Test
    void GivenDigestMismatch_WhenUpload_ThenRecordsUploadFailedAuditWithDigestMismatchReasonAndRethrows() {
        // Given
        doThrow(new DigestMismatchException("X-Message-Digest does not match file contents"))
                .when(uploadRequestValidator)
                .validateMessageDigest(any(), any());

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(DigestMismatchException.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
                .record(eq("UPLOAD_FAILED"), isNull(), eq(testAccountNumber), isNull(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "digest_mismatch");
    }

    @Test
    void GivenStorageFailureDuringUpload_WhenUpload_ThenRecordsUploadFailedAuditWithUploadErrorReason() {
        // Given
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenThrow(new StatementUploadException("Failed to encrypt and store file"));

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(StatementUploadException.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
                .record(eq("UPLOAD_FAILED"), isNull(), eq(testAccountNumber), isNull(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "upload_error");
    }

    @Test
    void GivenDuplicateStatement_WhenUpload_ThenRecordsUploadFailedAuditWithDuplicateStatementReason() {
        // Given
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateStatementException(
                        "A statement already exists for this account number and statement date"));

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(DuplicateStatementException.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
                .record(eq("UPLOAD_FAILED"), isNull(), eq(testAccountNumber), isNull(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "duplicate_statement");
    }

    @Test
    void GivenUnexpectedRuntimeFailure_WhenUpload_ThenRecordsUploadFailedAuditWithUnexpectedReason() {
        // Given
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(IllegalStateException.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("UPLOAD_FAILED"), isNull(), any(), isNull(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "unexpected");
    }

    @Test
    void GivenInvalidAccountNumber_WhenUpload_ThenUploadFailedAuditOmitsAccountNumber() {
        // Given: the rejected value must not be persisted as an account identifier
        var garbageAccountNumber = "'; DROP TABLE statements; --";
        doThrow(new InvalidAccountNumberException("Invalid account number"))
                .when(uploadRequestValidator)
                .validateFileUploadInputs(any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, garbageAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidAccountNumberException.class);
        verify(auditService).record(eq("UPLOAD_FAILED"), isNull(), isNull(), isNull(), any(), any(Map.class));
    }

    @Test
    void GivenFailureAuditRecordingAlsoThrows_WhenUpload_ThenOriginalExceptionStillPropagates() {
        // Given
        doThrow(new DigestMismatchException("mismatch"))
                .when(uploadRequestValidator)
                .validateFileUploadInputs(any(), any(), any());
        doThrow(new RuntimeException("audit down")).when(auditService).record(any(), any(), any(), any(), any(), any());

        // When / Then: the audit failure must not mask the business failure
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(DigestMismatchException.class)
                .hasMessage("mismatch");
    }

    @Test
    void GivenInvalidAccountNumber_WhenUpload_ThenThrowsWithoutCallingStatementService() {
        doThrow(new InvalidAccountNumberException("Invalid account"))
                .when(uploadRequestValidator)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidAccountNumberException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
    }

    @Test
    void GivenInvalidDate_WhenUpload_ThenThrowsWithoutCallingStatementService() {
        doThrow(new InvalidDateException("Invalid date"))
                .when(uploadRequestValidator)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidDateException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
    }

    @Test
    void GivenUploadRequest_WhenUpload_ThenAuditDetailsIncludeIpAndUserAgent() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("ip", "192.168.1.1");
        assertThat(details).containsEntry("userAgent", "Mozilla/5.0");
    }

    @Test
    void GivenSuccessfulUpload_WhenUpload_ThenRecordsUploadSuccessAction() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(auditService)
                .record(
                        eq("UPLOAD_SUCCESS"),
                        any(UUID.class),
                        eq(testAccountNumber),
                        isNull(),
                        eq("testUser"),
                        any(Map.class));
    }

    @Test
    void GivenSuccessfulUpload_WhenUpload_ThenAuditIncludesStatementId() {
        UUID statementId = UUID.randomUUID();
        UploadResponseDto response = UploadResponseDto.builder()
                .statementId(statementId)
                .fileName("test.pdf")
                .fileSize(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(response);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(auditService).record(any(), eq(statementId), any(), any(), any(), any());
    }

    @Test
    void GivenAuditRecordingThrows_WhenUpload_ThenUploadStillSucceeds() {

        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        doThrow(new RuntimeException("Audit failure"))
                .when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        UploadResponseDto result = statementUploadService.upload(
                testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUploadResponse);
        verify(statementService).uploadStatement(any(), any(), any(), any(), any());
    }

    @Test
    void GivenIsoDateString_WhenUpload_ThenDateIsParsedToLocalDate() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, "2024-12-25", testRequestInfo);
        verify(statementService).uploadStatement(any(), eq(LocalDate.of(2024, 12, 25)), any(), any(), any());
    }

    @Test
    void GivenUploadRequest_WhenUpload_ThenAllParametersReachStatementService() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(statementService)
                .uploadStatement(
                        eq(testAccountNumber),
                        eq(LocalDate.parse(testDate)),
                        eq(testFile),
                        eq("testUser"),
                        eq(expectedContentHash));
    }

    @Test
    void GivenCustomPerformedBy_WhenUpload_ThenThatActorIsUsedThroughout() {
        RequestInfo customUser = new RequestInfo("10.0.0.1", "Chrome", "customUser");
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, customUser);
        verify(statementService).uploadStatement(any(), any(), any(), eq("customUser"), any());
        verify(auditService).record(any(), any(), any(), any(), eq("customUser"), any());
    }

    @Test
    void GivenCustomClientIp_WhenUpload_ThenAuditDetailsCarryThatIp() {
        RequestInfo customInfo = new RequestInfo("203.0.113.1", "Safari", "user");
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, customInfo);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("ip", "203.0.113.1");
    }

    @Test
    void GivenValidUpload_WhenUpload_ThenSameStreamedHashIsValidatedAgainstHeaderAndPersisted() {
        // Given
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);

        // When
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);

        // Then
        var validatedHash = ArgumentCaptor.forClass(String.class);
        var persistedHash = ArgumentCaptor.forClass(String.class);
        verify(uploadRequestValidator).validateMessageDigest(validatedHash.capture(), eq(testMessageDigest));
        verify(statementService).uploadStatement(any(), any(), any(), any(), persistedHash.capture());
        assertThat(validatedHash.getValue()).isEqualTo(expectedContentHash);
        assertThat(persistedHash.getValue()).isEqualTo(expectedContentHash);
    }

    @Test
    void GivenUnreadableFile_WhenUpload_ThenThrowsDigestComputationExceptionAndAuditsValidationFailed()
            throws IOException {
        // Given
        var rawUnreadableFile = mock(MultipartFile.class);
        when(rawUnreadableFile.getInputStream()).thenThrow(new IOException("disk error"));
        var unreadableFile = new MultipartFileAdapter(rawUnreadableFile);

        // When / Then
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, unreadableFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(DigestComputationException.class)
                .hasMessageContaining("Failed to compute file digest");
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService)
                .record(eq("UPLOAD_FAILED"), isNull(), eq(testAccountNumber), isNull(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("reason", "validation_failed");
    }

    @Test
    void GivenUploadAudit_WhenUpload_ThenSignedLinkIdIsNull() {
        doNothing().when(uploadRequestValidator).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(auditService).record(any(), any(), any(), isNull(), any(), any());
    }
}
