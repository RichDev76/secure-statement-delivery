package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileCipher {

    byte[] generateInitializationVector();

    byte[] generateDek();

    byte[] wrapDek(byte[] dek);

    byte[] unwrapDek(byte[] wrappedDek);

    void encrypt(InputStream plaintext, OutputStream ciphertext, byte[] initializationVector, byte[] dek)
            throws IOException;

    // Deliberately eager (byte[] in, byte[] out): a lazy stream would commit the HTTP 200 before
    // the GCM tag is verified, letting tampered ciphertext reach the client as a truncated body.
    byte[] decrypt(byte[] ciphertext, byte[] dek);
}
