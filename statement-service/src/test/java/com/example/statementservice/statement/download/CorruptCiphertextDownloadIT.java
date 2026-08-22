package com.example.statementservice.statement.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLogRepository;
import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import com.example.statementservice.statement.StatementRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * A flipped ciphertext byte must surface as a 500 problem detail with a {@code DOWNLOAD_FAILED}
 * audit row - never as a silently truncated 200 with a {@code DOWNLOAD_SUCCESS} row.
 */
@AutoConfigureMockMvc
class CorruptCiphertextDownloadIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void
            GivenCorruptedCiphertextInObjectStore_WhenDownloadingViaSignedLink_ThenReturns500ProblemDetailWithDecryptionFailedErrorCodeAndNoPartialBody()
                    throws Exception {
        // Given: a genuine upload, then one ciphertext byte flipped directly in the object store
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var digest = new Sha256ContentDigest().hexOf(originalBytes);
        var accountNumber = String.format("1%08d", System.currentTimeMillis() % 100000000L);
        var uploadRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"));
        var linkRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"));

        var uploadResponseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", digest)
                        .with(uploadRole))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String statementId = JsonPath.read(uploadResponseBody, "$.statementId");

        var storageKey = statementRepository
                .findStatementById(UUID.fromString(statementId))
                .orElseThrow()
                .getStorageKey();
        corruptOneCiphertextByte(storageKey);

        var linkResponseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .queryParam("accountNumber", accountNumber)
                        .with(linkRole))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(linkResponseBody, "$.downloadLink");
        var uri = UriComponentsBuilder.fromUriString(downloadLink).build();
        var linkId = UUID.fromString(uri.getQueryParams().getFirst("linkId"));

        // When: downloading via the signed link
        var downloadResult = mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        // Then: an RFC 9457 body with the documented errorCode, not a partial plaintext body
        var body = downloadResult.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.errorCode")).isEqualTo("DECRYPTION_FAILED");
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isNotEqualTo(originalBytes);

        // Then: a DOWNLOAD_FAILED audit row with reason decryption_failed, and no DOWNLOAD_SUCCESS
        // row (audit writes are async, hence the await)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var failed = auditLogRepository.findBySignedLinkIdAndAction(linkId, AuditAction.DOWNLOAD_FAILED.getValue());
            assertThat(failed).anySatisfy(row -> assertThat(row.getDetails())
                    .containsEntry("reason", DownloadFailureReason.DECRYPTION_FAILED.getValue()));
        });
        assertThat(auditLogRepository.findBySignedLinkIdAndAction(linkId, AuditAction.DOWNLOAD_SUCCESS.getValue()))
                .isEmpty();
    }

    private void corruptOneCiphertextByte(String storageKey) {
        try (var s3Client = buildFlociClient()) {
            var stored = s3Client.getObjectAsBytes(
                            b -> b.bucket(STATEMENTS_BUCKET).key(storageKey))
                    .asByteArray();
            stored[stored.length - 1] ^= (byte) 0xFF;
            s3Client.putObject(b -> b.bucket(STATEMENTS_BUCKET).key(storageKey), RequestBody.fromBytes(stored));
        }
    }

    private static S3Client buildFlociClient() {
        return S3Client.builder()
                .endpointOverride(URI.create(flociEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
