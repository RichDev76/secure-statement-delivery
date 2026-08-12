package com.example.statementservice.shared;

/**
 * Thrown when the provided date is missing or not in the expected format.
 */
public class InvalidDateException extends RuntimeException {
    public InvalidDateException(String message) {
        super(message);
    }

    public InvalidDateException(String message, Throwable cause) {
        super(message, cause);
    }
}
