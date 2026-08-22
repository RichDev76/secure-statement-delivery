package com.example.statementservice.statement.upload;

import static com.example.statementservice.UploadDownloadSteps.uniqueAccountNumber;
import static com.example.statementservice.UploadDownloadSteps.uploadPdf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DuplicateUploadIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenStatementForAccountAndMonthExists_WhenUploadedAgain_ThenReturns409WithStatementAlreadyExists()
            throws Exception {
        // Given
        var accountNumber = uniqueAccountNumber("3");
        uploadPdf(mockMvc, "statement.pdf", accountNumber);

        // When / Then
        var retryBytes = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var retryFile = new MockMultipartFile("file", "statement.pdf", MediaType.APPLICATION_PDF_VALUE, retryBytes);
        mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(retryFile)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", new Sha256ContentDigest().hexOf(retryBytes))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATEMENT_ALREADY_EXISTS"));
    }
}
