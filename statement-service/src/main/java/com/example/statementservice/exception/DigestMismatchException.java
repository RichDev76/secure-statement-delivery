package com.example.statementservice.exception;

public class DigestMismatchException extends RuntimeException {
    public DigestMismatchException(String message) {
        super(message);
    }

    public DigestMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
