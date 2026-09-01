package com.example.statementservice.infrastructure.cache;

import static com.example.statementservice.infrastructure.cache.RedisCacheConfig.STATEMENT_CIPHERTEXT_CACHE;

import com.example.statementservice.statement.EncryptedFileFetcher;
import com.example.statementservice.statement.StatementFileStore;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

// Own bean, not a StatementService method: @Cacheable only fires through Spring's proxy, not via self-invocation.
@Component
@RequiredArgsConstructor
public class CachingEncryptedFileFetcher implements EncryptedFileFetcher {

    private final StatementFileStore fileStore;

    @Override
    @Cacheable(STATEMENT_CIPHERTEXT_CACHE)
    public byte[] fetch(String storageKey) throws IOException {
        try (var ciphertext = fileStore.open(storageKey)) {
            return ciphertext.readAllBytes();
        }
    }
}
