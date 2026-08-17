package com.example.statementservice.statement.upload.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.statementservice.infrastructure.web.RequestInfoProvider;
import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.upload.StatementUploadService;
import com.example.statementservice.statement.upload.UploadResponseDto;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController Unit Tests")
class AdminControllerTest {

    @Mock
    private StatementUploadService statementUploadService;

    @Mock
    private UploadResponseApiMapper uploadResponseApiMapper;

    @Mock
    private RequestInfoProvider requestInfoProvider;

    @InjectMocks
    private AdminController adminController;

    private MultipartFile testFile;
    private String testMessageDigest;
    private String testAccountNumber;
    private String testDate;
    private UploadResponseDto testDto;
    private UploadResponse testApiResponse;

    @BeforeEach
    void setUp() {
        byte[] pdfContent = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        testFile = new MockMultipartFile("file", "statement.pdf", "application/pdf", pdfContent);

        testMessageDigest = "a".repeat(64);
        testAccountNumber = "123456789";
        testDate = "2024-01-15";

        testDto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement.pdf")
                .build();

        testApiResponse = new UploadResponse();
        testApiResponse.setStatementId(testDto.getStatementId());
        testApiResponse.setUploadedAt(testDto.getUploadedAt());
        testApiResponse.setFileSize(testDto.getFileSize());
        testApiResponse.setFileName(testDto.getFileName());

        when(requestInfoProvider.get()).thenReturn(new RequestInfo("192.168.1.1", "Mozilla/5.0", "testUser"));
    }

    @Test
    void GivenValidUpload_WhenUploadStatement_ThenReturnsCreatedWithResponse() {

        when(statementUploadService.upload(
                        eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(testDate), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(testDto)).thenReturn(testApiResponse);

        ResponseEntity<UploadResponse> response =
                adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(testDto.getStatementId());
        assertThat(response.getBody().getFileName()).isEqualTo(testDto.getFileName());
        assertThat(response.getBody().getFileSize()).isEqualTo(testDto.getFileSize());

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(testDate), any());
        verify(uploadResponseApiMapper).toApi(testDto);
    }

    @Test
    void GivenUploadRequest_WhenUploadStatement_ThenAllParametersReachService() {

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenReturn(testApiResponse);

        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null);

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(testDate), any());
    }

    @Test
    void GivenServiceThrows_WhenUploadStatement_ThenExceptionPropagates() {

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Service error"));
        assertThatThrownBy(() ->
                        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(testDate), any());
        verifyNoInteractions(uploadResponseApiMapper);
    }

    @Test
    void GivenDifferentFileContents_WhenUploadStatement_ThenEachIsDelegatedToService() {

        MultipartFile largeFile =
                new MockMultipartFile("file", "large-statement.pdf", "application/pdf", new byte[10000]);

        UploadResponseDto largeDto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(10000L)
                .fileName("large-statement.pdf")
                .build();

        UploadResponse largeResponse = new UploadResponse();
        largeResponse.setStatementId(largeDto.getStatementId());
        largeResponse.setFileSize(10000L);

        when(statementUploadService.upload(
                        eq(testMessageDigest), eq(largeFile), eq(testAccountNumber), eq(testDate), any()))
                .thenReturn(largeDto);
        when(uploadResponseApiMapper.toApi(largeDto)).thenReturn(largeResponse);

        ResponseEntity<UploadResponse> response =
                adminController.uploadStatement(testMessageDigest, largeFile, testAccountNumber, testDate, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFileSize()).isEqualTo(10000L);
    }

    @Test
    void GivenBoundaryAccountNumbers_WhenUploadStatement_ThenEachIsDelegatedToService() {

        String minAccountNumber = "123456789"; // 9 digits
        String maxAccountNumber = "123456789012345"; // 15 digits

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenReturn(testApiResponse);

        adminController.uploadStatement(testMessageDigest, testFile, minAccountNumber, testDate, null);
        adminController.uploadStatement(testMessageDigest, testFile, maxAccountNumber, testDate, null);

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(minAccountNumber), eq(testDate), any());
        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(maxAccountNumber), eq(testDate), any());
    }

    @Test
    void GivenPastAndFutureDates_WhenUploadStatement_ThenEachIsDelegatedToService() {

        String pastDate = "2020-01-01";
        String futureDate = "2025-12-31";

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenReturn(testApiResponse);

        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, pastDate, null);
        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, futureDate, null);

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(pastDate), any());
        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(futureDate), any());
    }

    @Test
    void GivenSuccessfulUpload_WhenUploadStatement_ThenMapperReceivesServiceDto() {

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(testDto)).thenReturn(testApiResponse);

        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null);

        verify(uploadResponseApiMapper).toApi(eq(testDto));
    }

    @Test
    void GivenSuccessfulUpload_WhenUploadStatement_ThenResponseFieldsArePopulated() {

        UUID expectedId = UUID.randomUUID();
        OffsetDateTime expectedTime = OffsetDateTime.now();
        String expectedFileName = "test-statement.pdf";
        Long expectedSize = 2048L;

        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(expectedId)
                .uploadedAt(expectedTime)
                .fileSize(expectedSize)
                .fileName(expectedFileName)
                .build();

        UploadResponse apiResponse = new UploadResponse();
        apiResponse.setStatementId(expectedId);
        apiResponse.setUploadedAt(expectedTime);
        apiResponse.setFileSize(expectedSize);
        apiResponse.setFileName(expectedFileName);

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(dto);
        when(uploadResponseApiMapper.toApi(dto)).thenReturn(apiResponse);

        ResponseEntity<UploadResponse> response =
                adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(expectedId);
        assertThat(response.getBody().getUploadedAt()).isEqualTo(expectedTime);
        assertThat(response.getBody().getFileSize()).isEqualTo(expectedSize);
        assertThat(response.getBody().getFileName()).isEqualTo(expectedFileName);
    }

    @Test
    void GivenMapperThrows_WhenUploadStatement_ThenExceptionPropagates() {

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenThrow(new RuntimeException("Mapper error"));

        assertThatThrownBy(() ->
                        adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Mapper error");

        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(testFile), eq(testAccountNumber), eq(testDate), any());
        verify(uploadResponseApiMapper).toApi(testDto);
    }

    @Test
    void GivenFileWithoutName_WhenUploadStatement_ThenUploadStillSucceeds() {

        MultipartFile fileWithoutName = new MockMultipartFile("file", "", "application/pdf", new byte[100]);

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenReturn(testApiResponse);

        ResponseEntity<UploadResponse> response =
                adminController.uploadStatement(testMessageDigest, fileWithoutName, testAccountNumber, testDate, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(statementUploadService)
                .upload(eq(testMessageDigest), eq(fileWithoutName), eq(testAccountNumber), eq(testDate), any());
    }

    @Test
    void GivenRepeatedSuccessfulUploads_WhenUploadStatement_ThenStatusIsAlwaysCreated() {

        when(statementUploadService.upload(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(testDto);
        when(uploadResponseApiMapper.toApi(any())).thenReturn(testApiResponse);

        ResponseEntity<UploadResponse> response1 =
                adminController.uploadStatement(testMessageDigest, testFile, testAccountNumber, testDate, null);
        ResponseEntity<UploadResponse> response2 =
                adminController.uploadStatement(testMessageDigest, testFile, "987654321", "2024-02-15", null);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
