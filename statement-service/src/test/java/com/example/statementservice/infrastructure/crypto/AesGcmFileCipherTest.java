package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AesGcmFileCipherTest {

    @Mock
    private MasterKeyProvider masterKeyProvider;

    private AesGcmFileCipher cipher;

    @BeforeEach
    void setUp() {
        var key = new byte[32];
        new SecureRandom().nextBytes(key);
        when(masterKeyProvider.getKey()).thenReturn(key);
        cipher = new AesGcmFileCipher(masterKeyProvider);
    }

    @Test
    void GivenCipher_WhenGeneratingInitializationVector_ThenTwelveBytesAreProduced() {
        assertThat(cipher.generateInitializationVector()).hasSize(12);
    }

    @Test
    void GivenCipher_WhenGeneratingInitializationVectorTwice_ThenValuesDiffer() {
        assertThat(cipher.generateInitializationVector()).isNotEqualTo(cipher.generateInitializationVector());
    }

    @Test
    void GivenPlaintext_WhenEncryptedThenDecrypted_ThenOriginalContentIsRecovered() throws IOException {
        // Given
        var plaintext = "the quick brown fox jumps over the lazy dog".getBytes();
        var iv = cipher.generateInitializationVector();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(plaintext), ciphertextBytes, iv);
        var decrypted = cipher.decrypt(new ByteArrayInputStream(ciphertextBytes.toByteArray()))
                .readAllBytes();

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void GivenEmptyPlaintext_WhenEncryptedThenDecrypted_ThenEmptyContentIsRecovered() throws IOException {
        // Given
        var iv = cipher.generateInitializationVector();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(new byte[0]), ciphertextBytes, iv);
        var decrypted = cipher.decrypt(new ByteArrayInputStream(ciphertextBytes.toByteArray()))
                .readAllBytes();

        // Then
        assertThat(decrypted).isEmpty();
    }

    @Test
    void GivenLargePlaintext_WhenEncryptedThenDecrypted_ThenOriginalContentIsRecovered() throws IOException {
        // Given
        var plaintext = new byte[500_000];
        new SecureRandom().nextBytes(plaintext);
        var iv = cipher.generateInitializationVector();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(plaintext), ciphertextBytes, iv);
        var decrypted = cipher.decrypt(new ByteArrayInputStream(ciphertextBytes.toByteArray()))
                .readAllBytes();

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void GivenCiphertextShorterThanInitializationVector_WhenDecrypting_ThenIOExceptionIsThrown() {
        var tooShort = new ByteArrayInputStream(new byte[] {1, 2, 3});
        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("initialization vector");
    }

    @Test
    void GivenCorruptedCiphertext_WhenDecryptingAndReading_ThenIOExceptionIsThrown() throws IOException {
        // Given
        var iv = cipher.generateInitializationVector();
        var ciphertextBytes = new ByteArrayOutputStream();
        cipher.encrypt(new ByteArrayInputStream("some data".getBytes()), ciphertextBytes, iv);
        var corrupted = ciphertextBytes.toByteArray();
        corrupted[corrupted.length - 1] ^= 0xFF;

        // When
        var decryptStream = cipher.decrypt(new ByteArrayInputStream(corrupted));

        // Then
        assertThatThrownBy(decryptStream::readAllBytes).isInstanceOf(IOException.class);
    }
}
