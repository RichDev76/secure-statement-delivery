package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.exception.DecryptionFailedException;
import com.example.statementservice.model.DownloadOutcome;
import com.example.statementservice.service.DownloadService;
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
    @DisplayName("Should build OK response with proper headers and body")
    void testBuild_OkOutcome() {

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
    }

    @Test
    @DisplayName("Should throw DownloadInvalidSignatureException for INVALID_SIGNATURE outcome")
    void testBuild_InvalidSignatureOutcome() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.INVALID_SIGNATURE, Optional.empty());
        assertThrows(
                com.example.statementservice.exception.DownloadInvalidSignatureException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    @DisplayName("Should throw DownloadLinkExpiredException for LINK_EXPIRED_OR_USED outcome")
    void testBuild_LinkExpiredOrUsedOutcome() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.LINK_EXPIRED_OR_USED, Optional.empty());
        assertThrows(
                com.example.statementservice.exception.DownloadLinkExpiredException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    @DisplayName("Should throw StatementNotFoundException for STATEMENT_NOT_FOUND outcome")
    void testBuild_StatementNotFoundOutcome() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        assertThrows(
                com.example.statementservice.exception.StatementNotFoundException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    @DisplayName("Should throw DownloadFileMissingException for FILE_MISSING outcome")
    void testBuild_FileMissingOutcome() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
        assertThrows(
                com.example.statementservice.exception.DownloadFileMissingException.class,
                () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    @DisplayName("Should throw DecryptionFailedException for DECRYPTION_FAILED outcome")
    void testBuild_DecryptionFailedOutcome() {

        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());
        assertThrows(DecryptionFailedException.class, () -> downloadResponseFactory.build(fileName, result));
    }

    @Test
    @DisplayName("Should handle OK outcome with different file names")
    void testBuild_OkOutcome_DifferentFileNames() {

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
    @DisplayName("Should handle OK outcome with file name containing special characters")
    void testBuild_OkOutcome_SpecialCharactersInFileName() {

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
    @DisplayName("Should verify all required security headers are set for OK outcome")
    void testBuild_OkOutcome_SecurityHeaders() {

        var inputStream = new ByteArrayInputStream("secure content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);
        assertNotNull(response);
        var headers = response.getHeaders();
        assertEquals("no-store, no-cache, must-revalidate", headers.getCacheControl());
        assertEquals("no-cache", headers.getFirst("Pragma"));
        var contentDisposition = headers.getContentDisposition().toString();
        assertTrue(contentDisposition.contains("attachment"));
        assertTrue(contentDisposition.contains(fileName));
    }

    @Test
    @DisplayName("Should handle empty input stream for OK outcome")
    void testBuild_OkOutcome_EmptyInputStream() {

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
    @DisplayName("Should set correct content type for OK outcome")
    void testBuild_OkOutcome_ContentType() {

        var inputStream = new ByteArrayInputStream("content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);
        assertNotNull(response);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }
}
