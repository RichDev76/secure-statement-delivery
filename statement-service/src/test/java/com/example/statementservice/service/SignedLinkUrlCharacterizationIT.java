package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.SignedLinkRepository;
import com.example.statementservice.repository.StatementRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
class SignedLinkUrlCharacterizationIT extends AbstractIntegrationTest {

    private record SeededStatement(UUID id, String fileName) {}

    private static Pattern signedUrlShape(String fileName) {
        return Pattern.compile("^http://localhost/api/v1/statements/download/" + Pattern.quote(fileName)
                + "\\?expires=(\\d+)&signature=([A-Za-z0-9_-]{43})$");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private SignedLinkRepository signedLinkRepository;

    private SeededStatement seedStatement() {
        // (account_number, statement_date) has a unique index, and the file name feeds the
        // deterministic HMAC token: same file within one second would collide tokens.
        var fileName = "statement-" + UUID.randomUUID() + ".pdf";
        var statement = Statement.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-" + UUID.randomUUID())
                .statementDate(LocalDate.of(2026, 7, 31))
                .uploadFileName(fileName)
                .filePath("/unused/in/this/test.pdf.enc")
                .uploadedAt(OffsetDateTime.now())
                .encrypted(true)
                .build();
        statementRepository.save(statement);
        return new SeededStatement(statement.getId(), fileName);
    }

    private String mintDownloadLink(UUID statementId) throws Exception {
        var responseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.downloadLink");
    }

    @Test
    void GivenExistingStatement_WhenMintingSignedLink_ThenUrlMatchesPinnedShapeAndExpiryWindow() throws Exception {
        var seeded = seedStatement();
        var mintedAtEpoch = OffsetDateTime.now().toEpochSecond();

        var downloadLink = mintDownloadLink(seeded.id());

        var matcher = signedUrlShape(seeded.fileName()).matcher(downloadLink);
        assertThat(matcher.matches())
                .as("URL must match pinned shape, was: %s", downloadLink)
                .isTrue();
        var expires = Long.parseLong(matcher.group(1));
        assertThat(expires)
                .as("expires must be ~900s (link-expiry-seconds) after minting")
                .isBetween(mintedAtEpoch + 890, mintedAtEpoch + 910);
    }

    @Test
    void GivenExistingStatement_WhenMintingSignedLink_ThenUrlSignatureIsThePersistedSingleUseToken() throws Exception {
        var seeded = seedStatement();

        var downloadLink = mintDownloadLink(seeded.id());

        var signature = UriComponentsBuilder.fromUriString(downloadLink)
                .build()
                .getQueryParams()
                .getFirst("signature");
        var storedLink = signedLinkRepository.findByToken(signature);
        assertThat(storedLink).isPresent();
        assertThat(storedLink.get().getStatementId()).isEqualTo(seeded.id());
        assertThat(storedLink.get().isSingleUse()).isTrue();
        assertThat(storedLink.get().isUsed()).isFalse();
        var expiresInUrl = Long.parseLong(UriComponentsBuilder.fromUriString(downloadLink)
                .build()
                .getQueryParams()
                .getFirst("expires"));
        assertThat(storedLink.get().getExpiresAt().toEpochSecond())
                .as("expires in the URL must equal the persisted expiry — validateAndConsume depends on it")
                .isEqualTo(expiresInUrl);
    }
}
