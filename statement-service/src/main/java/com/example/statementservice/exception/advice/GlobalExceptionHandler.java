package com.example.statementservice.exception.advice;

import static com.example.statementservice.util.CommonUtil.buildProblemDetailTypeURI;

import com.example.statementservice.exception.DecryptionFailedException;
import com.example.statementservice.exception.DigestComputationException;
import com.example.statementservice.exception.DigestMismatchException;
import com.example.statementservice.exception.DownloadFileMissingException;
import com.example.statementservice.exception.DownloadInvalidSignatureException;
import com.example.statementservice.exception.DownloadLinkExpiredException;
import com.example.statementservice.exception.InvalidAccountNumberException;
import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.exception.InvalidMessageDigestException;
import com.example.statementservice.exception.MissingFileException;
import com.example.statementservice.exception.PdfValidationException;
import com.example.statementservice.exception.SignatureException;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.exception.StatementUploadException;
import com.example.statementservice.exception.UnsupportedContentTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Error Code Constants
    private static final String ERROR_CODE_INVALID_INPUT = "INVALID_INPUT";
    private static final String ERROR_CODE_INVALID_MESSAGE_DIGEST = "INVALID_MESSAGE_DIGEST";
    private static final String ERROR_CODE_MISSING_FILE = "MISSING_FILE";
    private static final String ERROR_CODE_INVALID_ACCOUNT_NUMBER = "INVALID_ACCOUNT_NUMBER";
    private static final String ERROR_CODE_INVALID_DATE = "INVALID_DATE";
    private static final String ERROR_CODE_DIGEST_MISMATCH = "DIGEST_MISMATCH";
    private static final String ERROR_CODE_DIGEST_ERROR = "DIGEST_ERROR";
    private static final String ERROR_CODE_UPLOAD_FAILED = "STATEMENT_UPLOAD_FAILED";
    private static final String ERROR_CODE_UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA_TYPE";
    private static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String ERROR_CODE_STATEMENT_NOT_FOUND = "STATEMENT_NOT_FOUND";
    private static final String ERROR_CODE_INVALID_SIGNATURE = "INVALID_SIGNATURE";
    private static final String ERROR_CODE_LINK_EXPIRED = "LINK_EXPIRED_OR_USED";
    private static final String ERROR_CODE_FILE_MISSING = "FILE_MISSING";
    private static final String ERROR_CODE_DECRYPTION_FAILED = "DECRYPTION_FAILED";

    // Error Type URIs
    private static final String TYPE_PREFIX = "/errors/";
    private static final String TYPE_VALIDATION = TYPE_PREFIX + "validation";
    private static final String TYPE_UPLOAD = TYPE_PREFIX + "upload";
    private static final String TYPE_MEDIA_TYPE = TYPE_PREFIX + "media-type";
    private static final String TYPE_INTERNAL = TYPE_PREFIX + "internal";
    private static final String TYPE_STATEMENT = TYPE_PREFIX + "statement";
    private static final String TYPE_DOWNLOAD = TYPE_PREFIX + "download";

    // Default Messages
    private static final String DEFAULT_BAD_REQUEST_MSG = "Bad request";
    private static final String DEFAULT_INTERNAL_ERROR_MSG = "Internal server error";

    // Title Descriptions
    public static final String TITLE_DESCRIPTION_NOT_FOUND = "Not Found";
    public static final String TITLE_DESCRIPTION_VALIDATION_FAILED = "Validation Failed";
    public static final String TITLE_DESCRIPTION_INVALID_MESSAGE_DIGEST = "Invalid Message Digest";
    public static final String TITLE_DESCRIPTION_MISSING_FILE = "Missing File";
    public static final String TITLE_DESCRIPTION_INVALID_ACCOUNT_NUMBER = "Invalid Account Number";
    public static final String TITLE_DESCRIPTION_INVALID_DATE_FORMAT = "Invalid Date Format";
    public static final String TITLE_DESCRIPTION_DIGEST_MISMATCH = "Digest Mismatch";
    public static final String TITLE_DESCRIPTION_DIGEST_COMPUTATION_FAILED = "Digest Computation Failed";
    public static final String TITLE_DESCRIPTION_STATEMENT_UPLOAD_FAILED = "Statement Upload Failed";
    public static final String TITLE_DESCRIPTION_INVALID_SIGNATURE = "Invalid Signature";
    public static final String TITLE_DESCRIPTION_LINK_EXPIRED_OR_USED = "Link Expired or Used";
    public static final String TITLE_DESCRIPTION_FILE_MISSING = "File Missing";
    public static final String TITLE_DESCRIPTION_DECRYPTION_FAILED = "Decryption Failed";
    public static final String TITLE_DESCRIPTION_UNSUPPORTED_MEDIA_TYPE = "Unsupported Media Type";

    // Metadata maps for consolidated exception handlers
    private static final Map<Class<? extends Exception>, ExceptionMetadata> VALIDATION_EXCEPTION_METADATA = Map.of(
            InvalidMessageDigestException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_MESSAGE_DIGEST, ERROR_CODE_INVALID_MESSAGE_DIGEST),
            MissingFileException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_MISSING_FILE, ERROR_CODE_MISSING_FILE),
            InvalidAccountNumberException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_ACCOUNT_NUMBER, ERROR_CODE_INVALID_ACCOUNT_NUMBER),
            InvalidDateException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_DATE_FORMAT, ERROR_CODE_INVALID_DATE),
            DigestMismatchException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_DIGEST_MISMATCH, ERROR_CODE_DIGEST_MISMATCH),
            DigestComputationException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_DIGEST_COMPUTATION_FAILED, ERROR_CODE_DIGEST_ERROR));

    private static final Map<Class<? extends Exception>, ExceptionMetadata> DOWNLOAD_EXCEPTION_METADATA = Map.of(
            DownloadInvalidSignatureException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_INVALID_SIGNATURE, ERROR_CODE_INVALID_SIGNATURE, HttpStatus.FORBIDDEN),
            DownloadLinkExpiredException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_LINK_EXPIRED_OR_USED, ERROR_CODE_LINK_EXPIRED, HttpStatus.NOT_FOUND),
            DownloadFileMissingException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_FILE_MISSING, ERROR_CODE_FILE_MISSING, HttpStatus.NOT_FOUND),
            DecryptionFailedException.class,
            new ExceptionMetadata(
                    TITLE_DESCRIPTION_DECRYPTION_FAILED,
                    ERROR_CODE_DECRYPTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR));

    /**
     * Helper record to store exception metadata
     */
    private record ExceptionMetadata(String title, String errorCode, HttpStatus status) {
        // Constructor for validation exceptions (all use BAD_REQUEST)
        ExceptionMetadata(String title, String errorCode) {
            this(title, errorCode, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Creates a ProblemDetail response with consistent structure
     */
    private ProblemDetail createProblemDetail(
            HttpStatus status, URI type, String title, String detail, String errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : title);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setProperty("errorCode", errorCode);
        return problemDetail;
    }

    /**
     * Handles statement not found exceptions
     */
    @ExceptionHandler(StatementNotFoundException.class)
    public ProblemDetail handleStatementNotFound(StatementNotFoundException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                buildProblemDetailTypeURI(request, TYPE_STATEMENT),
                TITLE_DESCRIPTION_NOT_FOUND,
                ex.getMessage(),
                ERROR_CODE_STATEMENT_NOT_FOUND);
    }

    /**
     * Handles Spring/Jakarta validation exceptions
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        PdfValidationException.class
    })
    public ProblemDetail handleValidationExceptions(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                TITLE_DESCRIPTION_VALIDATION_FAILED,
                ex.getMessage(),
                ERROR_CODE_INVALID_INPUT);
    }

    /**
     * Handles custom input validation exceptions (consolidated)
     * All return BAD_REQUEST with unique error codes and titles
     */
    @ExceptionHandler({
        InvalidMessageDigestException.class,
        MissingFileException.class,
        InvalidAccountNumberException.class,
        InvalidDateException.class,
        DigestMismatchException.class,
        DigestComputationException.class
    })
    public ProblemDetail handleInputValidationExceptions(Exception ex, HttpServletRequest request) {
        ExceptionMetadata metadata = VALIDATION_EXCEPTION_METADATA.get(ex.getClass());

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                metadata.title(),
                ex.getMessage(),
                metadata.errorCode());
    }

    /**
     * Handles statement upload failures
     */
    @ExceptionHandler(StatementUploadException.class)
    public ProblemDetail handleUploadFailure(StatementUploadException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_UPLOAD),
                TITLE_DESCRIPTION_STATEMENT_UPLOAD_FAILED,
                ex.getMessage(),
                ERROR_CODE_UPLOAD_FAILED);
    }

    /**
     * Handles download-related exceptions (consolidated)
     * Returns different HTTP status codes based on exception type
     */
    @ExceptionHandler({
        DownloadInvalidSignatureException.class,
        DownloadLinkExpiredException.class,
        DownloadFileMissingException.class,
        DecryptionFailedException.class
    })
    public ProblemDetail handleDownloadExceptions(Exception ex, HttpServletRequest request) {
        ExceptionMetadata metadata = DOWNLOAD_EXCEPTION_METADATA.get(ex.getClass());

        return createProblemDetail(
                metadata.status(),
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                metadata.title(),
                ex.getMessage(),
                metadata.errorCode());
    }

    /**
     * Handles unsupported media type exceptions
     */
    @ExceptionHandler({UnsupportedContentTypeException.class, HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                buildProblemDetailTypeURI(request, TYPE_MEDIA_TYPE),
                TITLE_DESCRIPTION_UNSUPPORTED_MEDIA_TYPE,
                ex.getMessage(),
                ERROR_CODE_UNSUPPORTED_MEDIA);
    }

    /**
     * Handles generic runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                DEFAULT_BAD_REQUEST_MSG,
                ex.getMessage(),
                ERROR_CODE_INVALID_INPUT);
    }

    /**
     * Handles all other unhandled exceptions
     */
    @ExceptionHandler({Exception.class, SignatureException.class})
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_INTERNAL),
                DEFAULT_INTERNAL_ERROR_MSG,
                ex.getMessage(),
                ERROR_CODE_INTERNAL_ERROR);
    }
}
