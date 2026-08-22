package com.example.statementservice.statement.signedlink;

import java.util.UUID;

public interface SignedLinkRateLimiter {

    boolean tryConsume(UUID linkId);

    int deleteExpiredBuckets();
}
