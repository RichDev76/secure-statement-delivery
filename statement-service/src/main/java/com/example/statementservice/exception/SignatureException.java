package com.example.statementservice.exception;

public class SignatureException extends RuntimeException {
    public SignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
