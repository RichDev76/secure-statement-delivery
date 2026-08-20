package com.example.statementservice.infrastructure.web;

import static com.example.statementservice.UploadDownloadSteps.downloadRequest;
import static com.example.statementservice.UploadDownloadSteps.mintDownloadLink;
import static com.example.statementservice.UploadDownloadSteps.uniqueAccountNumber;
import static com.example.statementservice.UploadDownloadSteps.uploadPdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ForwardedHeaderIT extends AbstractIntegrationTest {

    private static final String FORWARDED_CLIENT_IP = "203.0.113.9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void GivenForwardedHeaders_WhenMintingSignedLink_ThenLinkUsesForwardedSchemeAndHost() throws Exception {
        // Given
        var uploaded = uploadPdf(mockMvc, "statement.pdf", uniqueAccountNumber("4"));

        // When: the request arrives through a TLS-terminating proxy
        var linkResponseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", uploaded.statementId())
                        .queryParam("accountNumber", uploaded.accountNumber())
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "statements.public.example.com")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(linkResponseBody, "$.downloadLink");

        // Then
        assertThat(downloadLink).startsWith("https://statements.public.example.com/api/v1/statements/download/");
    }

    @Test
    void GivenXForwardedFor_WhenDownloading_ThenAuditRecordsForwardedClientIp() throws Exception {
        // Given
        var uploaded = uploadPdf(mockMvc, "statement.pdf", uniqueAccountNumber("5"));
        var linkUri = mintDownloadLink(mockMvc, uploaded.statementId(), uploaded.accountNumber());
        var linkId = UUID.fromString(linkUri.getQueryParams().getFirst("linkId"));

        // When: the download arrives through a proxy that appends the real client IP
        mockMvc.perform(downloadRequest(linkUri).header("X-Forwarded-For", FORWARDED_CLIENT_IP))
                .andExpect(status().isOk());

        // Then: the audit row carries the client's IP, not the proxy's
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var rows = auditLogRepository.findBySignedLinkIdAndAction(linkId, AuditAction.DOWNLOAD_SUCCESS.getValue());
            assertThat(rows).isNotEmpty();
            assertThat(rows.get(0).getDetails()).containsEntry("ip", FORWARDED_CLIENT_IP);
        });
    }
}
