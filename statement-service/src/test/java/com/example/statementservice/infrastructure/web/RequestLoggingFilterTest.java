package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.example.statementservice.support.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void GivenActuatorHealthPath_WhenFiltering_ThenRequestIsNotLogged() throws ServletException, IOException {
        // Given
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/actuator/health");
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = (req, res) -> {};

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When
            filter.doFilter(request, response, chain);

            // Then
            assertThat(logs.lines()).isEmpty();
        }
    }

    @Test
    void Given2xxResponse_WhenFiltering_ThenLoggedAtInfoWithMethodEndpointStatusAndDuration()
            throws ServletException, IOException {
        // Given
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/search");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(200);

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When
            filter.doFilter(request, response, chain);

            // Then
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.events().get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.lines().get(0))
                    .contains("GET")
                    .contains("/api/v1/statements/search")
                    .contains("status=200")
                    .contains("durationMs=");
        }
    }

    @Test
    void Given4xxResponse_WhenFiltering_ThenLoggedAtWarn() throws ServletException, IOException {
        // Given
        var request = new MockHttpServletRequest("POST", "/api/v1/statements/upload");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(400);

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When
            filter.doFilter(request, response, chain);

            // Then
            assertThat(logs.events().get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(logs.lines().get(0)).contains("status=400");
        }
    }

    @Test
    void Given5xxResponse_WhenFiltering_ThenLoggedAtError() throws ServletException, IOException {
        // Given
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/search");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(500);

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When
            filter.doFilter(request, response, chain);

            // Then
            assertThat(logs.events().get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(logs.lines().get(0)).contains("status=500");
        }
    }

    @Test
    void GivenChainThrows_WhenFiltering_ThenLoggedAsServerErrorAndExceptionPropagates() {
        // Given: the container hasn't set 500 on the response yet when the chain unwinds here
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/search");
        var response = new MockHttpServletResponse();
        var failure = new RuntimeException("boom");
        FilterChain chain = (req, res) -> {
            throw failure;
        };

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When / Then
            assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
            assertThat(logs.events().get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(logs.lines().get(0)).contains("status=500");
        }
    }

    @Test
    void GivenUriCarryingAFileNameSegment_WhenFiltering_ThenLoggedLineNeverContainsTheFileName()
            throws ServletException, IOException {
        // Given
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/download/statement-2026-07.pdf");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> response.setStatus(200);

        try (var logs = LogCapture.forClass(RequestLoggingFilter.class)) {
            // When
            filter.doFilter(request, response, chain);

            // Then
            assertThat(logs.lines().get(0))
                    .doesNotContain("statement-2026-07.pdf")
                    .contains("/api/v1/statements/download");
        }
    }
}
