package com.example.statementservice.exception;

public class DownloadFileMissingException extends RuntimeException {
    public DownloadFileMissingException(String message) {
        super(message);
    }
}
