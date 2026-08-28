package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.infrastructure.web.SecurityProblemDetailFactory;
import com.example.statementservice.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(new SecurityEndpointsProperties());
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void GivenUriCarryingAFileNameSegment_WhenAuthenticationEntryPointCommences_ThenLoggedLineNeverContainsTheFileName()
            throws Exception {
        // Given
        var entryPoint = securityConfig.problemDetailAuthEntryPoint(jsonMapper);
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/download/statement-2026-07.pdf");
        var response = new MockHttpServletResponse();

        try (var logs = LogCapture.forClass(SecurityConfig.class)) {
            // When
            entryPoint.commence(
                    request,
                    response,
                    new org.springframework.security.core.AuthenticationException("unauthenticated") {});

            // Then
            assertThat(logs.lines())
                    .as("the denial must still log something - a guard only checking absence "
                            + "would pass trivially if the log statement were deleted")
                    .isNotEmpty()
                    .as("the raw filename-carrying URI must never reach a log line")
                    .noneMatch(line -> line.contains("statement-2026-07.pdf"))
                    .as("the truncated endpoint label is the non-identifying replacement")
                    .anyMatch(line -> line.contains("/api/v1/statements/download"));
        }
    }

    @Test
    void GivenUriCarryingAFileNameSegment_WhenAccessDeniedHandlerHandles_ThenLoggedLineNeverContainsTheFileName()
            throws Exception {
        // Given
        var handler = securityConfig.problemDetailAccessDeniedHandler(jsonMapper);
        var request = new MockHttpServletRequest("GET", "/api/v1/statements/download/statement-2026-07.pdf");
        var response = new MockHttpServletResponse();

        try (var logs = LogCapture.forClass(SecurityProblemDetailFactory.class)) {
            // When
            handler.handle(request, response, new org.springframework.security.access.AccessDeniedException("denied"));

            // Then
            assertThat(logs.lines())
                    .isNotEmpty()
                    .noneMatch(line -> line.contains("statement-2026-07.pdf"))
                    .anyMatch(line -> line.contains("/api/v1/statements/download"));
        }
    }
}
