package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
                + "\\?expires=(\\d+)&linkId=([0-9a-fA-F-]{36})&signature=([A-Za-z0-9_-]{43})$");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private SignedLinkRepository signedLinkRepository;

    private SeededStatement seedStatement() {
        var fileName = "statement-" + UUID.randomUUID() + ".pdf";
        var statement = Statement.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-" + UUID.randomUUID())
                .statementDate(LocalDate.of(2026, 7, 31))
                .uploadFileName(fileName)
                .storageKey("/unused/in/this/test.pdf.enc")
                .encryptedDek(new byte[] {1, 2, 3, 4, 5})
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
                .as("URL must match pinned shape (expires, linkId nonce, signature), was: %s", downloadLink)
                .isTrue();
        var expires = Long.parseLong(matcher.group(1));
        assertThat(expires)
                .as("expires must be ~900s (statement.signed-link.expiry) after minting")
                .isBetween(mintedAtEpoch + 890, mintedAtEpoch + 910);
    }

    @Test
    void GivenExistingStatement_WhenMintingSignedLink_ThenUrlSignatureIsOnlyFindableViaItsHash() throws Exception {
        var seeded = seedStatement();

        var downloadLink = mintDownloadLink(seeded.id());

        var queryParams =
                UriComponentsBuilder.fromUriString(downloadLink).build().getQueryParams();
        var signature = queryParams.getFirst("signature");
        var linkIdInUrl = UUID.fromString(queryParams.getFirst("linkId"));

        // The raw signature must never be directly usable as a lookup key (ADR 0021) - only its
        // SHA-256 hash resolves to the persisted row.
        var storedLink =
                signedLinkRepository.findByTokenHash(Sha256Digest.hexOf(signature.getBytes(StandardCharsets.UTF_8)));
        assertThat(storedLink).isPresent();
        assertThat(storedLink.get().getId()).isEqualTo(linkIdInUrl);
        assertThat(storedLink.get().getStatementId()).isEqualTo(seeded.id());
        assertThat(signedLinkRepository.findByTokenHash(signature))
                .as("the raw signature itself must not resolve as if it were already a hash")
                .isEmpty();

        var expiresInUrl = Long.parseLong(queryParams.getFirst("expires"));
        assertThat(storedLink.get().getExpiresAt().toEpochSecond())
                .as("expires in the URL must equal the persisted expiry - validate() depends on it")
                .isEqualTo(expiresInUrl);
    }
}
