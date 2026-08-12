package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/statements/search");

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;
    private Level originalLevel;

    @BeforeEach
    void captureHandlerLogs() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        originalLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        handlerLogger.setLevel(originalLevel);
    }

    @Test
    void GivenBeanValidationException_WhenHandleValidationExceptions_ThenReturnsBadRequestWithValidationFailedCode() {
        // Given
        var ex = new MissingServletRequestParameterException("accountNumber", "String");

        // When
        var response = handler.handleValidationExceptions(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getProperties()).containsEntry("errorCode", "INVALID_INPUT");
    }

    @Test
    void GivenRuntimeExceptionWithSensitiveMessage_WhenHandleGeneric_ThenResponseDetailDoesNotLeakMessage() {
        // Given
        var sensitiveMessage = "Failed to create storage directory: /data/files/statements/abc123";
        var ex = new RuntimeException(sensitiveMessage);

        // When
        var response = handler.handleGeneric(ex, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).doesNotContain(sensitiveMessage);
    }

    @Test
    void GivenNullPointerExceptionWithSensitiveMessage_WhenHandleGeneric_ThenResponseDetailDoesNotLeakMessage() {
        // Given
        var sensitiveMessage = "db connection string is jdbc:postgresql://prod-db:5432/secrets is null";
        var ex = new NullPointerException(sensitiveMessage);

        // When
        var response = handler.handleGeneric(ex, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).doesNotContain(sensitiveMessage);
    }

    @Test
    void GivenUnmappedException_WhenHandleGeneric_ThenResponseUsesProblemJsonContentTypeAndLogsAtError() {
        // Given
        var ex = new IllegalStateException("boom");

        // When
        var response = handler.handleGeneric(ex, request);

        // Then
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Unhandled exception"));
    }

    @Test
    void Given4xxErrorResponseException_WhenHandleGeneric_ThenResponseDetailExposesExceptionMessage() {
        // Given
        var ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed request body");

        // When
        var response = handler.handleGeneric(ex, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo(ex.getMessage());
    }

    @Test
    void
            Given5xxErrorResponseException_WhenHandleGeneric_ThenResponseDetailIsGenericAndDoesNotLeakMessageAndLogsAtError() {
        // Given
        var sensitiveMessage = "connection refused to internal-db.corp:5432";
        var ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, sensitiveMessage);

        // When
        var response = handler.handleGeneric(ex, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).doesNotContain(sensitiveMessage);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("Unclassified 5xx ErrorResponse"));
    }
}
