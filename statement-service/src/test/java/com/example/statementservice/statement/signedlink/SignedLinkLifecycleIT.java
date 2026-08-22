package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
class SignedLinkLifecycleIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void shortExpiry(DynamicPropertyRegistry registry) {
        registry.add("statement.signed-link.expiry", () -> "2s");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Statement seedStatement() {
        var statement = Statement.builder()
                .id(UUID.randomUUID())
                .accountNumber("9" + String.format("%08d", System.nanoTime() % 100000000L))
                .statementDate(LocalDate.of(2026, 7, 31))
                .uploadFileName("statement-" + UUID.randomUUID() + ".pdf")
                .storageKey("/unused/in/this/test.pdf.enc")
                .encryptedDek(new byte[] {1, 2, 3, 4, 5})
                .uploadedAt(OffsetDateTime.now())
                .encrypted(true)
                .build();
        return statementRepository.save(statement);
    }

    private UriComponentsBuilder mintLinkUri(UUID statementId, String accountNumber) throws Exception {
        var responseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .queryParam("accountNumber", accountNumber)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(responseBody, "$.downloadLink");
        return UriComponentsBuilder.fromUriString(downloadLink);
    }

    private long countAuditRows(UUID statementId, String action) {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE statement_id = ? AND action = ?",
                Long.class,
                statementId,
                action);
        return count == null ? 0 : count;
    }

    @Test
    void Given_ValidLink_When_DownloadingTwice_Then_BothSucceedAndBothAreAudited() throws Exception {
        // Given: a real upload so the download can actually stream decrypted bytes back.
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var digest = new Sha256ContentDigest().hexOf(originalBytes);
        var accountNumber = String.format("1%08d", System.currentTimeMillis() % 100000000L);
        var uploadResponseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", digest)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String statementId = JsonPath.read(uploadResponseBody, "$.statementId");
        var uri = mintLinkUri(UUID.fromString(statementId), accountNumber).build();

        // When: the same link is downloaded twice
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get(uri.getPath())
                            .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                            .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                            .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                    .andExpect(status().isOk())
                    .andExpect(content -> assertThat(content.getResponse().getContentAsByteArray())
                            .isEqualTo(originalBytes));
        }

        // Then: audit writes are fire-and-forget (AuditService.record submits to an executor), so
        // poll rather than asserting immediately after the HTTP response returns.
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(countAuditRows(UUID.fromString(statementId), "DOWNLOAD_SUCCESS"))
                        .isEqualTo(2));
    }

    @Test
    void Given_LinkRedeemedUpToMaxRedemptions_When_RedeemingOneMoreTime_Then_TreatedAsExpired() throws Exception {
        // Given: statement.signed-link.max-redemptions defaults to 3 in the test profile - a real
        // upload so every one of the first 3 downloads actually succeeds, not just the check.
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var digest = new Sha256ContentDigest().hexOf(originalBytes);
        var accountNumber = String.format("1%08d", System.currentTimeMillis() % 100000000L);
        var uploadResponseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", digest)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String statementId = JsonPath.read(uploadResponseBody, "$.statementId");
        var uri = mintLinkUri(UUID.fromString(statementId), accountNumber).build();

        // When: the first 3 redemptions all succeed
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get(uri.getPath())
                            .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                            .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                            .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                    .andExpect(status().isOk());
        }

        // Then: the 4th is treated exactly like a naturally expired link - a 404, not a
        // distinguishable "redemption limit" response.
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isNotFound());
    }

    @Test
    void Given_TamperedSignature_When_Downloading_Then_Returns403() throws Exception {
        // Given
        var statement = seedStatement();
        var uri = mintLinkUri(statement.getId(), statement.getAccountNumber()).build();
        var signature = uri.getQueryParams().getFirst("signature");
        var tampered = signature.substring(0, signature.length() - 1) + (signature.endsWith("A") ? "B" : "A");

        // When / Then
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", tampered))
                .andExpect(status().isForbidden());
    }

    @Test
    void Given_TamperedExpiryParameter_When_Downloading_Then_Returns403() throws Exception {
        // Given
        var statement = seedStatement();
        var uri = mintLinkUri(statement.getId(), statement.getAccountNumber()).build();
        var tamperedExpires = Long.parseLong(uri.getQueryParams().getFirst("expires")) + 3600;

        // When / Then
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", String.valueOf(tamperedExpires))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isForbidden());
    }

    @Test
    void Given_TamperedLinkIdParameter_When_Downloading_Then_Returns403() throws Exception {
        // Given
        var statement = seedStatement();
        var uri = mintLinkUri(statement.getId(), statement.getAccountNumber()).build();

        // When / Then
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", UUID.randomUUID().toString())
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isForbidden());
    }

    @Test
    void Given_ExpiredLink_When_Downloading_Then_RejectedAndFailureAudited() throws Exception {
        // Given: statement.signed-link.expiry is overridden to 2s for this test class.
        var statement = seedStatement();
        var uri = mintLinkUri(statement.getId(), statement.getAccountNumber()).build();
        Thread.sleep(2100);

        // When / Then
        mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isNotFound());
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(countAuditRows(statement.getId(), "DOWNLOAD_FAILED"))
                        .isGreaterThanOrEqualTo(1));
    }
}
