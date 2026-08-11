package com.example.statementservice.exception;

public class StatementNotFoundException extends RuntimeException {
    public StatementNotFoundException(String message) {
        super(message);
    }
}
