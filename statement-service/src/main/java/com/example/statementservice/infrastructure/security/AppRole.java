package com.example.statementservice.infrastructure.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppRole {
    UPLOAD("Upload"),
    SEARCH("Search"),
    GENERATE_SIGNED_LINK("GenerateSignedLink"),
    AUDIT_LOGS_SEARCH("AuditLogsSearch");

    private final String roleName;
}
