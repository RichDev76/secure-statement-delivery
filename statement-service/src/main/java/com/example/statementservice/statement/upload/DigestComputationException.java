package com.example.statementservice.statement.upload;

public class DigestComputationException extends RuntimeException {
    public DigestComputationException(String message) {
        super(message);
    }

    public DigestComputationException(String message, Throwable cause) {
        super(message, cause);
    }
}
