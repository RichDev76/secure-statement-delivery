package com.example.statementservice.statement.download;

public class DownloadStorageUnavailableException extends RuntimeException {
    public DownloadStorageUnavailableException(String message) {
        super(message);
    }
}
