package com.example.statementservice.infrastructure.web;

/** Reduces a request URI to a bounded, non-identifying endpoint label. */
public final class EndpointLabel {

    // /download/{fileName} carries a caller value; segment depth alone can't detect it.
    private static final String SENSITIVE_SEGMENT = "/download/";

    private EndpointLabel() {}

    public static String of(String requestUri) {
        var idx = requestUri.indexOf(SENSITIVE_SEGMENT);
        return idx == -1 ? requestUri : requestUri.substring(0, idx + SENSITIVE_SEGMENT.length() - 1);
    }
}
