package com.example.statementservice.statement.infrastructure;

import static com.example.statementservice.infrastructure.web.ProblemDetailSupport.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.ProblemDetailSupport.createProblemDetail;

import com.example.statementservice.statement.StatementNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Own precedence (not shared HIGHEST_PRECEDENCE) so a future handler collision resolves deterministically.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class StatementExceptionHandler {

    private static final String TYPE_STATEMENT = "/errors/statement";
    private static final String TITLE_DESCRIPTION_NOT_FOUND = "Not Found";
    private static final String ERROR_CODE_STATEMENT_NOT_FOUND = "STATEMENT_NOT_FOUND";

    @ExceptionHandler(StatementNotFoundException.class)
    public ProblemDetail handleStatementNotFound(StatementNotFoundException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                buildProblemDetailTypeURI(request, TYPE_STATEMENT),
                TITLE_DESCRIPTION_NOT_FOUND,
                ex.getMessage(),
                ERROR_CODE_STATEMENT_NOT_FOUND);
    }
}
