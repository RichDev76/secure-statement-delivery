package com.example.statementservice.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * End-to-end proof that the S3/Floci storage migration works for real: a genuine upload is
 * AES-GCM encrypted and written to a live Floci container via {@code S3StatementFileStore}, then
 * a signed link is minted and the download endpoint decrypts the same object back out, byte for
 * byte. Exercises the full HTTP stack - no mocks on the storage path.
 */
@AutoConfigureMockMvc
class StatementUploadDownloadIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenValidPdf_WhenUploadedThenLinkedThenDownloaded_ThenDecryptedBytesMatchOriginal() throws Exception {
        // Given
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var digest = Sha256Digest.hexOf(originalBytes);
        var accountNumber = "1" + System.currentTimeMillis() % 100000000L;
        var uploadRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"));
        var linkRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"));

        // When: upload
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

        // When: mint signed link
        var linkResponseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .with(linkRole))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(linkResponseBody, "$.downloadLink");
        var uri = UriComponentsBuilder.fromUriString(downloadLink).build();

        // When: download via the whitelisted signed link (no auth)
        var downloadResult = mockMvc.perform(get(uri.getPath())
                        .queryParam("expires", uri.getQueryParams().getFirst("expires"))
                        .queryParam("linkId", uri.getQueryParams().getFirst("linkId"))
                        .queryParam("signature", uri.getQueryParams().getFirst("signature")))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(originalBytes);
    }
}
