package com.example.statementservice.statement.download;

public class DecryptionFailedException extends RuntimeException {
    public DecryptionFailedException(String message) {
        super(message);
    }
}
