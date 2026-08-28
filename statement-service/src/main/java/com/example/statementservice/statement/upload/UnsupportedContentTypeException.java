package com.example.statementservice.statement.upload;

public class UnsupportedContentTypeException extends RuntimeException {
    public UnsupportedContentTypeException(String message) {
        super(message);
    }
}
