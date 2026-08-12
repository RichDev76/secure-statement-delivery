package com.example.statementservice.statement.download;

public class DownloadInvalidSignatureException extends RuntimeException {
    public DownloadInvalidSignatureException(String message) {
        super(message);
    }
}
