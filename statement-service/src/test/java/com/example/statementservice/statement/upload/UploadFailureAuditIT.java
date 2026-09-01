package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.UploadDownloadSteps;
import com.example.statementservice.audit.AuditAction;
import com.example.statementservice.audit.AuditLogRepository;
import com.example.statementservice.infrastructure.storage.s3.S3StorageProperties;
import com.example.statementservice.statement.StatementRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

@AutoConfigureMockMvc
class UploadFailureAuditIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3StorageProperties s3StorageProperties;

    @Test
    void GivenUploadWithMismatchedDigest_WhenUploadFails_ThenUploadFailedAuditRowIsPersisted() throws Exception {
        // Given
        var accountNumber = UploadDownloadSteps.uniqueAccountNumber("2");

        // When
        performUploadWithMismatchedDigest(accountNumber)
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

    @Test
    void GivenUploadWithMismatchedDigest_WhenUploadFails_ThenNoStatementRowOrStorageObjectRemains() throws Exception {
        // Given: validation fails after the file is readable but before anything is stored
        var accountNumber = UploadDownloadSteps.uniqueAccountNumber("3");
        var objectCountBefore = totalBucketObjectCount();

        // When
        performUploadWithMismatchedDigest(accountNumber).andExpect(status().isBadRequest());

        // Then: no metadata row, and the bucket holds exactly what it held before
        assertThat(statementRepository.existsByAccountNumberAndStatementDate(accountNumber, LocalDate.of(2026, 7, 1)))
                .isFalse();
        assertThat(totalBucketObjectCount()).isEqualTo(objectCountBefore);
    }

    private ResultActions performUploadWithMismatchedDigest(String accountNumber) throws Exception {
        var originalBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, originalBytes);
        return mockMvc.perform(multipart("/api/v1/statements/upload")
                .file(file)
                .param("accountNumber", accountNumber)
                .param("date", "2026-07-01")
                .header("X-Message-Digest", "0".repeat(64))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))));
    }

    private int totalBucketObjectCount() {
        return s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(s3StorageProperties.getBucket())
                        .build())
                .keyCount();
    }
}
