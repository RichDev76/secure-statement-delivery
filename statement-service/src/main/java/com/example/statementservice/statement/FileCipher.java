package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;

public interface FileCipher {

    byte[] generateInitializationVector();

    byte[] generateDek();

    byte[] wrapDek(byte[] dek);

    byte[] unwrapDek(byte[] wrappedDek);

    InputStream encryptingStream(InputStream plaintext, byte[] initializationVector, byte[] dek) throws IOException;

    // IV and tag are fixed-size, so this is computable before encrypting a byte.
    long ciphertextLength(long plaintextLength);

    // Deliberately eager (byte[] in, byte[] out): a lazy stream would commit the HTTP 200 before
    // the GCM tag is verified, letting tampered ciphertext reach the client as a truncated body.
    byte[] decrypt(byte[] ciphertext, byte[] dek);
}
