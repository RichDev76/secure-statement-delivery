package com.example.statementservice.statement.download.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadOutcome;
import com.example.statementservice.statement.download.DownloadService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
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
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        var response = downloadResponseFactory.build(fileName, result);
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

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.RATE_LIMITED, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.download.DownloadRateLimitedException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenInvalidSignatureOutcome_WhenBuildingResponse_ThenThrowsDownloadInvalidSignatureException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.INVALID_SIGNATURE, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.download.DownloadInvalidSignatureException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenLinkExpiredOutcome_WhenBuildingResponse_ThenThrowsDownloadLinkExpiredException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.LINK_EXPIRED, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.download.DownloadLinkExpiredException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenStatementNotFoundOutcome_WhenBuildingResponse_ThenThrowsStatementNotFoundException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.StatementNotFoundException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenFileMissingOutcome_WhenBuildingResponse_ThenThrowsDownloadFileMissingException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.download.DownloadFileMissingException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenDecryptionFailedOutcome_WhenBuildingResponse_ThenThrowsDecryptionFailedException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());
        assertThrows(DecryptionFailedException.class, () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenStorageUnavailableOutcome_WhenBuildingResponse_ThenThrowsDownloadStorageUnavailableException() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STORAGE_UNAVAILABLE, Optional.empty());
        assertThrows(
                com.example.statementservice.statement.download.DownloadStorageUnavailableException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    void GivenDifferentFileNames_WhenBuildingOkResponse_ThenEachFileNameAppearsInHeaders() {

        var customFileName = "annual-report-2024.pdf";
        var inputStream = new ByteArrayInputStream("test".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(customFileName, result);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains(customFileName));
    }

    @Test
    void GivenSpecialCharacterFileName_WhenBuildingOkResponse_ThenFileNameAppearsInHeaders() {

        var specialFileName = "statement-2023-01 (copy).pdf";
        var inputStream = new ByteArrayInputStream("data".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(specialFileName, result);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders().getContentDisposition());
    }

    @Test
    void GivenOkOutcome_WhenBuildingResponse_ThenAllSecurityHeadersAreSet() {

        var inputStream = new ByteArrayInputStream("secure content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);
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
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(emptyInputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);
    }

    @Test
    void GivenOkOutcome_WhenBuildingResponse_ThenContentTypeIsPdf() {

        var inputStream = new ByteArrayInputStream("content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);
        assertNotNull(response);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }
}
