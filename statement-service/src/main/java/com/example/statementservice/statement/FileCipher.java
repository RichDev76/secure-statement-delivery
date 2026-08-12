package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileCipher {

    byte[] generateInitializationVector();

    void encrypt(InputStream plaintext, OutputStream ciphertext, byte[] initializationVector) throws IOException;

    InputStream decrypt(InputStream ciphertext) throws IOException;
}
