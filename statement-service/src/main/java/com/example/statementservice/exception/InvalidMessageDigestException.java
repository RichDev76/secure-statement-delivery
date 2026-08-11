package com.example.statementservice.exception;

/**
 * Thrown when the X-Message-Digest header is missing or has an invalid format.
 */
public class InvalidMessageDigestException extends RuntimeException {
    public InvalidMessageDigestException(String message) {
        super(message);
    }
}
