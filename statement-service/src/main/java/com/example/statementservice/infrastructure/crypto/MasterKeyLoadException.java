package com.example.statementservice.infrastructure.crypto;

public class MasterKeyLoadException extends RuntimeException {
    public MasterKeyLoadException(String message) {
        super(message);
    }

    public MasterKeyLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
