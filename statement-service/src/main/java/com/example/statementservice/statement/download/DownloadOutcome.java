package com.example.statementservice.statement.download;

public enum DownloadOutcome {
    OK,
    INVALID_SIGNATURE,
    LINK_EXPIRED_OR_USED,
    STATEMENT_NOT_FOUND,
    FILE_MISSING,
    DECRYPTION_FAILED
}
