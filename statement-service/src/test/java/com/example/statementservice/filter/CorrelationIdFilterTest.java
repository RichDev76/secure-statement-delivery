package com.example.statementservice.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
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
    void reusesExistingCorrelationIdHeader() throws ServletException, IOException {
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
}
