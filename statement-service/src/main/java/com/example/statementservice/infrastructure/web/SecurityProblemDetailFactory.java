package com.example.statementservice.infrastructure.web;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.CommonUtil.createProblemDetail;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@Slf4j
public final class SecurityProblemDetailFactory {

    public static final String ERROR_CODE_UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ERROR_CODE_ACCESS_DENIED = "ACCESS_DENIED";

    private SecurityProblemDetailFactory() {}

    public static ProblemDetail unauthenticated(HttpServletRequest request) {
        return withInstance(
                createProblemDetail(
                        HttpStatus.UNAUTHORIZED,
                        buildProblemDetailTypeURI(request, "/errors/authentication"),
                        "Unauthenticated",
                        "Authentication required to access this resource",
                        ERROR_CODE_UNAUTHENTICATED),
                request);
    }

    public static ProblemDetail accessDenied(HttpServletRequest request) {
        return withInstance(
                createProblemDetail(
                        HttpStatus.FORBIDDEN,
                        buildProblemDetailTypeURI(request, "/errors/authorization"),
                        "Forbidden",
                        "You do not have permission to access this resource",
                        ERROR_CODE_ACCESS_DENIED),
                request);
    }

    private static ProblemDetail withInstance(ProblemDetail problemDetail, HttpServletRequest request) {
        try {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException malformedUri) {
            log.debug("Request URI not representable as a URI instance", malformedUri);
        }
        return problemDetail;
    }
}
