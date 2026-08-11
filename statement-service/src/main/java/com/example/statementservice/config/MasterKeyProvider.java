package com.example.statementservice.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MasterKeyProvider {

    private final byte[] key;

    public MasterKeyProvider(
            @Value("${statement.encryption.master-key:}") String keyContent,
            @Value("${statement.encryption.master-key-file:/run/secrets/master-key}") String keyFile) {
        try {
            if (keyContent != null && !keyContent.trim().isEmpty()) {
                this.key = java.util.Base64.getDecoder().decode(keyContent.trim());
            } else {
                var p = Path.of(keyFile);
                if (!Files.exists(p)) {
                    throw new IllegalStateException("Master key file not found at: " + keyFile
                            + ". This is required if statement.encryption.master-key is not set.");
                }
                var content = Files.readAllBytes(p);
                var s = new String(content).trim();
                this.key = java.util.Base64.getDecoder().decode(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load master key", e);
        }
    }

    public byte[] getKey() {
        return Arrays.copyOf(key, key.length);
    }
}
