package com.example.statementservice.statement.upload.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class MultipartFileAdapterTest {

    @Test
    void GivenMultipartFile_WhenDelegating_ThenEveryMethodReturnsTheUnderlyingValue() throws IOException {
        // Given
        var content = "%PDF-1.4".getBytes();
        var delegate = new MockMultipartFile("file", "statement.pdf", "application/pdf", content);

        // When
        var adapter = new MultipartFileAdapter(delegate);

        // Then
        assertThat(adapter.isEmpty()).isFalse();
        assertThat(adapter.getContentType()).isEqualTo("application/pdf");
        assertThat(adapter.getOriginalFilename()).isEqualTo("statement.pdf");
        assertThat(adapter.getSize()).isEqualTo(content.length);
        assertThat(adapter.getInputStream()).hasBinaryContent(content);
    }

    @Test
    void GivenMultipartFileReturningFreshStreamsPerCall_WhenReadingTwice_ThenBothReadsSucceed() throws IOException {
        // Given: mirrors disk-spooled MultipartFile's repeatable-read guarantee, not a single-use stream.
        var delegate = mock(MultipartFile.class);
        when(delegate.getInputStream())
                .thenAnswer(invocation -> new ByteArrayInputStream("content".getBytes()))
                .thenAnswer(invocation -> new ByteArrayInputStream("content".getBytes()));
        var adapter = new MultipartFileAdapter(delegate);

        // When
        var firstRead = adapter.getInputStream().readAllBytes();
        var secondRead = adapter.getInputStream().readAllBytes();

        // Then
        assertThat(firstRead).isEqualTo("content".getBytes());
        assertThat(secondRead).isEqualTo("content".getBytes());
    }

    @Test
    void GivenDelegateThrowsOnRead_WhenGettingInputStream_ThenIOExceptionPropagates() throws IOException {
        // Given
        var delegate = mock(MultipartFile.class);
        when(delegate.getInputStream()).thenThrow(new IOException("disk read failed"));
        var adapter = new MultipartFileAdapter(delegate);

        // When / Then
        assertThatThrownBy(adapter::getInputStream)
                .isInstanceOf(IOException.class)
                .hasMessage("disk read failed");
    }

    @Test
    void GivenEmptyMultipartFile_WhenCheckingEmpty_ThenReturnsTrue() {
        // Given
        var delegate = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        // When
        var adapter = new MultipartFileAdapter(delegate);

        // Then
        assertThat(adapter.isEmpty()).isTrue();
    }
}
