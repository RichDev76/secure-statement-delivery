package com.example.statementservice.statement.upload;

/**
 * Thrown when the uploaded file has an unsupported content type.
 */
public class UnsupportedContentTypeException extends RuntimeException {
    public UnsupportedContentTypeException(String message) {
        super(message);
    }
}
