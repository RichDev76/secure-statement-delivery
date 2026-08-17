package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.audit.AuditService;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.StatementService;
import java.io.IOException;
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
    private ValidationUtil validationUtil;

    @Mock
    private StatementService statementService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private StatementUploadService statementUploadService;

    private String testMessageDigest;
    private MultipartFile testFile;
    private String testAccountNumber;
    private String testDate;
    private RequestInfo testRequestInfo;
    private UploadResponseDto testUploadResponse;

    private String expectedContentHash;

    @BeforeEach
    void setUp() {
        testMessageDigest = "a".repeat(64);
        testFile = new MockMultipartFile("file", "statement.pdf", "application/pdf", "test content".getBytes());
        expectedContentHash = Sha256Digest.hexOf("test content".getBytes());
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
    @DisplayName("upload - should successfully upload and audit statement")
    void upload_Success() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        doNothing().when(auditService).record(any(), any(), any(), any(), any(), any());
        UploadResponseDto result = statementUploadService.upload(
                testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUploadResponse);
        verify(validationUtil).validateFileUploadInputs(testFile, testAccountNumber, testDate);
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
    @DisplayName("upload - should use 'admin' when performedBy is null")
    void upload_NullPerformedBy() {
        RequestInfo infoWithNullUser = new RequestInfo("192.168.1.1", "Mozilla/5.0", null);
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, infoWithNullUser);
        verify(statementService).uploadStatement(any(), any(), any(), eq("admin"), any());
        verify(auditService).record(any(), any(), any(), any(), eq("admin"), any());
    }

    @Test
    @DisplayName("upload - should validate all inputs before processing")
    void upload_ValidatesInputs() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(validationUtil).validateFileUploadInputs(testFile, testAccountNumber, testDate);
    }

    @Test
    @DisplayName("upload - should throw exception when validation fails and audit the failure")
    void upload_ValidationFails() {
        doThrow(new InvalidMessageDigestException("Invalid digest"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("Invalid digest");
        verify(validationUtil).validateFileUploadInputs(any(), any(), any());
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
                .when(validationUtil)
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
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
    void GivenUnexpectedRuntimeFailure_WhenUpload_ThenRecordsUploadFailedAuditWithUnexpectedReason() {
        // Given
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
                .when(validationUtil)
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
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any());
        doThrow(new RuntimeException("audit down")).when(auditService).record(any(), any(), any(), any(), any(), any());

        // When / Then: the audit failure must not mask the business failure
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(DigestMismatchException.class)
                .hasMessage("mismatch");
    }

    @Test
    @DisplayName("upload - should throw exception for invalid account number")
    void upload_InvalidAccountNumber() {
        doThrow(new InvalidAccountNumberException("Invalid account"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidAccountNumberException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should throw exception for invalid date")
    void upload_InvalidDate() {
        doThrow(new InvalidDateException("Invalid date"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any());
        assertThatThrownBy(() -> statementUploadService.upload(
                        testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo))
                .isInstanceOf(InvalidDateException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should include audit details with IP and user agent")
    void upload_AuditDetailsIncluded() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
    @DisplayName("upload - should record UPLOAD_SUCCESS action")
    void upload_RecordsCorrectAction() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
    @DisplayName("upload - should include statement ID in audit")
    void upload_AuditIncludesStatementId() {
        UUID statementId = UUID.randomUUID();
        UploadResponseDto response = UploadResponseDto.builder()
                .statementId(statementId)
                .fileName("test.pdf")
                .fileSize(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(response);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(auditService).record(any(), eq(statementId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should not fail if audit recording throws exception")
    void upload_AuditFailureDoesNotAffectUpload() {

        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
    @DisplayName("upload - should parse date string to LocalDate")
    void upload_ParsesDateCorrectly() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, "2024-12-25", testRequestInfo);
        verify(statementService).uploadStatement(any(), eq(LocalDate.of(2024, 12, 25)), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should pass all parameters to statementService")
    void upload_PassesAllParameters() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
    @DisplayName("upload - should handle different user names")
    void upload_DifferentUserNames() {
        RequestInfo customUser = new RequestInfo("10.0.0.1", "Chrome", "customUser");
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, customUser);
        verify(statementService).uploadStatement(any(), any(), any(), eq("customUser"), any());
        verify(auditService).record(any(), any(), any(), any(), eq("customUser"), any());
    }

    @Test
    @DisplayName("upload - should handle different IP addresses in audit")
    void upload_DifferentIpAddresses() {
        RequestInfo customInfo = new RequestInfo("203.0.113.1", "Safari", "user");
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
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
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);

        // When
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);

        // Then
        var validatedHash = ArgumentCaptor.forClass(String.class);
        var persistedHash = ArgumentCaptor.forClass(String.class);
        verify(validationUtil).validateMessageDigest(validatedHash.capture(), eq(testMessageDigest));
        verify(statementService).uploadStatement(any(), any(), any(), any(), persistedHash.capture());
        assertThat(validatedHash.getValue()).isEqualTo(expectedContentHash);
        assertThat(persistedHash.getValue()).isEqualTo(expectedContentHash);
    }

    @Test
    void GivenUnreadableFile_WhenUpload_ThenThrowsDigestComputationExceptionAndAuditsValidationFailed()
            throws IOException {
        // Given
        var unreadableFile = mock(MultipartFile.class);
        when(unreadableFile.getInputStream()).thenThrow(new IOException("disk error"));

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
    @DisplayName("upload - should set signedLinkId to null in audit")
    void upload_SignedLinkIdIsNull() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any());
        when(statementService.uploadStatement(any(), any(), any(), any(), any()))
                .thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate, testRequestInfo);
        verify(auditService).record(any(), any(), any(), isNull(), any(), any());
    }
}
