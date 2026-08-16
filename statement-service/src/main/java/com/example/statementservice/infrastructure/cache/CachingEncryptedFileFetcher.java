package com.example.statementservice.infrastructure.cache;

import static com.example.statementservice.infrastructure.cache.RedisCacheConfig.STATEMENT_CIPHERTEXT_CACHE;

import com.example.statementservice.statement.EncryptedFileFetcher;
import com.example.statementservice.statement.StatementFileStore;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

// Its own bean, not a method on StatementService calling itself: @Cacheable (like @Transactional)
// only takes effect when called through Spring's proxy - a self-invocation via `this` inside the
// same class bypasses that proxy entirely, so caching would silently never activate.
@Component
@RequiredArgsConstructor
public class CachingEncryptedFileFetcher implements EncryptedFileFetcher {

    private final StatementFileStore fileStore;

    @Override
    @Cacheable(STATEMENT_CIPHERTEXT_CACHE)
    public byte[] fetch(String storageKey) throws IOException {
        return fileStore.open(storageKey).readAllBytes();
    }
}
