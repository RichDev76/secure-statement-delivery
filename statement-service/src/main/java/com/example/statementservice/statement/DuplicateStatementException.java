package com.example.statementservice.statement;

public class DuplicateStatementException extends RuntimeException {

    public DuplicateStatementException(String message) {
        super(message);
    }

    public DuplicateStatementException(String message, Throwable cause) {
        super(message, cause);
    }
}
