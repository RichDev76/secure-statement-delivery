package com.example.statementservice.infrastructure.ratelimit;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Checked before signature validation so a guessing flood against a known linkId is throttled too.
@Slf4j
@Component
@RequiredArgsConstructor
public class Bucket4jSignedLinkRateLimiter implements SignedLinkRateLimiter {

    static final String TABLE_NAME = "signed_link_rate_limit_buckets";

    private final ProxyManager<String> proxyManager;
    private final SignedLinkProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryConsume(UUID linkId) {
        try {
            var bucket = proxyManager.getProxy(linkId.toString(), this::configuration);
            return bucket.tryConsume(1);
        } catch (Exception e) {
            // Fail closed: sole abuse control on an unauthenticated endpoint.
            log.error("Rate limiter unavailable, failing closed for linkId={}", linkId, e);
            return false;
        }
    }

    // No FK possible (text vs uuid PK) - reuses SignedLinkCleanupService's ShedLock trigger.
    @Override
    public int deleteExpiredBuckets() {
        return jdbcTemplate.update(
                "DELETE FROM " + TABLE_NAME + " WHERE expires_at IS NOT NULL AND expires_at < ?",
                System.currentTimeMillis());
    }

    private BucketConfiguration configuration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(properties.getRateLimitPerMinute(), Duration.ofMinutes(1)))
                .build();
    }
}
