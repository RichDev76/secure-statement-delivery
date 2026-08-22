package com.example.statementservice.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves ADR 0004 envelope encryption end-to-end against real Postgres: every uploaded
 * statement gets its own random per-file DEK, wrapped and persisted, never shared across
 * statements even when the plaintext content is byte-for-byte identical.
 */
@AutoConfigureMockMvc
class EnvelopeEncryptionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID upload(byte[] content, String accountNumber) throws Exception {
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, content);
        var digest = new Sha256ContentDigest().hexOf(content);
        var responseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", digest)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(responseBody, "$.statementId"));
    }

    private byte[] encryptedDekOf(UUID statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT encrypted_dek FROM statements WHERE id = ?", byte[].class, statementId);
    }

    @Test
    void
            Given_TwoUploadedStatementsWithIdenticalContent_When_InspectingStoredEncryptedDeks_Then_EachHasADistinctWrappedDek()
                    throws Exception {
        // Given: byte-for-byte identical plaintext, uploaded twice under different accounts.
        var content = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var accountOne = String.format("1%08d", System.currentTimeMillis() % 100000000L);
        var accountTwo = String.format("2%08d", System.currentTimeMillis() % 100000000L);

        // When
        var firstStatementId = upload(content, accountOne);
        var secondStatementId = upload(content, accountTwo);

        // Then
        var firstWrappedDek = encryptedDekOf(firstStatementId);
        var secondWrappedDek = encryptedDekOf(secondStatementId);
        assertThat(firstWrappedDek).isNotNull().isNotEmpty();
        assertThat(secondWrappedDek).isNotNull().isNotEmpty();
        assertThat(firstWrappedDek).isNotEqualTo(secondWrappedDek);
    }
}
