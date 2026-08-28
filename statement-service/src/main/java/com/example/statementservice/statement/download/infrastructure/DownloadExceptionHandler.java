package com.example.statementservice.statement.download.infrastructure;

import static com.example.statementservice.infrastructure.web.ProblemDetailSupport.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.ProblemDetailSupport.createProblemDetail;

import com.example.statementservice.infrastructure.web.ExceptionMetadata;
import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadFileMissingException;
import com.example.statementservice.statement.download.DownloadInvalidSignatureException;
import com.example.statementservice.statement.download.DownloadLinkExpiredException;
import com.example.statementservice.statement.download.DownloadRateLimitedException;
import com.example.statementservice.statement.download.DownloadStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RestControllerAdvice
public class DownloadExceptionHandler {

    private static final String TYPE_DOWNLOAD = "/errors/download";
    private static final String HTTP_HEADER_RETRY_AFTER = "Retry-After";
    // Matches the rate limiter's own bandwidth refill window (SignedLinkProperties.rateLimitPerMinute).
    private static final String RETRY_AFTER_SECONDS = "60";
    // Fixed conservative hint, unlike RETRY_AFTER_SECONDS above: no recovery-window signal exists
    // yet to derive this from.
    private static final String STORAGE_RETRY_AFTER_SECONDS = "30";

    private static final String TITLE_DESCRIPTION_INVALID_SIGNATURE = "Invalid Signature";
    private static final String TITLE_DESCRIPTION_LINK_EXPIRED = "Link Expired";
    private static final String TITLE_DESCRIPTION_FILE_MISSING = "File Missing";
    private static final String TITLE_DESCRIPTION_DECRYPTION_FAILED = "Decryption Failed";
    private static final String TITLE_DESCRIPTION_RATE_LIMITED = "Too Many Requests";
    private static final String TITLE_DESCRIPTION_STORAGE_UNAVAILABLE = "Storage Unavailable";

    private static final String ERROR_CODE_INVALID_SIGNATURE = "INVALID_SIGNATURE";
    private static final String ERROR_CODE_LINK_EXPIRED = "LINK_EXPIRED";
    private static final String ERROR_CODE_FILE_MISSING = "FILE_MISSING";
    private static final String ERROR_CODE_DECRYPTION_FAILED = "DECRYPTION_FAILED";
    private static final String ERROR_CODE_RATE_LIMITED = "RATE_LIMITED";
    private static final String ERROR_CODE_STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";

    private static final Map<Class<? extends Exception>, ExceptionMetadata> DOWNLOAD_EXCEPTION_METADATA = Map.of(
            DownloadInvalidSignatureException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_INVALID_SIGNATURE, ERROR_CODE_INVALID_SIGNATURE, HttpStatus.FORBIDDEN),
            DownloadLinkExpiredException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_LINK_EXPIRED, ERROR_CODE_LINK_EXPIRED, HttpStatus.NOT_FOUND),
            DownloadFileMissingException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_FILE_MISSING, ERROR_CODE_FILE_MISSING, HttpStatus.NOT_FOUND),
            DecryptionFailedException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_DECRYPTION_FAILED,
                    ERROR_CODE_DECRYPTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR),
            DownloadRateLimitedException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_RATE_LIMITED, ERROR_CODE_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            DownloadStorageUnavailableException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_STORAGE_UNAVAILABLE,
                    ERROR_CODE_STORAGE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE));

    // ResponseEntity with a pinned content type, not a bare ProblemDetail: on the octet-stream-only
    // download endpoint, ExceptionHandlerExceptionResolver would otherwise negotiate a producible
    // media type from the originally matched handler method, not this one.
    @ExceptionHandler({
        DownloadInvalidSignatureException.class,
        DownloadLinkExpiredException.class,
        DownloadFileMissingException.class,
        DecryptionFailedException.class,
        DownloadRateLimitedException.class,
        DownloadStorageUnavailableException.class
    })
    public ResponseEntity<ProblemDetail> handleDownloadExceptions(Exception ex, HttpServletRequest request) {
        ExceptionMetadata metadata = DOWNLOAD_EXCEPTION_METADATA.get(ex.getClass());

        var problemDetail = createProblemDetail(
                metadata.status(),
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                metadata.title(),
                ex.getMessage(),
                metadata.errorCode());
        var responseBuilder = ResponseEntity.status(metadata.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex instanceof DownloadRateLimitedException) {
            responseBuilder.header(HTTP_HEADER_RETRY_AFTER, RETRY_AFTER_SECONDS);
        } else if (ex instanceof DownloadStorageUnavailableException) {
            responseBuilder.header(HTTP_HEADER_RETRY_AFTER, STORAGE_RETRY_AFTER_SECONDS);
        }
        return responseBuilder.body(problemDetail);
    }
}
