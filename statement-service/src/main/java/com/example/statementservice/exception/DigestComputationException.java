package com.example.statementservice.exception;

public class DigestComputationException extends RuntimeException {
    public DigestComputationException(String message) {
        super(message);
    }

    public DigestComputationException(String message, Throwable cause) {
        super(message, cause);
    }
}
