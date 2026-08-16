package com.example.statementservice.statement.download;

import lombok.Getter;

@Getter
public enum DownloadFailureReason {
    INVALID("invalid_link"),
    EXPIRED("expired_link"),
    STATEMENT_NOT_FOUND("statement_not_found"),
    FILE_MISSING("file_missing"),
    DECRYPTION_FAILED("decryption_failed"),
    RATE_LIMITED("rate_limited");

    private final String value;

    DownloadFailureReason(String value) {
        this.value = value;
    }
}
