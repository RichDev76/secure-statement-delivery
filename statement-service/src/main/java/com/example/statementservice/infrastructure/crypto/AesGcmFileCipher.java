package com.example.statementservice.infrastructure.crypto;

import com.example.statementservice.statement.FileCipher;
import com.example.statementservice.statement.FileCipherException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AesGcmFileCipher implements FileCipher {

    private static final String ALGORITHM_AES = "AES";
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int INITIALIZATION_VECTOR_LENGTH = 12;
    private static final int DEK_LENGTH = 32;
    private static final byte DEK_WRAP_VERSION_GCM = 0x01;

    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom random = new SecureRandom();

    public AesGcmFileCipher(MasterKeyProvider masterKeyProvider) {
        this.masterKeyProvider = masterKeyProvider;
    }

    @Override
    public byte[] generateInitializationVector() {
        var initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
        random.nextBytes(initializationVector);
        return initializationVector;
    }

    @Override
    public byte[] generateDek() {
        var dek = new byte[DEK_LENGTH];
        random.nextBytes(dek);
        return dek;
    }

    @Override
    public byte[] wrapDek(byte[] dek) {
        var iv = generateInitializationVector();
        try {
            var cipher = Cipher.getInstance(ALGO);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            var ciphertext = cipher.doFinal(dek);
            var wrapped = new byte[1 + iv.length + ciphertext.length];
            wrapped[0] = DEK_WRAP_VERSION_GCM;
            System.arraycopy(iv, 0, wrapped, 1, iv.length);
            System.arraycopy(ciphertext, 0, wrapped, 1 + iv.length, ciphertext.length);
            return wrapped;
        } catch (GeneralSecurityException e) {
            throw new FileCipherException("Failed to wrap DEK", e);
        }
    }

    @Override
    public byte[] unwrapDek(byte[] wrapped) {
        var minLength = 1 + INITIALIZATION_VECTOR_LENGTH;
        if (wrapped == null || wrapped.length <= minLength || wrapped[0] != DEK_WRAP_VERSION_GCM) {
            throw new FileCipherException("Unrecognised DEK wrap format");
        }
        var iv = Arrays.copyOfRange(wrapped, 1, minLength);
        var ciphertext = Arrays.copyOfRange(wrapped, minLength, wrapped.length);
        try {
            var cipher = Cipher.getInstance(ALGO);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new FileCipherException("Failed to unwrap DEK", e);
        }
    }

    @Override
    public InputStream encryptingStream(InputStream plaintext, byte[] initializationVector, byte[] dek)
            throws IOException {
        try {
            var secretKeySpec = new SecretKeySpec(dek, ALGORITHM_AES);
            var cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector));
            return new SequenceInputStream(
                    new ByteArrayInputStream(initializationVector), new CipherInputStream(plaintext, cipher));
        } catch (GeneralSecurityException e) {
            closeQuietly(plaintext);
            throw new IOException("Encryption failed", e);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public long ciphertextLength(long plaintextLength) {
        return INITIALIZATION_VECTOR_LENGTH + plaintextLength + (GCM_TAG_LENGTH / 8);
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] dek) {
        if (ciphertext == null || ciphertext.length < INITIALIZATION_VECTOR_LENGTH) {
            throw new FileCipherException("Invalid encrypted file format: initialization vector missing");
        }
        var initializationVector = Arrays.copyOfRange(ciphertext, 0, INITIALIZATION_VECTOR_LENGTH);
        try {
            var keySpec = new SecretKeySpec(dek, ALGORITHM_AES);
            var cipher = Cipher.getInstance(ALGO);
            var spec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return cipher.doFinal(
                    ciphertext, INITIALIZATION_VECTOR_LENGTH, ciphertext.length - INITIALIZATION_VECTOR_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new FileCipherException("Ciphertext integrity check failed", e);
        }
    }
}
