package com.example.statementservice.statement.signedlink;

public record LinkValidationResult(SignedLink link, boolean valid, ValidationFailureReason failureReason) {

    public static LinkValidationResult notFound() {
        return new LinkValidationResult(null, false, ValidationFailureReason.NOT_FOUND);
    }

    public static LinkValidationResult expired(SignedLink link) {
        return new LinkValidationResult(link, false, ValidationFailureReason.EXPIRED);
    }

    public static LinkValidationResult valid(SignedLink link) {
        return new LinkValidationResult(link, true, null);
    }

    public static LinkValidationResult invalidSignature(SignedLink link) {
        return new LinkValidationResult(link, false, ValidationFailureReason.INVALID_SIGNATURE);
    }
}
