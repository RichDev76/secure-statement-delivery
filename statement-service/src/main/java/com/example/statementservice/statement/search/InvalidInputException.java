package com.example.statementservice.statement.search;

// Parameters that are each individually valid but mutually inconsistent (e.g. a date range where
// startDate is after endDate) - distinct from a single malformed field, which has its own
// exception types elsewhere in this package.
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
