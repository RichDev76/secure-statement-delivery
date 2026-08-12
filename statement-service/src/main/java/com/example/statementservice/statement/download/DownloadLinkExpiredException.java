package com.example.statementservice.statement.download;

public class DownloadLinkExpiredException extends RuntimeException {
    public DownloadLinkExpiredException(String message) {
        super(message);
    }
}
