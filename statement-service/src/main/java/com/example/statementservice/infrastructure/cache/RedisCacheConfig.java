package com.example.statementservice.infrastructure.cache;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisCacheConfig {

    public static final String STATEMENT_CIPHERTEXT_CACHE = "statementCiphertext";

    private final SignedLinkProperties signedLinkProperties;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // TTL matched to the signed-link expiry: bounded, self-evicting, no manual cleanup job -
        // an entry is never worth more than the link that could still be redeeming it.
        var cacheConfig = RedisCacheConfiguration.defaultCacheConfig().entryTtl(signedLinkProperties.getExpiry());
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .withCacheConfiguration(STATEMENT_CIPHERTEXT_CACHE, cacheConfig)
                .build();
    }
}
