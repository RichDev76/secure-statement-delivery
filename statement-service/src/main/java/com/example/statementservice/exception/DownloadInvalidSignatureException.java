package com.example.statementservice.exception;

public class DownloadInvalidSignatureException extends RuntimeException {
    public DownloadInvalidSignatureException(String message) {
        super(message);
    }
}
