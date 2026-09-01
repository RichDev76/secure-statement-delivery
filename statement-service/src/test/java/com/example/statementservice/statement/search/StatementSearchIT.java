package com.example.statementservice.statement.search;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class StatementSearchIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private void upload(String accountNumber, String date) throws Exception {
        upload(accountNumber, date, "statement.pdf", 0);
    }

    private void upload(String accountNumber, String date, String fileName, int paddingBytes) throws Exception {
        var bytes = ("%PDF-1.4\n" + UUID.randomUUID() + "x".repeat(paddingBytes) + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, bytes);
        mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", date)
                        .header("X-Message-Digest", new Sha256ContentDigest().hexOf(bytes))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated());
    }

    private static String uniqueAccountNumber() {
        return String.format("3%011d", System.nanoTime() % 100_000_000_000L);
    }

    @Test
    void GivenStatementsInAndOutsideDateRange_WhenSearching_ThenOnlyInRangeStatementsForThatAccountReturn()
            throws Exception {
        // Given
        var accountNumber = uniqueAccountNumber();
        var otherAccount = uniqueAccountNumber();
        upload(accountNumber, "2026-01-15");
        upload(accountNumber, "2026-02-15");
        upload(accountNumber, "2026-03-15");
        upload(otherAccount, "2026-02-20");

        // When / Then
        mockMvc.perform(get("/api/v1/statements/search")
                        .queryParam("accountNumber", accountNumber)
                        .queryParam("startDate", "2026-02-01")
                        .queryParam("endDate", "2026-03-31")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Search"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].accountNumber").value(everyItemIs(accountNumber)))
                .andExpect(jsonPath("$.content[*].date")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("2026-02-15", "2026-03-15")));
    }

    @Test
    void GivenMoreStatementsThanPageSize_WhenSearchingPaged_ThenPageMetadataAndContentSplitCorrectly()
            throws Exception {
        // Given
        var accountNumber = uniqueAccountNumber();
        upload(accountNumber, "2026-01-15");
        upload(accountNumber, "2026-02-15");
        upload(accountNumber, "2026-03-15");
        var searchRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_Search"));

        // When / Then: first page carries two of three, metadata reflects the full result set
        mockMvc.perform(get("/api/v1/statements/search")
                        .queryParam("accountNumber", accountNumber)
                        .queryParam("startDate", "2026-01-01")
                        .queryParam("endDate", "2026-12-31")
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .with(searchRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/statements/search")
                        .queryParam("accountNumber", accountNumber)
                        .queryParam("startDate", "2026-01-01")
                        .queryParam("endDate", "2026-12-31")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .with(searchRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void GivenStatementsOfVaryingSizes_WhenSearchingSortedByFileSize_ThenResultsOrderBySizeAscending()
            throws Exception {
        // Given
        var accountNumber = uniqueAccountNumber();
        upload(accountNumber, "2026-01-15", "statement.pdf", 200);
        upload(accountNumber, "2026-02-15", "statement.pdf", 0);
        upload(accountNumber, "2026-03-15", "statement.pdf", 100);

        // When / Then
        mockMvc.perform(get("/api/v1/statements/search")
                        .queryParam("accountNumber", accountNumber)
                        .queryParam("startDate", "2026-01-01")
                        .queryParam("endDate", "2026-12-31")
                        .queryParam("sort", "fileSize,asc")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Search"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].date")
                        .value(org.hamcrest.Matchers.contains("2026-02-15", "2026-03-15", "2026-01-15")));
    }

    @Test
    void GivenStatementsWithDistinctFileNames_WhenSearchingSortedByFileName_ThenResultsOrderByNameDescending()
            throws Exception {
        // Given
        var accountNumber = uniqueAccountNumber();
        upload(accountNumber, "2026-01-15", "alpha.pdf", 0);
        upload(accountNumber, "2026-02-15", "charlie.pdf", 0);
        upload(accountNumber, "2026-03-15", "bravo.pdf", 0);

        // When / Then
        mockMvc.perform(get("/api/v1/statements/search")
                        .queryParam("accountNumber", accountNumber)
                        .queryParam("startDate", "2026-01-01")
                        .queryParam("endDate", "2026-12-31")
                        .queryParam("sort", "fileName,desc")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Search"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fileName")
                        .value(org.hamcrest.Matchers.contains("charlie.pdf", "bravo.pdf", "alpha.pdf")));
    }

    private static org.hamcrest.Matcher<Iterable<? extends String>> everyItemIs(String expected) {
        return org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(expected));
    }
}
