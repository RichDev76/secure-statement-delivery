package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterKeyProviderTest {

    private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef".getBytes();
    private static final String KEY_BASE64 = Base64.getEncoder().encodeToString(KEY_BYTES);

    @TempDir
    private Path tempDir;

    @Test
    void GivenBase64KeyProperty_WhenConstructing_ThenKeyIsDecodedFromProperty() {
        // Given / When: the file path does not exist, proving the property takes precedence
        var provider =
                new MasterKeyProvider(KEY_BASE64, tempDir.resolve("absent").toString());

        // Then
        assertThat(provider.getKey()).isEqualTo(KEY_BYTES);
    }

    @Test
    void GivenBlankPropertyAndExistingKeyFile_WhenConstructing_ThenKeyIsReadFromFile() throws Exception {
        // Given
        var keyFile = tempDir.resolve("master-key");
        Files.writeString(keyFile, KEY_BASE64 + "\n");

        // When
        var provider = new MasterKeyProvider("  ", keyFile.toString());

        // Then
        assertThat(provider.getKey()).isEqualTo(KEY_BYTES);
    }

    @Test
    void GivenBlankPropertyAndMissingKeyFile_WhenConstructing_ThenFailsWithMasterKeyError() {
        // Given
        var missingFile = tempDir.resolve("absent").toString();

        // When / Then
        assertThatThrownBy(() -> new MasterKeyProvider("", missingFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load master key")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenInvalidBase64Property_WhenConstructing_ThenFailsWithMasterKeyError() {
        // Given
        var notBase64 = "!!!not-base64!!!";

        // When / Then
        assertThatThrownBy(() -> new MasterKeyProvider(
                        notBase64, tempDir.resolve("absent").toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load master key");
    }

    @Test
    void GivenSixteenByteKey_WhenConstructing_ThenFailsNamingRequiredKeyLength() {
        // Given
        var shortKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

        // When / Then
        assertThatThrownBy(() -> new MasterKeyProvider(
                        shortKey, tempDir.resolve("absent").toString()))
                .isInstanceOf(MasterKeyLoadException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void GivenSixteenByteKeyInFile_WhenConstructing_ThenFailsNamingRequiredKeyLength() throws Exception {
        // Given
        var keyFile = tempDir.resolve("master-key");
        Files.writeString(keyFile, Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()));

        // When / Then
        assertThatThrownBy(() -> new MasterKeyProvider("", keyFile.toString()))
                .isInstanceOf(MasterKeyLoadException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void GivenConstructedProvider_WhenMutatingReturnedKey_ThenSubsequentGetKeyIsUnaffected() {
        // Given
        var provider =
                new MasterKeyProvider(KEY_BASE64, tempDir.resolve("absent").toString());

        // When
        var leaked = provider.getKey();
        leaked[0] = (byte) ~leaked[0];

        // Then
        assertThat(provider.getKey()).isEqualTo(KEY_BYTES);
    }
}
