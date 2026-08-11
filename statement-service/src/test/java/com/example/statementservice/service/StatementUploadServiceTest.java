package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.exception.InvalidAccountNumberException;
import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.exception.InvalidMessageDigestException;
import com.example.statementservice.model.dto.UploadResponseDto;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import com.example.statementservice.util.ValidationUtil;
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
    private RequestInfoProvider requestInfoProvider;

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

    @BeforeEach
    void setUp() {
        testMessageDigest = "a".repeat(64);
        testFile = new MockMultipartFile("file", "statement.pdf", "application/pdf", "test content".getBytes());
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
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        doNothing().when(auditService).record(any(), any(), any(), any(), any(), any());
        UploadResponseDto result =
                statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUploadResponse);
        verify(validationUtil).validateFileUploadInputs(testFile, testMessageDigest, testAccountNumber, testDate);
        verify(requestInfoProvider).get();
        verify(statementService)
                .uploadStatement(eq(testAccountNumber), eq(LocalDate.parse(testDate)), eq(testFile), eq("testUser"));
        verify(auditService).record(any(), any(), eq(testAccountNumber), isNull(), eq("testUser"), any(Map.class));
    }

    @Test
    @DisplayName("upload - should use 'admin' when performedBy is null")
    void upload_NullPerformedBy() {
        RequestInfo infoWithNullUser = new RequestInfo("192.168.1.1", "Mozilla/5.0", null);
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(infoWithNullUser);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(statementService).uploadStatement(any(), any(), any(), eq("admin"));
        verify(auditService).record(any(), any(), any(), any(), eq("admin"), any());
    }

    @Test
    @DisplayName("upload - should validate all inputs before processing")
    void upload_ValidatesInputs() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(validationUtil).validateFileUploadInputs(testFile, testMessageDigest, testAccountNumber, testDate);
    }

    @Test
    @DisplayName("upload - should throw exception when validation fails")
    void upload_ValidationFails() {
        doThrow(new InvalidMessageDigestException("Invalid digest"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any(), any());
        assertThatThrownBy(
                        () -> statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("Invalid digest");
        verify(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        verify(statementService, never()).uploadStatement(any(), any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should throw exception for invalid account number")
    void upload_InvalidAccountNumber() {
        doThrow(new InvalidAccountNumberException("Invalid account"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any(), any());
        assertThatThrownBy(
                        () -> statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate))
                .isInstanceOf(InvalidAccountNumberException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should throw exception for invalid date")
    void upload_InvalidDate() {
        doThrow(new InvalidDateException("Invalid date"))
                .when(validationUtil)
                .validateFileUploadInputs(any(), any(), any(), any());
        assertThatThrownBy(
                        () -> statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate))
                .isInstanceOf(InvalidDateException.class);
        verify(statementService, never()).uploadStatement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should include audit details with IP and user agent")
    void upload_AuditDetailsIncluded() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("ip", "192.168.1.1");
        assertThat(details).containsEntry("userAgent", "Mozilla/5.0");
    }

    @Test
    @DisplayName("upload - should record UPLOAD_SUCCESS action")
    void upload_RecordsCorrectAction() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
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
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(response);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(auditService).record(any(), eq(statementId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should not fail if audit recording throws exception")
    void upload_AuditFailureDoesNotAffectUpload() {

        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        doThrow(new RuntimeException("Audit failure"))
                .when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        UploadResponseDto result =
                statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUploadResponse);
        verify(statementService).uploadStatement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("upload - should parse date string to LocalDate")
    void upload_ParsesDateCorrectly() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, "2024-12-25");
        verify(statementService).uploadStatement(any(), eq(LocalDate.of(2024, 12, 25)), any(), any());
    }

    @Test
    @DisplayName("upload - should pass all parameters to statementService")
    void upload_PassesAllParameters() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(statementService)
                .uploadStatement(eq(testAccountNumber), eq(LocalDate.parse(testDate)), eq(testFile), eq("testUser"));
    }

    @Test
    @DisplayName("upload - should handle different user names")
    void upload_DifferentUserNames() {
        RequestInfo customUser = new RequestInfo("10.0.0.1", "Chrome", "customUser");
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(customUser);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(statementService).uploadStatement(any(), any(), any(), eq("customUser"));
        verify(auditService).record(any(), any(), any(), any(), eq("customUser"), any());
    }

    @Test
    @DisplayName("upload - should handle different IP addresses in audit")
    void upload_DifferentIpAddresses() {
        RequestInfo customInfo = new RequestInfo("203.0.113.1", "Safari", "user");
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(customInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("ip", "203.0.113.1");
    }

    @Test
    @DisplayName("upload - should set signedLinkId to null in audit")
    void upload_SignedLinkIdIsNull() {
        doNothing().when(validationUtil).validateFileUploadInputs(any(), any(), any(), any());
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.uploadStatement(any(), any(), any(), any())).thenReturn(testUploadResponse);
        statementUploadService.upload(testMessageDigest, testFile, testAccountNumber, testDate);
        verify(auditService).record(any(), any(), any(), isNull(), any(), any());
    }
}
