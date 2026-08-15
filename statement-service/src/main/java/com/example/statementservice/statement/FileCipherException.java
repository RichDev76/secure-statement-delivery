package com.example.statementservice.statement;

public class FileCipherException extends RuntimeException {
    public FileCipherException(String message) {
        super(message);
    }

    public FileCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
