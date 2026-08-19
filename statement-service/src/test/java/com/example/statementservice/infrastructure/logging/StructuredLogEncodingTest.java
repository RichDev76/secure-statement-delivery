package com.example.statementservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Mirrors logback-spring.xml's LogstashEncoder field mapping to verify the resulting JSON shape without booting Spring. */
class StructuredLogEncodingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LoggerContext context;
    private LogstashEncoder encoder;

    @BeforeEach
    void setUp() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();

        encoder = new LogstashEncoder();
        encoder.setContext(context);

        var fieldNames = new LogstashFieldNames();
        fieldNames.setTimestamp("timestamp");
        fieldNames.setVersion("[ignore]");
        fieldNames.setMessage("message");
        fieldNames.setLogger("logger");
        fieldNames.setThread("thread");
        fieldNames.setLevel("level");
        fieldNames.setLevelValue("[ignore]");
        fieldNames.setStackTrace("stack_trace");
        encoder.setFieldNames(fieldNames);

        encoder.setCustomFields("{\"service\":\"statement-service\"}");
        encoder.addIncludeMdcKeyName("correlationId");

        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        MDC.clear();
    }

    private Map<String, Object> encodeAndParse(LoggingEvent event) throws Exception {
        var bytes = encoder.encode(event);
        return JSON.readValue(new String(bytes, StandardCharsets.UTF_8), Map.class);
    }

    private LoggingEvent eventFor(Level level, String message) {
        var logger = (Logger) context.getLogger("com.example.statementservice.SomeClass");
        return new LoggingEvent(getClass().getName(), logger, level, message, null, null);
    }

    @Test
    void GivenAnInfoEvent_WhenEncoded_ThenOutputIsSingleLineJsonWithExpectedKeys() throws Exception {
        // Given
        var event = eventFor(Level.INFO, "something happened");

        // When
        var json = encodeAndParse(event);

        // Then
        assertThat(json)
                .containsKeys("timestamp", "message", "logger", "thread", "level", "service")
                .doesNotContainKeys("@version", "level_value")
                .containsEntry("message", "something happened")
                .containsEntry("logger", "com.example.statementservice.SomeClass")
                .containsEntry("level", "INFO")
                .containsEntry("service", "statement-service");
    }

    @Test
    void GivenCorrelationIdInMdc_WhenEncoded_ThenCorrelationIdFieldIsPresent() throws Exception {
        // Given
        MDC.put("correlationId", "abc-123");
        var event = eventFor(Level.INFO, "with correlation id");

        // When
        var json = encodeAndParse(event);

        // Then
        assertThat(json).containsEntry("correlationId", "abc-123");
    }

    @Test
    void GivenAnEventWithAThrowable_WhenEncoded_ThenStackTraceFieldIsPresentAndBounded() throws Exception {
        // Given
        var logger = (Logger) context.getLogger("com.example.statementservice.SomeClass");
        var event = new LoggingEvent(
                getClass().getName(), logger, Level.ERROR, "failed", new RuntimeException("boom"), null);

        // When
        var json = encodeAndParse(event);

        // Then
        assertThat(json).containsKey("stack_trace");
        assertThat((String) json.get("stack_trace")).contains("RuntimeException: boom");
    }
}
