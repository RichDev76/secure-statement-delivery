package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityFilterChainIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenUploadRoleJwt_WhenPostingWithoutCsrfToken_ThenAccessIsGranted() throws Exception {
        var uploadRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"));
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());

        var result = mockMvc.perform(
                        multipart("/api/v1/statements/upload").file(file).with(uploadRole))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("authorization must pass: any status except 401/403")
                .isNotIn(401, 403);
    }
}
