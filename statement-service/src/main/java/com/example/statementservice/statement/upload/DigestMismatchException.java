package com.example.statementservice.statement.upload;

public class DigestMismatchException extends RuntimeException {
    public DigestMismatchException(String message) {
        super(message);
    }

    public DigestMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
