package com.example.statementservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class LocalStatementFileStoreTest {

    private LocalStatementFileStore fileStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStore = new LocalStatementFileStore();
        ReflectionTestUtils.setField(fileStore, "baseDir", tempDir.toString());
    }

    @Test
    void GivenContent_WhenStored_ThenReferenceCanBeReadBackWithSameBytes() throws IOException {
        // Given
        var id = UUID.randomUUID();
        var content = "encrypted-bytes".getBytes();

        // When
        var reference = fileStore.store(id, "123456789", LocalDate.of(2026, 7, 1), out -> out.write(content));

        // Then
        assertThat(fileStore.exists(reference)).isTrue();
        assertThat(fileStore.open(reference).readAllBytes()).isEqualTo(content);
    }

    @Test
    void GivenSameAccountAndDate_WhenStoringTwice_ThenBothFilesCoexistUnderTheSameDirectory() throws IOException {
        // Given
        var accountNumber = "987654321";
        var date = LocalDate.of(2026, 3, 15);

        // When
        var first = fileStore.store(UUID.randomUUID(), accountNumber, date, out -> out.write("a".getBytes()));
        var second = fileStore.store(UUID.randomUUID(), accountNumber, date, out -> out.write("b".getBytes()));

        // Then
        assertThat(first).isNotEqualTo(second);
        assertThat(Path.of(first).getParent()).isEqualTo(Path.of(second).getParent());
    }

    @Test
    void GivenNonExistentReference_WhenCheckingExistence_ThenFalseIsReturned() {
        assertThat(fileStore.exists(tempDir.resolve("missing.pdf.enc").toString()))
                .isFalse();
    }

    @Test
    void GivenDifferentStatementDates_WhenStoring_ThenDirectoriesAreSplitByYearAndMonth() throws IOException {
        // Given
        var accountNumber = "111222333";

        // When
        var januaryRef = fileStore.store(
                UUID.randomUUID(), accountNumber, LocalDate.of(2026, 1, 10), out -> out.write("x".getBytes()));
        var decemberRef = fileStore.store(
                UUID.randomUUID(), accountNumber, LocalDate.of(2026, 12, 10), out -> out.write("y".getBytes()));

        // Then
        assertThat(januaryRef).contains("01");
        assertThat(decemberRef).contains("12");
    }
}
