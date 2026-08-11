package com.example.statementservice.util;

import com.example.statementservice.model.ValidationFailureReason;
import com.example.statementservice.model.entity.SignedLink;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class LinkValidationResult {
    private final SignedLink link;
    private final boolean valid;
    private final ValidationFailureReason failureReason;

    public static LinkValidationResult notFound() {
        return new LinkValidationResult(null, false, ValidationFailureReason.NOT_FOUND);
    }

    public static LinkValidationResult used(SignedLink link) {
        return new LinkValidationResult(link, false, ValidationFailureReason.USED);
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
