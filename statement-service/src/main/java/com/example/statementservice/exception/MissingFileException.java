package com.example.statementservice.exception;

/**
 * Thrown when the uploaded file part is missing or empty.
 */
public class MissingFileException extends RuntimeException {
    public MissingFileException(String message) {
        super(message);
    }
}
