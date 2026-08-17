package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.statementservice.statement.FileCipherException;
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
        var dek = cipher.generateDek();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(plaintext), ciphertextBytes, iv, dek);
        var decrypted = cipher.decrypt(ciphertextBytes.toByteArray(), dek);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void GivenEmptyPlaintext_WhenEncryptedThenDecrypted_ThenEmptyContentIsRecovered() throws IOException {
        // Given
        var iv = cipher.generateInitializationVector();
        var dek = cipher.generateDek();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(new byte[0]), ciphertextBytes, iv, dek);
        var decrypted = cipher.decrypt(ciphertextBytes.toByteArray(), dek);

        // Then
        assertThat(decrypted).isEmpty();
    }

    @Test
    void GivenLargePlaintext_WhenEncryptedThenDecrypted_ThenOriginalContentIsRecovered() throws IOException {
        // Given
        var plaintext = new byte[500_000];
        new SecureRandom().nextBytes(plaintext);
        var iv = cipher.generateInitializationVector();
        var dek = cipher.generateDek();
        var ciphertextBytes = new ByteArrayOutputStream();

        // When
        cipher.encrypt(new ByteArrayInputStream(plaintext), ciphertextBytes, iv, dek);
        var decrypted = cipher.decrypt(ciphertextBytes.toByteArray(), dek);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void GivenCiphertextShorterThanInitializationVector_WhenDecrypting_ThenThrowsFileCipherException() {
        // Given
        var tooShort = new byte[] {1, 2, 3};

        // When / Then
        assertThatThrownBy(() -> cipher.decrypt(tooShort, cipher.generateDek()))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("initialization vector");
    }

    @Test
    void GivenNullCiphertext_WhenDecrypting_ThenThrowsFileCipherException() {
        // When / Then
        assertThatThrownBy(() -> cipher.decrypt(null, cipher.generateDek()))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("initialization vector");
    }

    @Test
    void GivenTamperedCiphertextByte_WhenDecrypting_ThenThrowsFileCipherException() throws IOException {
        // Given
        var iv = cipher.generateInitializationVector();
        var dek = cipher.generateDek();
        var ciphertextBytes = new ByteArrayOutputStream();
        cipher.encrypt(new ByteArrayInputStream("some data".getBytes()), ciphertextBytes, iv, dek);
        var corrupted = ciphertextBytes.toByteArray();
        corrupted[corrupted.length - 1] ^= 0xFF;

        // When / Then: the GCM tag check must fail eagerly, before any plaintext is exposed
        assertThatThrownBy(() -> cipher.decrypt(corrupted, dek))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("integrity");
    }

    @Test
    void GivenFileEncryptedWithOneDek_WhenDecryptingWithDifferentDek_ThenThrowsFileCipherException()
            throws IOException {
        // Given
        var iv = cipher.generateInitializationVector();
        var dek = cipher.generateDek();
        var otherDek = cipher.generateDek();
        var ciphertextBytes = new ByteArrayOutputStream();
        cipher.encrypt(new ByteArrayInputStream("some data".getBytes()), ciphertextBytes, iv, dek);

        // When / Then
        assertThatThrownBy(() -> cipher.decrypt(ciphertextBytes.toByteArray(), otherDek))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("integrity");
    }

    @Test
    void GivenCipher_WhenGeneratingDek_ThenReturnsThirtyTwoBytes() {
        assertThat(cipher.generateDek()).hasSize(32);
    }

    @Test
    void GivenCipher_WhenGeneratingDekTwice_ThenValuesDiffer() {
        assertThat(cipher.generateDek()).isNotEqualTo(cipher.generateDek());
    }

    @Test
    void GivenDek_WhenWrapThenUnwrap_ThenOriginalDekIsRecovered() {
        // Given
        var dek = cipher.generateDek();

        // When
        var wrapped = cipher.wrapDek(dek);
        var unwrapped = cipher.unwrapDek(wrapped);

        // Then
        assertThat(unwrapped).isEqualTo(dek);
    }

    @Test
    void GivenDek_WhenWrapped_ThenWrappedFormIsNotTheRawDek() {
        // Given
        var dek = cipher.generateDek();

        // When
        var wrapped = cipher.wrapDek(dek);

        // Then
        assertThat(wrapped).isNotEqualTo(dek);
    }

    @Test
    void GivenSameDekWrappedTwice_WhenWrapDek_ThenProducesDifferentCiphertext() {
        // Given
        var dek = cipher.generateDek();

        // When
        var wrappedFirst = cipher.wrapDek(dek);
        var wrappedSecond = cipher.wrapDek(dek);

        // Then
        assertThat(wrappedFirst).isNotEqualTo(wrappedSecond);
        assertThat(cipher.unwrapDek(wrappedFirst)).isEqualTo(dek);
        assertThat(cipher.unwrapDek(wrappedSecond)).isEqualTo(dek);
    }

    @Test
    void GivenWrappedDekTamperedWithBitFlip_WhenUnwrapDek_ThenThrows() {
        // Given
        var wrapped = cipher.wrapDek(cipher.generateDek());
        wrapped[wrapped.length - 1] ^= 0xFF;

        // When / Then
        assertThatThrownBy(() -> cipher.unwrapDek(wrapped)).isInstanceOf(FileCipherException.class);
    }

    @Test
    void GivenUnrecognisedWrapFormat_WhenUnwrapDek_ThenThrows() {
        // Given
        var garbage = new byte[61];
        new SecureRandom().nextBytes(garbage);
        garbage[0] = 0x02;

        // When / Then
        assertThatThrownBy(() -> cipher.unwrapDek(garbage))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("Unrecognised DEK wrap format");
    }

    @Test
    void GivenTruncatedWrappedDek_WhenUnwrapDek_ThenThrows() {
        // Given
        var tooShort = new byte[] {0x01, 1, 2, 3};

        // When / Then
        assertThatThrownBy(() -> cipher.unwrapDek(tooShort))
                .isInstanceOf(FileCipherException.class)
                .hasMessageContaining("Unrecognised DEK wrap format");
    }
}
