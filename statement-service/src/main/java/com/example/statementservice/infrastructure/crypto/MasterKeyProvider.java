package com.example.statementservice.infrastructure.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MasterKeyProvider {

    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final byte[] key;

    public MasterKeyProvider(
            @Value("${statement.encryption.master-key:}") String keyContent,
            @Value("${statement.encryption.master-key-file:/run/secrets/master-key}") String keyFile) {
        byte[] decoded;
        try {
            if (keyContent != null && !keyContent.trim().isEmpty()) {
                decoded = java.util.Base64.getDecoder().decode(keyContent.trim());
            } else {
                var p = Path.of(keyFile);
                if (!Files.exists(p)) {
                    throw new IllegalStateException("Master key file not found at: " + keyFile
                            + ". This is required if statement.encryption.master-key is not set.");
                }
                var content = Files.readAllBytes(p);
                var s = new String(content, StandardCharsets.UTF_8).trim();
                decoded = java.util.Base64.getDecoder().decode(s);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new MasterKeyLoadException("Failed to load master key", e);
        }
        if (decoded.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new MasterKeyLoadException("Master key must be exactly " + REQUIRED_KEY_LENGTH_BYTES
                    + " bytes (AES-256) after Base64 decoding, but was " + decoded.length + " bytes");
        }
        this.key = decoded;
    }

    public byte[] getKey() {
        return Arrays.copyOf(key, key.length);
    }
}
