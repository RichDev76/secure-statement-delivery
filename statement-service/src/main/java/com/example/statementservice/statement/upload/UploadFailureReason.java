package com.example.statementservice.statement.upload;

import lombok.Getter;

@Getter
public enum UploadFailureReason {
    VALIDATION_FAILED("validation_failed"),
    DIGEST_MISMATCH("digest_mismatch"),
    UNSUPPORTED_MEDIA_TYPE("unsupported_media_type"),
    UPLOAD_ERROR("upload_error"),
    UNEXPECTED("unexpected");

    private final String value;

    UploadFailureReason(String value) {
        this.value = value;
    }
}
