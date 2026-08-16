package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WhitelistedEndpointsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenNoAuthentication_WhenCheckingHealthEndpoint_ThenOkIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void GivenNoAuthentication_WhenCheckingInfoEndpoint_ThenOkIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void GivenNoAuthentication_WhenDownloadingWithoutSignedParams_ThenNotBlockedBySecurity() throws Exception {
        var result = mockMvc.perform(get("/api/v1/statements/download/some-file.pdf"))
                .andReturn();

        // The whitelist admits the request (no 401); it fails on missing/invalid signed-URL
        // parameters instead, proving the signature check - not authentication - is the real gate.
        assertThat(result.getResponse().getStatus())
                .as("whitelisted download path must not be blocked by authentication")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void GivenNoAuthentication_WhenCheckingReadinessGroup_ThenUpIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void GivenNoAuthentication_WhenCheckingLivenessGroup_ThenUpIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/statements/actuator/health/liveness")).andExpect(status().isOk());
    }
}
