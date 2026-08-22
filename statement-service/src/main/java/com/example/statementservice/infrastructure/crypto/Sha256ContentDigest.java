package com.example.statementservice.infrastructure.crypto;

import com.example.statementservice.shared.ContentDigest;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Sha256ContentDigest implements ContentDigest {

    private static final String ALGORITHM = "SHA-256";
    private static final int STREAM_BUFFER_SIZE = 8192;

    @Override
    public String hexOf(byte[] content) {
        return toHex(newDigest().digest(content));
    }

    @Override
    public String hexOf(InputStream content) throws IOException {
        var digest = newDigest();
        var buffer = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = content.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] hash) {
        var hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
