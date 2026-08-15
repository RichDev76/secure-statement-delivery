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

    InputStream decrypt(InputStream ciphertext, byte[] dek) throws IOException;
}
