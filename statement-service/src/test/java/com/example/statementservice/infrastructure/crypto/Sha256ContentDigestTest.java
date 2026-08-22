package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class Sha256ContentDigestTest {

    private final Sha256ContentDigest digest = new Sha256ContentDigest();

    @Test
    void GivenKnownInput_WhenHashing_ThenWellKnownSha256HexIsProduced() {
        // Given: SHA-256("abc") is a published test vector
        var input = "abc".getBytes(StandardCharsets.UTF_8);

        // When
        var hex = digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void GivenEmptyInput_WhenHashing_ThenEmptyInputSha256HexIsProduced() {
        // Given
        var input = new byte[0];

        // When
        var hex = digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void GivenKnownInputAsStream_WhenHashing_ThenWellKnownSha256HexIsProduced() throws IOException {
        // Given: SHA-256("abc") is a published test vector
        var input = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));

        // When
        var hex = digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void GivenEmptyStream_WhenHashing_ThenEmptyInputSha256HexIsProduced() throws IOException {
        // Given
        var input = new ByteArrayInputStream(new byte[0]);

        // When
        var hex = digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void GivenContentLargerThanInternalBuffer_WhenHashingAsStream_ThenMatchesByteArrayOverload() throws IOException {
        // Given: content spanning multiple 8KB read chunks
        var content = new byte[64 * 1024 + 17];
        ThreadLocalRandom.current().nextBytes(content);

        // When
        var streamedHex = digest.hexOf(new ByteArrayInputStream(content));

        // Then
        assertThat(streamedHex).isEqualTo(digest.hexOf(content));
    }

    @Test
    void GivenAnyInput_WhenHashing_ThenHexIsLowercaseAnd64Characters() {
        // Given
        var input = "statement-content".getBytes(StandardCharsets.UTF_8);

        // When
        var hex = digest.hexOf(input);

        // Then
        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
    }
}
