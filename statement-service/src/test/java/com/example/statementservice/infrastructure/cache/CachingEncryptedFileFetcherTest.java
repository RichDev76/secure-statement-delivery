package com.example.statementservice.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statementservice.statement.StatementFileStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachingEncryptedFileFetcherTest {

    private static final String STORAGE_KEY = "statements/abc/2026/01/file.pdf.enc";
    private static final byte[] CIPHERTEXT = {1, 2, 3, 4};

    private StatementFileStore fileStore;
    private CachingEncryptedFileFetcher fetcher;

    @BeforeEach
    void setUp() {
        fileStore = mock(StatementFileStore.class);
        fetcher = new CachingEncryptedFileFetcher(fileStore);
    }

    @Test
    void GivenStoreStream_WhenFetching_ThenStreamIsClosedAfterRead() throws IOException {
        // Given
        var closed = new AtomicBoolean(false);
        when(fileStore.open(STORAGE_KEY)).thenReturn(closeTrackingStream(CIPHERTEXT, closed));

        // When
        var bytes = fetcher.fetch(STORAGE_KEY);

        // Then
        assertThat(bytes).isEqualTo(CIPHERTEXT);
        assertThat(closed)
                .as("underlying store stream must be closed after a full read")
                .isTrue();
    }

    @Test
    void GivenReadFailure_WhenFetching_ThenStreamIsStillClosed() throws IOException {
        // Given
        var closed = new AtomicBoolean(false);
        when(fileStore.open(STORAGE_KEY)).thenReturn(failingStream(closed));

        // When / Then
        assertThatThrownBy(() -> fetcher.fetch(STORAGE_KEY)).isInstanceOf(IOException.class);
        assertThat(closed)
                .as("underlying store stream must be closed even when the read fails")
                .isTrue();
    }

    private static InputStream closeTrackingStream(byte[] content, AtomicBoolean closed) {
        return new ByteArrayInputStream(content) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }

    private static InputStream failingStream(AtomicBoolean closed) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset mid-read");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
    }
}
