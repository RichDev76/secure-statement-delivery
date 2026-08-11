package com.example.statementservice.model;

import lombok.Getter;

@Getter
public enum DownloadFailureReason {
    INVALID("invalid_link"),
    EXPIRED("expired_link"),
    USED("used_link"),
    STATEMENT_NOT_FOUND("statement_not_found"),
    FILE_MISSING("file_missing"),
    DECRYPTION_FAILED("decryption_failed");

    private final String value;

    DownloadFailureReason(String value) {
        this.value = value;
    }
}
