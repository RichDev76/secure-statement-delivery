package com.example.statementservice.infrastructure.web;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.CommonUtil.createProblemDetail;

import com.example.statementservice.statement.signedlink.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Only genuinely cross-cutting concerns live here: framework Bean Validation exceptions and the
// safe catch-all (handleGeneric). Feature-specific exceptions are handled by each feature's own
// @Order(HIGHEST_PRECEDENCE) advice bean (StatementExceptionHandler, UploadExceptionHandler,
// DownloadExceptionHandler); this class runs last so it only fires when nothing more specific matched.
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_CODE_INVALID_INPUT = "INVALID_INPUT";
    private static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String ERROR_CODE_INVALID_REQUEST = "INVALID_REQUEST";

    private static final String TYPE_PREFIX = "/errors/";
    private static final String TYPE_VALIDATION = TYPE_PREFIX + "validation";
    private static final String TYPE_INTERNAL = TYPE_PREFIX + "internal";
    private static final String TYPE_REQUEST = TYPE_PREFIX + "request";

    private static final String DEFAULT_INTERNAL_ERROR_MSG = "Internal server error";

    public static final String TITLE_DESCRIPTION_VALIDATION_FAILED = "Validation Failed";

    // @PreAuthorize denials surface inside MVC; without this the Exception catch-all would answer 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(HttpServletRequest request) {
        log.warn("Access denied - path={}, method={}", EndpointLabel.of(request.getRequestURI()), request.getMethod());
        var problemDetail = SecurityProblemDetailFactory.accessDenied(request);
        return ResponseEntity.status(problemDetail.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class
    })
    public ProblemDetail handleValidationExceptions(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                TITLE_DESCRIPTION_VALIDATION_FAILED,
                ex.getMessage(),
                ERROR_CODE_INVALID_INPUT);
    }

    // Safe catch-all for every exception not handled more specifically. A genuine ErrorResponse
    // exposes its message only when the status is 4xx (a framework-classified client error is
    // safe to echo); anything else, including a 5xx ErrorResponse, is logged server-side and
    // returns a fixed generic detail. Every unhandled exception funnels through this one point,
    // so it must never leak ex.getMessage() for an unclassified failure.
    @ExceptionHandler({Exception.class, SignatureException.class})
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            boolean clientError = status.is4xxClientError();
            if (!clientError) {
                log.error("Unclassified 5xx ErrorResponse reached GlobalExceptionHandler", ex);
            }
            var problemDetail = createProblemDetail(
                    HttpStatus.valueOf(status.value()),
                    buildProblemDetailTypeURI(request, clientError ? TYPE_REQUEST : TYPE_INTERNAL),
                    errorResponse.getBody().getTitle(),
                    clientError ? ex.getMessage() : null,
                    clientError ? ERROR_CODE_INVALID_REQUEST : ERROR_CODE_INTERNAL_ERROR);
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problemDetail);
        }

        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        var problemDetail = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_INTERNAL),
                DEFAULT_INTERNAL_ERROR_MSG,
                null,
                ERROR_CODE_INTERNAL_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
