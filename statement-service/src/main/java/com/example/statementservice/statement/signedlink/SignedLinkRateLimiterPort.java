package com.example.statementservice.statement.signedlink;

import java.util.UUID;

public interface SignedLinkRateLimiterPort {

    boolean tryConsume(UUID linkId);

    int deleteExpiredBuckets();
}
