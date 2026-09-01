package com.example.statementservice.statement.search;

// Parameters each individually valid but mutually inconsistent (e.g. startDate after endDate).
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
