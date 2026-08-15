package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statementservice.statement.signedlink.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HmacSha256LinkSigner Tests")
class HmacSha256LinkSignerTest {

    private static final String PATH = "/api/v1/statements/download/statement.pdf";
    private static final long EXPIRES = 1234567890L;
    private static final String METHOD = "GET";
    private static final String NONCE = "0198f2b0-7a1e-7c33-8b1a-3fd41e9c2b77";

    private HmacSha256LinkSigner signer;

    @BeforeEach
    void setUp() {
        signer = new HmacSha256LinkSigner("test-secret-key-12345");
    }

    @Test
    void GivenValidInputs_WhenSign_ThenGeneratesBase64UrlEncodedSignature() {
        // When
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(signature).isNotEmpty();
        assertThat(signature).doesNotContain("+", "/").doesNotEndWith("=");
    }

    @Test
    void GivenSameInputs_WhenSignTwice_ThenProducesIdenticalSignatures() {
        // When
        var first = signer.sign(PATH, EXPIRES, METHOD, NONCE);
        var second = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(first).isEqualTo(second);
    }

    @Test
    void GivenDifferentPaths_WhenSign_ThenProducesDifferentSignatures() {
        // When
        var first = signer.sign("/a", EXPIRES, METHOD, NONCE);
        var second = signer.sign("/b", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GivenDifferentExpires_WhenSign_ThenProducesDifferentSignatures() {
        // When
        var first = signer.sign(PATH, EXPIRES, METHOD, NONCE);
        var second = signer.sign(PATH, EXPIRES + 1, METHOD, NONCE);

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GivenDifferentMethods_WhenSign_ThenProducesDifferentSignatures() {
        // When
        var first = signer.sign(PATH, EXPIRES, "GET", NONCE);
        var second = signer.sign(PATH, EXPIRES, "POST", NONCE);

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GivenDifferentSecrets_WhenSign_ThenProducesDifferentSignatures() {
        // Given
        var other = new HmacSha256LinkSigner("a-different-secret");

        // When
        var first = signer.sign(PATH, EXPIRES, METHOD, NONCE);
        var second = other.sign(PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GivenNonEmptyNonce_WhenSign_ThenProducesDifferentSignatureThanEmptyNonce() {
        // When
        var withNonce = signer.sign(PATH, EXPIRES, METHOD, NONCE);
        var withoutNonce = signer.sign(PATH, EXPIRES, METHOD, "");

        // Then
        assertThat(withNonce).isNotEqualTo(withoutNonce);
    }

    @Test
    void GivenDifferentNonces_WhenSign_ThenProducesDifferentSignatures() {
        // When
        var first = signer.sign(PATH, EXPIRES, METHOD, "nonce-one");
        var second = signer.sign(PATH, EXPIRES, METHOD, "nonce-two");

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GivenEmptyPath_WhenSign_ThenGeneratesSignature() {
        // When
        var signature = signer.sign("", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(signature).isNotEmpty();
    }

    @Test
    void GivenSpecialCharactersInPath_WhenSign_ThenGeneratesSignature() {
        // When
        var signature = signer.sign("/api/statements/file%20name.pdf?a=1&b=2", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(signature).isNotEmpty();
    }

    @Test
    void GivenUnicodeCharactersInPath_WhenSign_ThenGeneratesSignature() {
        // When
        var signature = signer.sign("/api/statements/文件名.pdf", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(signature).isNotEmpty();
    }

    @Test
    void GivenPipeCharacterInPath_WhenSign_ThenGeneratesSignature() {
        // When
        var signature = signer.sign("/api/statements/file|with|pipes.pdf", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(signature).isNotEmpty();
    }

    @Test
    void GivenDifferentPathLengths_WhenSign_ThenProducesConsistentLengthSignatures() {
        // When
        var short_ = signer.sign("/short", EXPIRES, METHOD, NONCE);
        var long_ = signer.sign("/very/long/path/with/many/segments/and/parameters", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(short_.length()).isEqualTo(long_.length());
    }

    @Test
    void GivenEmptySecret_WhenSign_ThenThrowsSignatureException() {
        // Given
        var emptySecretSigner = new HmacSha256LinkSigner("");

        // When / Then
        assertThatThrownBy(() -> emptySecretSigner.sign(PATH, EXPIRES, METHOD, NONCE))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void GivenMatchingSignature_WhenVerify_ThenReturnsTrue() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // When
        var result = signer.verify(signature, PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void GivenTamperedSignature_WhenVerify_ThenReturnsFalse() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);
        var tampered = signature.substring(0, signature.length() - 1) + (signature.endsWith("A") ? "B" : "A");

        // When
        var result = signer.verify(tampered, PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenWrongNonce_WhenVerify_ThenReturnsFalse() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // When
        var result = signer.verify(signature, PATH, EXPIRES, METHOD, "a-different-nonce");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenWrongPath_WhenVerify_ThenReturnsFalse() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // When
        var result = signer.verify(signature, "/a-different-path", EXPIRES, METHOD, NONCE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenWrongExpires_WhenVerify_ThenReturnsFalse() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // When
        var result = signer.verify(signature, PATH, EXPIRES + 1, METHOD, NONCE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenWrongMethod_WhenVerify_ThenReturnsFalse() {
        // Given
        var signature = signer.sign(PATH, EXPIRES, METHOD, NONCE);

        // When
        var result = signer.verify(signature, PATH, EXPIRES, "POST", NONCE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenNullSignature_WhenVerify_ThenReturnsFalse() {
        // When
        var result = signer.verify(null, PATH, EXPIRES, METHOD, NONCE);

        // Then
        assertThat(result).isFalse();
    }
}
