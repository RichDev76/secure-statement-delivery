package com.example.statementservice.infrastructure.crypto;

import com.example.statementservice.statement.FileCipher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AesGcmFileCipher implements FileCipher {

    private static final String ALGORITHM_AES = "AES";
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int INITIALIZATION_VECTOR_LENGTH = 12;

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
    public void encrypt(InputStream plaintext, OutputStream ciphertext, byte[] initializationVector)
            throws IOException {
        try {
            var secretKeySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
            var cipher = Cipher.getInstance(ALGO);
            var gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);
            ciphertext.write(initializationVector);
            try (var cipherOutputStream = new CipherOutputStream(ciphertext, cipher)) {
                plaintext.transferTo(cipherOutputStream);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Encryption failed", e);
        }
    }

    @Override
    public InputStream decrypt(InputStream ciphertext) throws IOException {
        var initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
        int read = ciphertext.readNBytes(initializationVector, 0, INITIALIZATION_VECTOR_LENGTH);
        if (read != INITIALIZATION_VECTOR_LENGTH) {
            ciphertext.close();
            throw new IOException("Invalid encrypted file format: initialization vector missing");
        }
        try {
            var keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
            var cipher = Cipher.getInstance(ALGO);
            var spec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return new CipherInputStream(ciphertext, cipher);
        } catch (Exception e) {
            throw new IOException("Decryption failed", e);
        }
    }
}
