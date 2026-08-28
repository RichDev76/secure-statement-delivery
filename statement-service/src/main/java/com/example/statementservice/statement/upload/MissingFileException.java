package com.example.statementservice.statement.upload;

public class MissingFileException extends RuntimeException {
    public MissingFileException(String message) {
        super(message);
    }
}
