package com.example.statementservice.statement.download.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadOutcome;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadResponseFactory Tests")
class DownloadResponseFactoryTest {

    @InjectMocks
    private DownloadResponseFactory downloadResponseFactory;

    private String fileName;

    @BeforeEach
    void setUp() {
        fileName = "statement-2023-01.pdf";
    }

    @Test
    void GivenOkOutcome_WhenBuildingResponse_ThenReturnsOkWithHeadersAndBody() {

        var testData = "PDF content".getBytes();
        var inputStream = new ByteArrayInputStream(testData);
        var response = downloadResponseFactory.build(fileName, DownloadOutcome.OK, inputStream);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);
        var headers = response.getHeaders();
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.getContentType());
        assertNotNull(headers.getContentDisposition());
        assertTrue(headers.getContentDisposition().toString().contains(fileName));
        assertTrue(headers.getContentDisposition().toString().contains("attachment"));
        assertEquals("no-store, no-cache, must-revalidate", headers.getCacheControl());
        assertEquals("no-cache", headers.getFirst("Pragma"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
    }

    @Test
    void GivenRateLimitedOutcome_WhenBuildingResponse_ThenThrowsDownloadRateLimitedException() {

        assertThrows(
                com.example.statementservice.statement.download.DownloadRateLimitedException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.RATE_LIMITED, null));
    }

    @Test
    void GivenInvalidSignatureOutcome_WhenBuildingResponse_ThenThrowsDownloadInvalidSignatureException() {

        assertThrows(
                com.example.statementservice.statement.download.DownloadInvalidSignatureException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.INVALID_SIGNATURE, null));
    }

    @Test
    void GivenLinkExpiredOutcome_WhenBuildingResponse_ThenThrowsDownloadLinkExpiredException() {

        assertThrows(
                com.example.statementservice.statement.download.DownloadLinkExpiredException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.LINK_EXPIRED, null));
    }

    @Test
    void GivenStatementNotFoundOutcome_WhenBuildingResponse_ThenThrowsStatementNotFoundException() {

        assertThrows(
                com.example.statementservice.statement.StatementNotFoundException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.STATEMENT_NOT_FOUND, null));
    }

    @Test
    void GivenFileMissingOutcome_WhenBuildingResponse_ThenThrowsDownloadFileMissingException() {

        assertThrows(
                com.example.statementservice.statement.download.DownloadFileMissingException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.FILE_MISSING, null));
    }

    @Test
    void GivenDecryptionFailedOutcome_WhenBuildingResponse_ThenThrowsDecryptionFailedException() {

        assertThrows(
                DecryptionFailedException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.DECRYPTION_FAILED, null));
    }

    @Test
    void GivenStorageUnavailableOutcome_WhenBuildingResponse_ThenThrowsDownloadStorageUnavailableException() {

        assertThrows(
                com.example.statementservice.statement.download.DownloadStorageUnavailableException.class,
                () -> downloadResponseFactory.build(fileName, DownloadOutcome.STORAGE_UNAVAILABLE, null));
    }

    @Test
    void GivenDifferentFileNames_WhenBuildingOkResponse_ThenEachFileNameAppearsInHeaders() {

        var customFileName = "annual-report-2024.pdf";
        var inputStream = new ByteArrayInputStream("test".getBytes());
        ResponseEntity<Resource> response =
                downloadResponseFactory.build(customFileName, DownloadOutcome.OK, inputStream);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains(customFileName));
    }

    @Test
    void GivenSpecialCharacterFileName_WhenBuildingOkResponse_ThenFileNameAppearsInHeaders() {

        var specialFileName = "statement-2023-01 (copy).pdf";
        var inputStream = new ByteArrayInputStream("data".getBytes());
        ResponseEntity<Resource> response =
                downloadResponseFactory.build(specialFileName, DownloadOutcome.OK, inputStream);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders().getContentDisposition());
    }

    @Test
    void GivenOkOutcome_WhenBuildingResponse_ThenAllSecurityHeadersAreSet() {

        var inputStream = new ByteArrayInputStream("secure content".getBytes());
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, DownloadOutcome.OK, inputStream);
        assertNotNull(response);
        var headers = response.getHeaders();
        assertEquals("no-store, no-cache, must-revalidate", headers.getCacheControl());
        assertEquals("no-cache", headers.getFirst("Pragma"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        var contentDisposition = headers.getContentDisposition().toString();
        assertTrue(contentDisposition.contains("attachment"));
        assertTrue(contentDisposition.contains(fileName));
    }

    @Test
    void GivenEmptyInputStream_WhenBuildingOkResponse_ThenReturnsOkWithEmptyBody() {

        var emptyInputStream = new ByteArrayInputStream(new byte[0]);
        ResponseEntity<Resource> response =
                downloadResponseFactory.build(fileName, DownloadOutcome.OK, emptyInputStream);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);
    }

    @Test
    void GivenOkOutcome_WhenBuildingResponse_ThenContentTypeIsPdf() {

        var inputStream = new ByteArrayInputStream("content".getBytes());
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, DownloadOutcome.OK, inputStream);
        assertNotNull(response);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }

    @Test
    void GivenOkOutcomeWithNullStream_WhenBuildingResponse_ThenThrowsNullPointerException() {

        assertThrows(
                NullPointerException.class, () -> downloadResponseFactory.build(fileName, DownloadOutcome.OK, null));
    }
}
