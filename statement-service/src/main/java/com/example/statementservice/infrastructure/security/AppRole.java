package com.example.statementservice.infrastructure.security;

// Constants, not an enum: @PreAuthorize needs a compile-time constant expression.
public final class AppRole {

    public static final String UPLOAD = "Upload";
    public static final String SEARCH = "Search";
    public static final String GENERATE_SIGNED_LINK = "GenerateSignedLink";
    public static final String AUDIT_LOGS_SEARCH = "AuditLogsSearch";

    private AppRole() {}
}
