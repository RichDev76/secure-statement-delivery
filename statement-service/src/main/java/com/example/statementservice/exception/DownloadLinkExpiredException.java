package com.example.statementservice.exception;

public class DownloadLinkExpiredException extends RuntimeException {
    public DownloadLinkExpiredException(String message) {
        super(message);
    }
}
