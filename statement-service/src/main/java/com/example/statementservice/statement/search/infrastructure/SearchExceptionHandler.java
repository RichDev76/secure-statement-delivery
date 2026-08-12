package com.example.statementservice.statement.search.infrastructure;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.CommonUtil.createProblemDetail;

import com.example.statementservice.statement.search.InvalidInputException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RestControllerAdvice
public class SearchExceptionHandler {

    private static final String TYPE_SEARCH = "/errors/search";
    private static final String TITLE_DESCRIPTION_INVALID_SEARCH_PARAMETERS = "Invalid Search Parameters";
    private static final String ERROR_CODE_INVALID_SEARCH_PARAMETERS = "INVALID_SEARCH_PARAMETERS";

    @ExceptionHandler(InvalidInputException.class)
    public ProblemDetail handleInvalidInput(InvalidInputException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_SEARCH),
                TITLE_DESCRIPTION_INVALID_SEARCH_PARAMETERS,
                ex.getMessage(),
                ERROR_CODE_INVALID_SEARCH_PARAMETERS);
    }
}
