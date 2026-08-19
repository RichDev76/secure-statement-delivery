package com.example.statementservice.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PrometheusEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenAuthenticatedCaller_WhenScrapingPrometheusEndpoint_ThenExposesDownloadOutcomeMetric() throws Exception {
        // Given: at least one download outcome has been recorded (an invalid link is enough)
        mockMvc.perform(get("/api/v1/statements/download/statement.pdf")
                .queryParam("expires", "1")
                .queryParam("linkId", UUID.randomUUID().toString())
                .queryParam("signature", "bm90LWEtcmVhbC1zaWduYXR1cmU"));

        // When
        var scrape = mockMvc.perform(
                        get("/api/v1/statements/actuator/prometheus").with(jwt()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        assertThat(scrape).contains("statement_download_outcome_total");
    }

    @Test
    void GivenUnauthenticatedCaller_WhenScrapingPrometheusEndpoint_ThenReturns401() throws Exception {
        // When / Then: metrics are not part of the public whitelist
        mockMvc.perform(get("/api/v1/statements/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
