package com.example.statementservice.shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class Sha256Digest {

    private static final String ALGORITHM = "SHA-256";

    private Sha256Digest() {}

    public static String hexOf(byte[] input) {
        try {
            var hash = MessageDigest.getInstance(ALGORITHM).digest(input);
            var hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
