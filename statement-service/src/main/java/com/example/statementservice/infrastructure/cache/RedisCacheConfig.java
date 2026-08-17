package com.example.statementservice.infrastructure.cache;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisCacheConfig implements CachingConfigurer {

    public static final String STATEMENT_CIPHERTEXT_CACHE = "statementCiphertext";

    private final SignedLinkProperties signedLinkProperties;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // TTL matched to the signed-link expiry: bounded, self-evicting, no manual cleanup job -
        // an entry is never worth more than the link that could still be redeeming it.
        var cacheConfig = RedisCacheConfiguration.defaultCacheConfig().entryTtl(signedLinkProperties.getExpiry());
        // Since Spring Data Redis 4.0, writes are deferred/async by default against a reactive-capable
        // connection factory (Lettuce always is) - a put() can return before Redis has acknowledged it,
        // so an immediately-following get() for the same key can race and miss. immediateWrites() opts
        // back into blocking writes, which is required here: caching only pays off if a redemption of
        // the same link moments later reliably hits.
        var cacheWriter = RedisCacheWriter.create(connectionFactory, config -> config.immediateWrites(true));
        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfig)
                .withCacheConfiguration(STATEMENT_CIPHERTEXT_CACHE, cacheConfig)
                .build();
    }

    // Spring's default SimpleCacheErrorHandler rethrows, which would fail the download on a
    // Redis outage instead of falling through to S3.
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(true);
    }
}
