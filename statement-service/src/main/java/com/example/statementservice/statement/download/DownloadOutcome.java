package com.example.statementservice.statement.download;

public enum DownloadOutcome {
    OK,
    INVALID_SIGNATURE,
    LINK_EXPIRED,
    STATEMENT_NOT_FOUND,
    FILE_MISSING,
    DECRYPTION_FAILED,
    RATE_LIMITED
}
