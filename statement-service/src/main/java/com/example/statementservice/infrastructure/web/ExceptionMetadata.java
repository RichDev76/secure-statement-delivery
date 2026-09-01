package com.example.statementservice.infrastructure.web;

import org.springframework.http.HttpStatus;

// Title/errorCode/status tuple looked up by each feature's @RestControllerAdvice.
public record ExceptionMetadata(String title, String errorCode, HttpStatus status) {

    // All-BAD_REQUEST exception types (the common case) don't need to repeat the status.
    public ExceptionMetadata(String title, String errorCode) {
        this(title, errorCode, HttpStatus.BAD_REQUEST);
    }
}
