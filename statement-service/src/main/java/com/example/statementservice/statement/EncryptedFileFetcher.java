package com.example.statementservice.statement;

import java.io.IOException;

public interface EncryptedFileFetcher {

    byte[] fetch(String storageKey) throws IOException;
}
