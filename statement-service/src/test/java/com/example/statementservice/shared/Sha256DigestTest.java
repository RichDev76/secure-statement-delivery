package com.example.statementservice.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256DigestTest {

    @Test
    void GivenKnownInput_WhenHashing_ThenWellKnownSha256HexIsProduced() {
        // Given: SHA-256("abc") is a published test vector
        var input = "abc".getBytes(StandardCharsets.UTF_8);

        // When
        var hex = Sha256Digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void GivenEmptyInput_WhenHashing_ThenEmptyInputSha256HexIsProduced() {
        // Given
        var input = new byte[0];

        // When
        var hex = Sha256Digest.hexOf(input);

        // Then
        assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void GivenAnyInput_WhenHashing_ThenHexIsLowercaseAnd64Characters() {
        // Given
        var input = "statement-content".getBytes(StandardCharsets.UTF_8);

        // When
        var hex = Sha256Digest.hexOf(input);

        // Then
        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
    }
}
