package com.example.statementservice.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.statement.EncryptedFileFetcher;
import com.example.statementservice.statement.StatementFileStore;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Real Redis Testcontainer, real Spring context: the @Cacheable self-invocation bug this class
// was specifically extracted to avoid (see CachingEncryptedFileFetcher) only surfaces when calls
// actually go through Spring's caching proxy - a mocked-bean unit test wouldn't exercise that
// proxy at all and would pass regardless of whether the annotation actually took effect.
class EncryptedFileFetcherCacheIT extends AbstractIntegrationTest {

    @Autowired
    private EncryptedFileFetcher encryptedFileFetcher;

    @MockitoSpyBean
    private StatementFileStore fileStore;

    @Test
    void Given_SameStorageKeyFetchedTwice_When_Fetching_Then_UnderlyingStoreIsOnlyReadOnce() throws Exception {
        // Given
        var content = "encrypted-bytes".getBytes();
        var storageKey =
                fileStore.store(UUID.randomUUID(), "123456789", LocalDate.of(2026, 8, 1), out -> out.write(content));

        // When
        var first = encryptedFileFetcher.fetch(storageKey);
        var second = encryptedFileFetcher.fetch(storageKey);

        // Then
        assertThat(first).isEqualTo(content);
        assertThat(second).isEqualTo(content);
        verify(fileStore, times(1)).open(storageKey);
    }

    @Test
    void Given_DifferentStorageKeys_When_Fetching_Then_UnderlyingStoreIsReadForEachKey() throws Exception {
        // Given
        var contentA = "file-a".getBytes();
        var contentB = "file-b".getBytes();
        var storageKeyA =
                fileStore.store(UUID.randomUUID(), "123456789", LocalDate.of(2026, 8, 1), out -> out.write(contentA));
        var storageKeyB =
                fileStore.store(UUID.randomUUID(), "123456789", LocalDate.of(2026, 8, 2), out -> out.write(contentB));

        // When
        var resultA = encryptedFileFetcher.fetch(storageKeyA);
        var resultB = encryptedFileFetcher.fetch(storageKeyB);

        // Then
        assertThat(resultA).isEqualTo(contentA);
        assertThat(resultB).isEqualTo(contentB);
        verify(fileStore, times(1)).open(storageKeyA);
        verify(fileStore, times(1)).open(storageKeyB);
    }
}
