package com.example.statementservice.exception;

/**
 * Thrown when the service fails to upload and persist a statement due to IO/crypto/persistence errors.
 */
public class StatementUploadException extends RuntimeException {
    public StatementUploadException(String message) {
        super(message);
    }

    public StatementUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
