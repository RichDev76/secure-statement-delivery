package com.example.statementservice.exception;

/**
 * Thrown when the uploaded file has an unsupported content type.
 */
public class UnsupportedContentTypeException extends RuntimeException {
    public UnsupportedContentTypeException(String message) {
        super(message);
    }
}
