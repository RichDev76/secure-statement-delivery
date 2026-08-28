package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LinkValidationResult Tests")
class LinkValidationResultTest {

    private SignedLink mockLink;
    private UUID linkId;
    private UUID statementId;

    @BeforeEach
    void setUp() {
        linkId = UUID.randomUUID();
        statementId = UUID.randomUUID();
        mockLink = new SignedLink();
        mockLink.setId(linkId);
        mockLink.setStatementId(statementId);
        mockLink.setToken("test-token-123");
        mockLink.setExpiresAt(OffsetDateTime.now().plusHours(1));
    }

    @Test
    void GivenNoArguments_WhenNotFound_ThenReturnsInvalidResultWithNullLinkAndNotFoundReason() {
        // When
        var result = LinkValidationResult.notFound();

        // Then
        assertThat(result.link()).isNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ValidationFailureReason.NOT_FOUND);
    }

    @Test
    void GivenLink_WhenExpired_ThenReturnsInvalidResultWithLinkAndExpiredReason() {
        // When
        var result = LinkValidationResult.expired(mockLink);

        // Then
        assertThat(result.link()).isEqualTo(mockLink);
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ValidationFailureReason.EXPIRED);
    }

    @Test
    void GivenNullLink_WhenExpired_ThenReturnsInvalidResultWithNullLink() {
        // When
        var result = LinkValidationResult.expired(null);

        // Then
        assertThat(result.link()).isNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ValidationFailureReason.EXPIRED);
    }

    @Test
    void GivenLink_WhenValid_ThenReturnsValidResultWithNullFailureReason() {
        // When
        var result = LinkValidationResult.valid(mockLink);

        // Then
        assertThat(result.link()).isEqualTo(mockLink);
        assertThat(result.valid()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void GivenNullLink_WhenValid_ThenReturnsValidResultWithNullLink() {
        // When
        var result = LinkValidationResult.valid(null);

        // Then
        assertThat(result.link()).isNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void GivenLinkOrNull_WhenInvalidSignature_ThenReturnsInvalidResultWithInvalidSignatureReason() {
        // When
        var withLink = LinkValidationResult.invalidSignature(mockLink);
        var withoutLink = LinkValidationResult.invalidSignature(null);

        // Then
        assertThat(withLink.valid()).isFalse();
        assertThat(withLink.failureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
        assertThat(withoutLink.link()).isNull();
        assertThat(withoutLink.failureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
    }

    @Test
    void GivenLinkAndValidity_WhenConstructed_ThenFieldsAreSetAsGiven() {
        // When
        var result = new LinkValidationResult(mockLink, false, ValidationFailureReason.EXPIRED);

        // Then
        assertThat(result.link()).isEqualTo(mockLink);
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ValidationFailureReason.EXPIRED);
    }

    @Test
    void GivenDifferentFactoryMethods_WhenComparingFailureReasons_ThenEachIsDistinct() {
        // When
        var notFoundResult = LinkValidationResult.notFound();
        var expiredResult = LinkValidationResult.expired(mockLink);
        var invalidSignatureResult = LinkValidationResult.invalidSignature(mockLink);

        // Then
        assertThat(notFoundResult.failureReason())
                .isNotEqualTo(expiredResult.failureReason())
                .isNotEqualTo(invalidSignatureResult.failureReason());
        assertThat(expiredResult.failureReason()).isNotEqualTo(invalidSignatureResult.failureReason());
    }
}
