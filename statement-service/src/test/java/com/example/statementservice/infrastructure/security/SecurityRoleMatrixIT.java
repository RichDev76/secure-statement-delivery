package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class SecurityRoleMatrixIT extends AbstractIntegrationTest {

    private record EndpointCase(HttpMethod method, String path, String allowedRole, String wrongRole) {}

    static Stream<EndpointCase> protectedEndpoints() {
        return Stream.of(
                new EndpointCase(HttpMethod.GET, "/api/v1/statements/audit/logs", "AuditLogsSearch", "Search"),
                new EndpointCase(HttpMethod.GET, "/api/v1/statements/search", "Search", "AuditLogsSearch"),
                new EndpointCase(
                        HttpMethod.GET,
                        "/api/v1/statements/link/" + UUID.randomUUID(),
                        "GenerateSignedLink",
                        "Search"));
    }

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor jwtWithRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions perform(EndpointCase endpoint, RequestPostProcessor auth) throws Exception {
        // Every case in this matrix is GET; POST /upload needs multipart and is covered separately.
        var request = get(endpoint.path());
        return mockMvc.perform(auth == null ? request : request.with(auth));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void GivenNoAuthentication_WhenCallingProtectedEndpoint_ThenProblemDetail401IsReturned(EndpointCase endpoint)
            throws Exception {
        perform(endpoint, null)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthenticated"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void GivenJwtWithWrongRole_WhenCallingProtectedEndpoint_ThenProblemDetail403IsReturned(EndpointCase endpoint)
            throws Exception {
        perform(endpoint, jwtWithRole(endpoint.wrongRole()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void GivenJwtWithAllowedRole_WhenCallingProtectedEndpoint_ThenSecurityGrantsAccess(EndpointCase endpoint)
            throws Exception {
        perform(endpoint, jwtWithRole(endpoint.allowedRole()))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("security must not block a correctly-roled caller")
                        .isNotIn(401, 403));
    }

    @Test
    void GivenUploadRoleJwt_WhenUploadingStatement_ThenSecurityGrantsAccess() throws Exception {
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/statements/upload").file(file).with(jwtWithRole("Upload")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("security must not block a correctly-roled caller")
                        .isNotIn(401, 403));
    }

    @Test
    void GivenJwtWithWrongRole_WhenUploadingStatement_ThenProblemDetail403IsReturned() throws Exception {
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/statements/upload").file(file).with(jwtWithRole("Search")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void GivenKeycloakShapedRealmAccessClaim_WhenCallingSearch_ThenProductionConverterGrantsAccess() throws Exception {
        // Given: authorities derived by the real KeycloakRoleConverter from a realm_access
        // claim, not injected directly - every other case here bypasses the converter.
        var keycloakJwt = jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("Search"))))
                .authorities(new KeycloakRoleConverter());

        // When
        var result = mockMvc.perform(get("/api/v1/statements/search").with(keycloakJwt))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus())
                .as("the production role converter must grant access from realm_access.roles")
                .isNotIn(401, 403);
    }

    @Test
    void GivenNonPostRequestToUploadPath_WhenCalledWithNonUploadRole_ThenTheAdminRuleDoesNotApply() throws Exception {
        // The admin rule is scoped to POST /upload only; a GET on the same path is governed
        // solely by anyRequest().authenticated() - any authenticated caller passes security,
        // regardless of role, proving the matcher no longer matches every HTTP method on the path.
        var result = mockMvc.perform(get("/api/v1/statements/upload").with(jwtWithRole("Search")))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("a non-Upload role must not be blocked by the Upload-only admin rule on a method it doesn't cover")
                .isNotEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
