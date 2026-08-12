package com.example.statementservice.statement.download;

public class DownloadFileMissingException extends RuntimeException {
    public DownloadFileMissingException(String message) {
        super(message);
    }
}
