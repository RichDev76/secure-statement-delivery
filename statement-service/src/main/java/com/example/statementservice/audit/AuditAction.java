package com.example.statementservice.audit;

import lombok.Getter;

@Getter
public enum AuditAction {
    DOWNLOAD_SUCCESS("DOWNLOAD_SUCCESS"),
    DOWNLOAD_FAILED("DOWNLOAD_FAILED"),
    UPLOAD_SUCCESS("UPLOAD_SUCCESS"),
    UPLOAD_FAILED("UPLOAD_FAILED"),
    LINK_GENERATED("LINK_GENERATED"),
    LINK_GENERATION_FAILED("LINK_GENERATION_FAILED");

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }
}
