package com.example.statementservice.statement.upload;

/**
 * Thrown when the X-Message-Digest header is missing or has an invalid format.
 */
public class InvalidMessageDigestException extends RuntimeException {
    public InvalidMessageDigestException(String message) {
        super(message);
    }
}
