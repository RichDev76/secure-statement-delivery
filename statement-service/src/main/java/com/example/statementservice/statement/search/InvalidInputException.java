package com.example.statementservice.statement.search;

/**
 * Thrown when search parameters are individually valid but mutually inconsistent (e.g. a date range
 * where startDate is after endDate).
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
