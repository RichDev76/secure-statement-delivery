package com.example.statementservice.statement;

public class StatementStorageUnavailableException extends RuntimeException {
    public StatementStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
