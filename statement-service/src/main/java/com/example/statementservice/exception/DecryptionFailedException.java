package com.example.statementservice.exception;

public class DecryptionFailedException extends RuntimeException {
    public DecryptionFailedException(String message) {
        super(message);
    }
}
