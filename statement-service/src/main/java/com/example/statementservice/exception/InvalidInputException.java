package com.example.statementservice.exception;

/**
 * Thrown when the provided date is missing or not in the expected format.
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
