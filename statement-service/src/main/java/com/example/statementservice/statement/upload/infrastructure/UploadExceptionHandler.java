package com.example.statementservice.statement.upload.infrastructure;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.CommonUtil.createProblemDetail;

import com.example.statementservice.infrastructure.web.ExceptionMetadata;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.DuplicateStatementException;
import com.example.statementservice.statement.upload.DigestComputationException;
import com.example.statementservice.statement.upload.DigestMismatchException;
import com.example.statementservice.statement.upload.InvalidAccountNumberException;
import com.example.statementservice.statement.upload.InvalidMessageDigestException;
import com.example.statementservice.statement.upload.MissingFileException;
import com.example.statementservice.statement.upload.PdfValidationException;
import com.example.statementservice.statement.upload.UnsupportedContentTypeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
public class UploadExceptionHandler {

    private static final String TYPE_VALIDATION = "/errors/validation";
    private static final String TYPE_UPLOAD = "/errors/upload";
    private static final String TYPE_MEDIA_TYPE = "/errors/media-type";

    private static final String TITLE_DESCRIPTION_INVALID_MESSAGE_DIGEST = "Invalid Message Digest";
    private static final String TITLE_DESCRIPTION_MISSING_FILE = "Missing File";
    private static final String TITLE_DESCRIPTION_INVALID_ACCOUNT_NUMBER = "Invalid Account Number";
    private static final String TITLE_DESCRIPTION_INVALID_DATE_FORMAT = "Invalid Date Format";
    private static final String TITLE_DESCRIPTION_DIGEST_MISMATCH = "Digest Mismatch";
    private static final String TITLE_DESCRIPTION_DIGEST_COMPUTATION_FAILED = "Digest Computation Failed";
    private static final String TITLE_DESCRIPTION_PDF_VALIDATION_FAILED = "PDF Validation Failed";
    private static final String TITLE_DESCRIPTION_STATEMENT_UPLOAD_FAILED = "Statement Upload Failed";
    private static final String TITLE_DESCRIPTION_STATEMENT_ALREADY_EXISTS = "Statement Already Exists";
    private static final String TITLE_DESCRIPTION_UNSUPPORTED_MEDIA_TYPE = "Unsupported Media Type";
    private static final String TITLE_DESCRIPTION_UPLOAD_TOO_LARGE = "Upload Too Large";
    private static final String DETAIL_UPLOAD_TOO_LARGE = "Uploaded file exceeds the maximum allowed size";

    private static final String ERROR_CODE_INVALID_MESSAGE_DIGEST = "INVALID_MESSAGE_DIGEST";
    private static final String ERROR_CODE_MISSING_FILE = "MISSING_FILE";
    private static final String ERROR_CODE_INVALID_ACCOUNT_NUMBER = "INVALID_ACCOUNT_NUMBER";
    private static final String ERROR_CODE_INVALID_DATE = "INVALID_DATE";
    private static final String ERROR_CODE_DIGEST_MISMATCH = "DIGEST_MISMATCH";
    private static final String ERROR_CODE_DIGEST_ERROR = "DIGEST_ERROR";
    private static final String ERROR_CODE_PDF_VALIDATION_FAILED = "PDF_VALIDATION_FAILED";
    private static final String ERROR_CODE_UPLOAD_FAILED = "STATEMENT_UPLOAD_FAILED";
    private static final String ERROR_CODE_STATEMENT_ALREADY_EXISTS = "STATEMENT_ALREADY_EXISTS";
    private static final String ERROR_CODE_UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA_TYPE";
    private static final String ERROR_CODE_UPLOAD_TOO_LARGE = "UPLOAD_TOO_LARGE";

    private static final Map<Class<? extends Exception>, ExceptionMetadata> VALIDATION_EXCEPTION_METADATA = Map.of(
            InvalidMessageDigestException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_MESSAGE_DIGEST, ERROR_CODE_INVALID_MESSAGE_DIGEST),
            MissingFileException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_MISSING_FILE, ERROR_CODE_MISSING_FILE),
            InvalidAccountNumberException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_ACCOUNT_NUMBER, ERROR_CODE_INVALID_ACCOUNT_NUMBER),
            // InvalidDateException lives in shared/ and is also thrown by the search feature
            // (StatementQueryService.parseDate) - this is its only handler, don't scope it to
            // upload-only without adding an equivalent entry wherever else it's thrown.
            InvalidDateException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_INVALID_DATE_FORMAT, ERROR_CODE_INVALID_DATE),
            DigestMismatchException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_DIGEST_MISMATCH, ERROR_CODE_DIGEST_MISMATCH),
            DigestComputationException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_DIGEST_COMPUTATION_FAILED, ERROR_CODE_DIGEST_ERROR),
            PdfValidationException.class,
            new ExceptionMetadata(TITLE_DESCRIPTION_PDF_VALIDATION_FAILED, ERROR_CODE_PDF_VALIDATION_FAILED));

    @ExceptionHandler({
        InvalidMessageDigestException.class,
        MissingFileException.class,
        InvalidAccountNumberException.class,
        InvalidDateException.class,
        DigestMismatchException.class,
        DigestComputationException.class,
        PdfValidationException.class
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

    @ExceptionHandler(DuplicateStatementException.class)
    public ProblemDetail handleDuplicateStatement(DuplicateStatementException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.CONFLICT,
                buildProblemDetailTypeURI(request, TYPE_UPLOAD),
                TITLE_DESCRIPTION_STATEMENT_ALREADY_EXISTS,
                ex.getMessage(),
                ERROR_CODE_STATEMENT_ALREADY_EXISTS);
    }

    @ExceptionHandler(StatementUploadException.class)
    public ProblemDetail handleUploadFailure(StatementUploadException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_UPLOAD),
                TITLE_DESCRIPTION_STATEMENT_UPLOAD_FAILED,
                ex.getMessage(),
                ERROR_CODE_UPLOAD_FAILED);
    }

    @ExceptionHandler({UnsupportedContentTypeException.class, HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                buildProblemDetailTypeURI(request, TYPE_MEDIA_TYPE),
                TITLE_DESCRIPTION_UNSUPPORTED_MEDIA_TYPE,
                ex.getMessage(),
                ERROR_CODE_UNSUPPORTED_MEDIA);
    }

    // Fixed detail: the exception message embeds container internals that must not reach clients.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.CONTENT_TOO_LARGE,
                buildProblemDetailTypeURI(request, TYPE_UPLOAD),
                TITLE_DESCRIPTION_UPLOAD_TOO_LARGE,
                DETAIL_UPLOAD_TOO_LARGE,
                ERROR_CODE_UPLOAD_TOO_LARGE);
    }
}
