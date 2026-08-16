package com.example.statementservice.infrastructure.ratelimit;

import io.github.bucket4j.distributed.expiration.FixedTtlExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    private static final String TABLE_NAME = "signed_link_rate_limit_buckets";

    // Matches the bucket's own refill window, so every row is stale by the time
    // Bucket4jSignedLinkRateLimiter#deleteExpiredBuckets sweeps it.
    private static final Duration BUCKET_STATE_TTL = Duration.ofMinutes(1);

    @Bean
    public ProxyManager<String> signedLinkRateLimitProxyManager(DataSource dataSource) {
        return Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table(TABLE_NAME)
                .expirationAfterWrite(new FixedTtlExpirationAfterWriteStrategy(BUCKET_STATE_TTL))
                .build();
    }
}
