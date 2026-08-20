package com.example.statementservice.infrastructure.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

// Documents the accepted ADR 0012 ordering: argument resolution runs before @PreAuthorize.
@AutoConfigureMockMvc
class MethodSecurityDenialIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void
            GivenAuthenticatedJwtWithWrongRoleAndMalformedRequest_WhenArgumentResolutionFails_ThenValidationAnswers400BeforeAuthorization()
                    throws Exception {
        // Given
        var wrongRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + AppRole.SEARCH));
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());

        // When / Then
        mockMvc.perform(multipart("/api/v1/statements/upload").file(file).with(wrongRole))
                .andExpect(status().isBadRequest());
    }
}
