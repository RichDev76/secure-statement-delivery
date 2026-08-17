package com.example.statementservice.statement.download;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.shared.Sha256Digest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = "statement.signed-link.rate-limit-per-minute=1")
class DownloadRateLimitIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenLinkBucketExhausted_WhenDownloadingAgain_ThenReturns429ProblemDetailWithRetryAfter() throws Exception {
        // Given: a real uploaded statement with a minted signed link, and a bucket of exactly one token
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var accountNumber = String.format("2%08d", System.currentTimeMillis() % 100000000L);

        var uploadResponseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", Sha256Digest.hexOf(originalBytes))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String statementId = JsonPath.read(uploadResponseBody, "$.statementId");

        var linkResponseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(linkResponseBody, "$.downloadLink");
        var uri = UriComponentsBuilder.fromUriString(downloadLink).build();

        // When: the first download consumes the only token and succeeds
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isOk());

        // Then: the next request against the same link is throttled with the full 429 contract
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }
}
