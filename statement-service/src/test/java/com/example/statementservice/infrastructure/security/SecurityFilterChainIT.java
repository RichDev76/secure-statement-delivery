package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class SecurityFilterChainIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor jwtWithRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static ResultMatcher accessGranted() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("authorization must pass: any status except 401/403")
                .isNotIn(401, 403);
    }

    @Test
    void GivenNoAuthentication_WhenSearchingStatements_ThenProblemDetail401IsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/search"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthenticated"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void GivenJwtWithoutRequiredRole_WhenSearchingStatements_ThenProblemDetail403IsReturned() throws Exception {
        var wrongRole = jwtWithRole("Upload");

        mockMvc.perform(get("/api/v1/statements/search").with(wrongRole))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void GivenJwtWithSearchRole_WhenSearchingStatements_ThenAccessIsGranted() throws Exception {
        var searchRole = jwtWithRole("Search");

        mockMvc.perform(get("/api/v1/statements/search").with(searchRole)).andExpect(accessGranted());
    }

    @Test
    void GivenJwtWithUploadRole_WhenUploadingStatementWithoutCsrfToken_ThenAccessIsGranted() throws Exception {
        // Given: POST without a CSRF token exercises the csrfIgnored matcher configuration
        var uploadRole = jwtWithRole("Upload");
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/statements/upload").file(file).with(uploadRole))
                .andExpect(accessGranted());
    }

    @Test
    void GivenJwtWithAuditLogsSearchRole_WhenQueryingAuditLogs_ThenAccessIsGranted() throws Exception {
        var auditRole = jwtWithRole("AuditLogsSearch");

        mockMvc.perform(get("/api/v1/statements/audit/logs").with(auditRole)).andExpect(accessGranted());
    }

    @Test
    void GivenJwtWithGenerateSignedLinkRole_WhenRequestingSignedLink_ThenAccessIsGranted() throws Exception {
        var linkRole = jwtWithRole("GenerateSignedLink");

        mockMvc.perform(get("/api/v1/statements/link/00000000-0000-0000-0000-000000000000")
                        .with(linkRole))
                .andExpect(accessGranted());
    }

    @Test
    void GivenJwtWithoutGenerateSignedLinkRole_WhenRequestingSignedLink_ThenProblemDetail403IsReturned()
            throws Exception {
        var wrongRole = jwtWithRole("Search");

        mockMvc.perform(get("/api/v1/statements/link/00000000-0000-0000-0000-000000000000")
                        .with(wrongRole))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void GivenNoAuthentication_WhenCheckingWhitelistedHealthEndpoint_ThenOkIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/actuator/health")).andExpect(status().isOk());
    }
}
