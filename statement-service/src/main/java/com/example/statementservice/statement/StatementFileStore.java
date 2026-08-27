package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

public interface StatementFileStore {

    String store(UUID id, String accountNumber, LocalDate statementDate, long contentLength, StreamSupplier content)
            throws IOException;

    InputStream open(String reference) throws IOException;

    boolean exists(String reference);

    void delete(String reference) throws IOException;

    @FunctionalInterface
    interface StreamSupplier {
        // Must be callable more than once, returning a fresh stream from position 0 each time.
        InputStream openStream() throws IOException;
    }
}
