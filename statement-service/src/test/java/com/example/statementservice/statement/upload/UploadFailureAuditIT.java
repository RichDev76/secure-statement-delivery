package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLogRepository;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UploadFailureAuditIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void GivenUploadWithMismatchedDigest_WhenUploadFails_ThenUploadFailedAuditRowIsPersisted() throws Exception {
        // Given: a valid PDF but a digest header that does not match its contents
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        var wrongDigest = "0".repeat(64);
        var accountNumber = String.format("2%08d", System.currentTimeMillis() % 100000000L);
        var uploadRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"));

        // When
        mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", wrongDigest)
                        .with(uploadRole))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DIGEST_MISMATCH"));

        // Then: an UPLOAD_FAILED audit row with reason digest_mismatch is persisted (async write)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var uploadFailedRows = auditLogRepository.findAll().stream()
                    .filter(row -> AuditAction.UPLOAD_FAILED.getValue().equals(row.getAction()))
                    .filter(row -> accountNumber.equals(row.getAccountNumber()))
                    .toList();
            assertThat(uploadFailedRows).hasSize(1);
            assertThat(uploadFailedRows.getFirst().getDetails())
                    .containsEntry("reason", UploadFailureReason.DIGEST_MISMATCH.getValue());
        });
    }
}
