package com.example.statementservice.statement.upload;

public class InvalidMessageDigestException extends RuntimeException {
    public InvalidMessageDigestException(String message) {
        super(message);
    }
}
