package com.example.statementservice.statement;

public class StatementUploadException extends RuntimeException {
    public StatementUploadException(String message) {
        super(message);
    }

    public StatementUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
