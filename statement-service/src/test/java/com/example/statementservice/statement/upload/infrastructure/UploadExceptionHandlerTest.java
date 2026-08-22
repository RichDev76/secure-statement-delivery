package com.example.statementservice.statement.upload.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.statement.DuplicateStatementException;
import com.example.statementservice.statement.StatementUploadException;
import com.example.statementservice.statement.upload.DigestComputationException;
import com.example.statementservice.statement.upload.DigestMismatchException;
import com.example.statementservice.statement.upload.InvalidAccountNumberException;
import com.example.statementservice.statement.upload.InvalidMessageDigestException;
import com.example.statementservice.statement.upload.MissingFileException;
import com.example.statementservice.statement.upload.PdfValidationException;
import com.example.statementservice.statement.upload.UnsupportedContentTypeException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class UploadExceptionHandlerTest {

    private final UploadExceptionHandler handler = new UploadExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/statements/upload");

    private static Stream<Arguments> validationExceptions() {
        return Stream.of(
                Arguments.of(
                        new InvalidMessageDigestException("bad digest header"),
                        "Invalid Message Digest",
                        "INVALID_MESSAGE_DIGEST"),
                Arguments.of(new MissingFileException("file part missing"), "Missing File", "MISSING_FILE"),
                Arguments.of(
                        new InvalidAccountNumberException("bad account number"),
                        "Invalid Account Number",
                        "INVALID_ACCOUNT_NUMBER"),
                Arguments.of(new InvalidDateException("bad date"), "Invalid Date Format", "INVALID_DATE"),
                Arguments.of(new DigestMismatchException("digest mismatch"), "Digest Mismatch", "DIGEST_MISMATCH"),
                Arguments.of(
                        new DigestComputationException("digest failed"), "Digest Computation Failed", "DIGEST_ERROR"),
                Arguments.of(
                        new PdfValidationException("not a pdf"), "PDF Validation Failed", "PDF_VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @MethodSource("validationExceptions")
    void
            GivenUploadValidationException_WhenHandleInputValidationExceptions_ThenReturnsBadRequestWithOwnTitleAndErrorCode(
                    RuntimeException ex, String expectedTitle, String expectedErrorCode) {
        // When
        var response = handler.handleInputValidationExceptions(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getTitle()).isEqualTo(expectedTitle);
        assertThat(response.getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getProperties()).containsEntry("errorCode", expectedErrorCode);
    }

    @Test
    void GivenStatementUploadException_WhenHandleUploadFailure_ThenReturnsInternalServerErrorWithUploadFailedCode() {
        // Given
        var ex = new StatementUploadException("Failed to create storage directory");

        // When
        var response = handler.handleUploadFailure(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.getTitle()).isEqualTo("Statement Upload Failed");
        assertThat(response.getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getProperties()).containsEntry("errorCode", "STATEMENT_UPLOAD_FAILED");
    }

    @ParameterizedTest
    @MethodSource("unsupportedMediaTypeExceptions")
    void GivenUnsupportedMediaTypeException_WhenHandleUnsupportedMediaType_ThenReturns415WithUnsupportedMediaCode(
            Exception ex) {
        // When
        var response = handler.handleUnsupportedMediaType(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(response.getTitle()).isEqualTo("Unsupported Media Type");
        assertThat(response.getProperties()).containsEntry("errorCode", "UNSUPPORTED_MEDIA_TYPE");
    }

    private static Stream<Exception> unsupportedMediaTypeExceptions() {
        return Stream.of(
                new UnsupportedContentTypeException("only application/pdf is accepted"),
                new HttpMediaTypeNotSupportedException("unsupported"));
    }

    @Test
    void GivenMaxUploadSizeExceededException_WhenHandled_ThenReturns413WithUploadTooLargeErrorCode() {
        // Given
        var ex = new MaxUploadSizeExceededException(10_485_760L);

        // When
        var response = handler.handleMaxUploadSizeExceeded(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(response.getTitle()).isEqualTo("Upload Too Large");
        assertThat(response.getProperties()).containsEntry("errorCode", "UPLOAD_TOO_LARGE");
    }

    @Test
    void GivenMaxUploadSizeExceededException_WhenHandled_ThenDetailHidesContainerInternals() {
        // Given: the raw exception message embeds the configured byte limit
        var ex = new MaxUploadSizeExceededException(10_485_760L);

        // When
        var response = handler.handleMaxUploadSizeExceeded(ex, request);

        // Then
        assertThat(response.getDetail())
                .isEqualTo("Uploaded file exceeds the maximum allowed size")
                .doesNotContain("10485760");
    }

    @Test
    void GivenDuplicateStatementException_WhenHandled_ThenReturns409WithStatementAlreadyExistsCode() {
        // Given
        var ex = new DuplicateStatementException(
                "A statement already exists for this account number and statement date");

        // When
        var response = handler.handleDuplicateStatement(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getTitle()).isEqualTo("Statement Already Exists");
        assertThat(response.getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getProperties()).containsEntry("errorCode", "STATEMENT_ALREADY_EXISTS");
    }
}
