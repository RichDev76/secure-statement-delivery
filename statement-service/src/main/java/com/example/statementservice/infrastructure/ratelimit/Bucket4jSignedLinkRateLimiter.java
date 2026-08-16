package com.example.statementservice.infrastructure.ratelimit;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import com.example.statementservice.statement.signedlink.SignedLinkRateLimiterPort;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Smooths burst speed against one leaked link (thundering-herd on one S3 key, one redemption
// cap being drained instantly) - it does not reduce total exposure from a leaked link, only
// how fast that exposure can be consumed. Checked before signature validation in DownloadService,
// so a signature-guessing flood against a known real linkId is throttled too, not just genuinely
// valid requests.
@Slf4j
@Component
@RequiredArgsConstructor
public class Bucket4jSignedLinkRateLimiter implements SignedLinkRateLimiterPort {

    private static final String TABLE_NAME = "signed_link_rate_limit_buckets";

    private final ProxyManager<String> proxyManager;
    private final SignedLinkProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryConsume(UUID linkId) {
        try {
            var bucket = proxyManager.getProxy(linkId.toString(), this::configuration);
            return bucket.tryConsume(1);
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, failing open for linkId={}", linkId, e);
            return true;
        }
    }

    // No FK/cascade to signed_links is possible here (Bucket4j's primary key column is text,
    // signed_links.id is uuid - Postgres foreign keys require matching column types), so this
    // table needs its own sweep. Reuses SignedLinkCleanupService's existing ShedLock-scheduled
    // trigger rather than adding a new scheduled job for one extra DELETE.
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
