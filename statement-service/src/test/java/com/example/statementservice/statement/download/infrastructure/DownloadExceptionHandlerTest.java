package com.example.statementservice.statement.download.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadFileMissingException;
import com.example.statementservice.statement.download.DownloadInvalidSignatureException;
import com.example.statementservice.statement.download.DownloadLinkExpiredException;
import java.util.stream.Stream;
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
                        "DECRYPTION_FAILED"));
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
}
