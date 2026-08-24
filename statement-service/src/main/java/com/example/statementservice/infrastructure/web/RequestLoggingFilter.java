package com.example.statementservice.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String ACTUATOR_PREFIX = "/api/v1/statements/actuator";
    private static final String FORMAT = "{} {} - status={} durationMs={}";
    private static final int SERVER_ERROR = 500;
    private static final int CLIENT_ERROR = 400;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
            logCompletion(request, response.getStatus(), startNanos);
        } catch (Exception e) {
            // response.getStatus() isn't set yet when the chain unwinds through here.
            logCompletion(request, SERVER_ERROR, startNanos);
            throw e;
        }
    }

    private void logCompletion(HttpServletRequest request, int status, long startNanos) {
        var durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        var endpoint = EndpointLabel.of(request.getRequestURI());
        var method = request.getMethod();

        if (status >= SERVER_ERROR) {
            log.error(FORMAT, method, endpoint, status, durationMs);
        } else if (status >= CLIENT_ERROR) {
            log.warn(FORMAT, method, endpoint, status, durationMs);
        } else {
            log.info(FORMAT, method, endpoint, status, durationMs);
        }
    }
}
