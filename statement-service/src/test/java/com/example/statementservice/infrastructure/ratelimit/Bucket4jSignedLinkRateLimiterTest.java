package com.example.statementservice.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bucket4jSignedLinkRateLimiter Unit Tests")
class Bucket4jSignedLinkRateLimiterTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private BucketProxy bucketProxy;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SignedLinkProperties properties;
    private Bucket4jSignedLinkRateLimiter rateLimiter;
    private UUID linkId;

    @BeforeEach
    void setUp() {
        properties = new SignedLinkProperties();
        properties.setRateLimitPerMinute(10);
        rateLimiter = new Bucket4jSignedLinkRateLimiter(proxyManager, properties, jdbcTemplate);
        linkId = UUID.randomUUID();
    }

    @Test
    void GivenBucketHasCapacity_WhenTryConsume_ThenReturnsTrue() {
        // Given
        when(proxyManager.getProxy(eq(linkId.toString()), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsume(1)).thenReturn(true);

        // When
        var result = rateLimiter.tryConsume(linkId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void GivenBucketIsExhausted_WhenTryConsume_ThenReturnsFalse() {
        // Given
        when(proxyManager.getProxy(eq(linkId.toString()), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsume(1)).thenReturn(false);

        // When
        var result = rateLimiter.tryConsume(linkId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenProxyManagerThrows_WhenTryConsume_ThenFailsClosedAndReturnsFalse() {
        // Given
        when(proxyManager.getProxy(eq(linkId.toString()), any())).thenThrow(new RuntimeException("db unavailable"));

        // When
        var result = rateLimiter.tryConsume(linkId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void GivenExpiredBuckets_WhenDeleteExpiredBuckets_ThenDelegatesToJdbcTemplateWithCurrentTime() {
        // Given
        when(jdbcTemplate.update(contains("signed_link_rate_limit_buckets"), anyLong()))
                .thenReturn(4);

        // When
        var deleted = rateLimiter.deleteExpiredBuckets();

        // Then
        assertThat(deleted).isEqualTo(4);
    }
}
