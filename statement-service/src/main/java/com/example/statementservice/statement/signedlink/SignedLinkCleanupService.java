package com.example.statementservice.statement.signedlink;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignedLinkCleanupService {

    private final SignedLinkRepository repository;
    private final SignedLinkCleanupProperties properties;
    private final SignedLinkRateLimiter rateLimiter;
    private final Clock clock;

    @Transactional
    public void cleanup() {
        if (!properties.isEnabled()) {
            log.debug("SignedLink cleanup is disabled");
            return;
        }

        var cutoff = OffsetDateTime.now(clock).minus(properties.getRetentionPeriod());

        int totalDeleted = 0;
        int deleted;

        do {
            deleted = repository.deleteExpired(cutoff, properties.getBatchSize());
            totalDeleted += deleted;
        } while (deleted == properties.getBatchSize());

        if (totalDeleted > 0) {
            log.info(
                    "SignedLink cleanup removed {} rows (cutoff={}, batchSize={})",
                    totalDeleted,
                    cutoff,
                    properties.getBatchSize());
        } else {
            log.info("SignedLink cleanup completed, no rows removed");
        }

        var expiredBuckets = rateLimiter.deleteExpiredBuckets();
        if (expiredBuckets > 0) {
            log.info("Rate limit bucket cleanup removed {} rows", expiredBuckets);
        }
    }
}
