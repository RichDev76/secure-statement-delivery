package com.example.statementservice.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class ProblemDetailSupport {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";

    // Shared by every feature-owned @RestControllerAdvice so each builds the same RFC 9457 shape.
    public static ProblemDetail createProblemDetail(
            HttpStatus status, URI type, String title, String detail, String errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : title);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setProperty("errorCode", errorCode);
        return problemDetail;
    }

    public static URI buildProblemDetailTypeURI(HttpServletRequest request, String typePathSegment) {
        var scheme = request.getScheme();
        var serverName = request.getServerName();
        int serverPort = request.getServerPort();

        var baseUrl = new StringBuilder(scheme).append("://").append(serverName);

        if (!((scheme.equals(HTTP) && serverPort == 80) || (scheme.equals(HTTPS) && serverPort == 443))) {
            baseUrl.append(":").append(serverPort);
        }

        if (typePathSegment != null && !typePathSegment.isEmpty()) {
            baseUrl.append(typePathSegment);
        }

        return URI.create(baseUrl.toString());
    }
}
