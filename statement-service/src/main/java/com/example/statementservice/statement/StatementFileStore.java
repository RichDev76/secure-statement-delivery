package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.UUID;

public interface StatementFileStore {

    String store(UUID id, String accountNumber, LocalDate statementDate, ContentWriter writer) throws IOException;

    InputStream open(String reference) throws IOException;

    boolean exists(String reference);

    @FunctionalInterface
    interface ContentWriter {
        void writeTo(OutputStream out) throws IOException;
    }
}
