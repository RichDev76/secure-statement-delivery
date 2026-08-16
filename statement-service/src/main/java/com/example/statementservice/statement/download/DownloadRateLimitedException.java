package com.example.statementservice.statement.download;

public class DownloadRateLimitedException extends RuntimeException {
    public DownloadRateLimitedException(String message) {
        super(message);
    }
}
