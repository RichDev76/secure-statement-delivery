package com.example.statementservice.statement.search;

/**
 * Thrown when the provided date is missing or not in the expected format.
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
