package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.shared.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SignedLinkRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private SignedLinkRepository signedLinkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedLink persistLink(String rawToken, OffsetDateTime expiresAt) {
        var link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setTokenHash(Sha256Digest.hexOf(rawToken.getBytes(StandardCharsets.UTF_8)));
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        link.setCreatedBy("it-test");
        return signedLinkRepository.saveAndFlush(link);
    }

    @Test
    void Given_ExistingTokenHash_When_InsertingDuplicate_Then_UniqueConstraintViolationRaised() {
        // Given
        var rawToken = "duplicate-token-" + UUID.randomUUID();
        persistLink(rawToken, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        var duplicate = new SignedLink();
        duplicate.setId(UUID.randomUUID());
        duplicate.setTokenHash(Sha256Digest.hexOf(rawToken.getBytes(StandardCharsets.UTF_8)));
        duplicate.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        duplicate.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        duplicate.setCreatedBy("it-test");

        // When / Then
        assertThatThrownBy(() -> signedLinkRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void Given_PersistedLink_When_FindByTokenHash_Then_LinkIsReturned() {
        // Given
        var rawToken = "findable-token-" + UUID.randomUUID();
        var persisted = persistLink(rawToken, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        // When
        var found = signedLinkRepository.findByTokenHash(persisted.getTokenHash());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(persisted.getId());
    }

    @Test
    void Given_ExpiredAndActiveLinks_When_DeletingExpired_Then_OnlyExpiredLinksRemoved() {
        // Given
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var expired = persistLink("expired-token-" + UUID.randomUUID(), now.minusMinutes(5));
        var active = persistLink("active-token-" + UUID.randomUUID(), now.plusMinutes(30));

        // When
        var deleted = signedLinkRepository.deleteExpired(now, 500);

        // Then
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(signedLinkRepository.findByTokenHash(expired.getTokenHash())).isEmpty();
        assertThat(signedLinkRepository.findByTokenHash(active.getTokenHash())).isPresent();
    }

    @Test
    void Given_CreatedLink_When_ReadingRawRowFromDb_Then_StoredValueIsNotUsableAsToken() {
        // Given
        var rawToken = "raw-jdbc-token-" + UUID.randomUUID();
        var persisted = persistLink(rawToken, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        // When: read the column value straight off the row, bypassing the repository/service layer
        var storedValue = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM signed_links WHERE id = ?", String.class, persisted.getId());

        // Then: the stored value is a hash, not the raw token
        assertThat(storedValue).isNotEqualTo(rawToken);
        assertThat(storedValue).isEqualTo(Sha256Digest.hexOf(rawToken.getBytes(StandardCharsets.UTF_8)));

        // And: an attacker who leaks only this stored value and replays it as if it were the raw
        // token (the same hashing step SignedLinkService.validate() applies to incoming tokens)
        // does not resolve to the link - the leaked hash is not itself a usable credential.
        var hashOfLeakedValue = Sha256Digest.hexOf(storedValue.getBytes(StandardCharsets.UTF_8));
        assertThat(signedLinkRepository.findByTokenHash(hashOfLeakedValue)).isEmpty();
    }
}
