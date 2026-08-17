package com.example.statementservice.shared;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class Sha256Digest {

    private static final String ALGORITHM = "SHA-256";
    private static final int STREAM_BUFFER_SIZE = 8192;

    private Sha256Digest() {}

    public static String hexOf(byte[] input) {
        return toHex(newDigest().digest(input));
    }

    // Caller owns and closes the stream.
    public static String hexOf(InputStream input) throws IOException {
        var digest = newDigest();
        var buffer = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
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
