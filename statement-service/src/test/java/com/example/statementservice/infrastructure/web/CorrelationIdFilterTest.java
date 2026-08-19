package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void GivenNoCorrelationIdHeader_WhenFiltering_ThenNewCorrelationIdIsGenerated()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcValueInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> mdcValueInChain.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        try {
            filter.doFilter(request, response, chain);

            String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertNotNull(headerValue, "Correlation id header should be set on response");
            assertEquals(headerValue, mdcValueInChain.get(), "MDC value should match header value inside the chain");
        } finally {
            assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        }
    }

    @Test
    void GivenCorrelationIdHeader_WhenFiltering_ThenExistingIdIsReused() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String existingId = "existing-correlation-id";
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);

        AtomicReference<String> mdcValueInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> mdcValueInChain.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        try {
            filter.doFilter(request, response, chain);

            String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertEquals(existingId, headerValue, "Existing header value should be preserved");
            assertEquals(existingId, mdcValueInChain.get(), "MDC value should follow incoming header");
        } finally {
            assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        }
    }

    @Test
    void GivenOverlongHeader_WhenFiltering_ThenReplacementIdIsGenerated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "a".repeat(CorrelationIdFilter.MAX_LENGTH + 1));
        FilterChain chain = (req, res) -> {};

        try {
            filter.doFilter(request, response, chain);

            String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertNotNull(headerValue);
            assertEquals(36, headerValue.length(), "generated UUID, not the oversized input");
            assertThatCode(() -> UUID.fromString(headerValue)).doesNotThrowAnyException();
        } finally {
            assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        }
    }

    @Test
    void GivenHeaderWithIllegalCharacters_WhenFiltering_ThenReplacementIdIsGenerated()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String malicious = "not a valid id; DROP TABLE statements";
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, malicious);
        FilterChain chain = (req, res) -> {};

        try {
            filter.doFilter(request, response, chain);

            String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertNotEquals(malicious, headerValue);
            assertThatCode(() -> UUID.fromString(headerValue)).doesNotThrowAnyException();
        } finally {
            assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        }
    }

    @Test
    void GivenHeaderContainingNewline_WhenFiltering_ThenReplacementIdIsGeneratedAndNewlineNeverReachesMdc()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String logInjectionAttempt = "abc\ndef";
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, logInjectionAttempt);

        AtomicReference<String> mdcValueInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValueInChain.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        try {
            filter.doFilter(request, response, chain);

            assertNotEquals(logInjectionAttempt, mdcValueInChain.get());
            assertThatCode(() -> UUID.fromString(mdcValueInChain.get())).doesNotThrowAnyException();
        } finally {
            assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        }
    }
}
