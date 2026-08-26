package com.example.statementservice.infrastructure.cache;

import static com.example.statementservice.infrastructure.cache.RedisCacheConfig.STATEMENT_CIPHERTEXT_CACHE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.statement.EncryptedFileFetcher;
import com.example.statementservice.statement.StatementFileStore;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Only the cache lookup is forced to fail, not the Redis container itself - stopping/restarting
// it mid-suite would break the connection factory's already-resolved port.
class CacheDegradeIT extends AbstractIntegrationTest {

    @Autowired
    private EncryptedFileFetcher encryptedFileFetcher;

    @MockitoSpyBean
    private StatementFileStore fileStore;

    @MockitoSpyBean
    private CacheManager cacheManager;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingCacheErrorHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void GivenCacheGetThrows_WhenFetching_ThenDegradesToUnderlyingStoreAndStillReturnsCorrectBytes() throws Exception {
        // Given
        var content = "encrypted-bytes".getBytes();
        var storageKey = fileStore.store(
                UUID.randomUUID(),
                "123456789",
                LocalDate.of(2026, 8, 1),
                content.length,
                () -> new ByteArrayInputStream(content));

        var realCache = cacheManager.getCache(STATEMENT_CIPHERTEXT_CACHE);
        var brokenCache = spy(realCache);
        doThrow(new RedisConnectionFailureException("simulated outage"))
                .when(brokenCache)
                .get(any());
        doReturn(brokenCache).when(cacheManager).getCache(STATEMENT_CIPHERTEXT_CACHE);

        // When
        var result = encryptedFileFetcher.fetch(storageKey);

        // Then
        assertThat(result).isEqualTo(content);
        verify(fileStore, times(1)).open(storageKey);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("failed to get entry"));
    }
}
