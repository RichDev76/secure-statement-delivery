package com.example.statementservice.statement.download;

import java.util.Objects;

// The callback's product crosses this boundary - the raw stream never leaves DownloadService.
public record DownloadAttempt<T>(DownloadOutcome outcome, T result) {

    public DownloadAttempt {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public static <T> DownloadAttempt<T> success(T result) {
        return new DownloadAttempt<>(DownloadOutcome.OK, Objects.requireNonNull(result, "result must not be null"));
    }

    public static <T> DownloadAttempt<T> failure(DownloadOutcome outcome) {
        return new DownloadAttempt<>(outcome, null);
    }
}
