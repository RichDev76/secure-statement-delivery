package com.example.statementservice.statement.download.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadFileMissingException;
import com.example.statementservice.statement.download.DownloadInvalidSignatureException;
import com.example.statementservice.statement.download.DownloadLinkExpiredException;
import com.example.statementservice.statement.download.DownloadRateLimitedException;
import com.example.statementservice.statement.download.DownloadStorageUnavailableException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class DownloadExceptionHandlerTest {

    private final DownloadExceptionHandler handler = new DownloadExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/statements/download/statement.pdf.enc");

    private static Stream<Arguments> downloadExceptions() {
        return Stream.of(
                Arguments.of(
                        new DownloadInvalidSignatureException("signature invalid"),
                        HttpStatus.FORBIDDEN,
                        "Invalid Signature",
                        "INVALID_SIGNATURE"),
                Arguments.of(
                        new DownloadLinkExpiredException("link expired"),
                        HttpStatus.NOT_FOUND,
                        "Link Expired",
                        "LINK_EXPIRED"),
                Arguments.of(
                        new DownloadFileMissingException("file missing"),
                        HttpStatus.NOT_FOUND,
                        "File Missing",
                        "FILE_MISSING"),
                Arguments.of(
                        new DecryptionFailedException("decryption failed"),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Decryption Failed",
                        "DECRYPTION_FAILED"),
                Arguments.of(
                        new DownloadRateLimitedException("too many requests"),
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        "RATE_LIMITED"),
                Arguments.of(
                        new DownloadStorageUnavailableException("storage unavailable"),
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Storage Unavailable",
                        "STORAGE_UNAVAILABLE"));
    }

    @ParameterizedTest
    @MethodSource("downloadExceptions")
    void GivenDownloadException_WhenHandleDownloadExceptions_ThenReturnsOwnStatusTitleAndErrorCodePinnedToProblemJson(
            RuntimeException ex, HttpStatus expectedStatus, String expectedTitle, String expectedErrorCode) {
        // When
        var response = handler.handleDownloadExceptions(ex, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo(expectedTitle);
        assertThat(response.getBody().getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", expectedErrorCode);
    }

    @Test
    void GivenRateLimitedException_WhenHandleDownloadExceptions_ThenResponseIncludesRetryAfterHeader() {
        // When
        var response = handler.handleDownloadExceptions(new DownloadRateLimitedException("too many requests"), request);

        // Then
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void GivenNonRateLimitedException_WhenHandleDownloadExceptions_ThenNoRetryAfterHeaderIsSet() {
        // When
        var response = handler.handleDownloadExceptions(new DownloadLinkExpiredException("expired"), request);

        // Then
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    void GivenStorageUnavailableException_WhenHandleDownloadExceptions_ThenResponseIncludesRetryAfterHeader() {
        // When
        var response = handler.handleDownloadExceptions(
                new DownloadStorageUnavailableException("storage unavailable"), request);

        // Then
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
    }
}
