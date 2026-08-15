package com.example.statementservice.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

    private final UuidV7IdGenerator generator = new UuidV7IdGenerator();

    @Test
    void GivenGenerator_WhenGeneratingId_ThenVersionNibbleIsSeven() {
        // When
        var id = generator.newId();

        // Then
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void GivenGenerator_WhenGeneratingId_ThenVariantIsIetf() {
        // When
        var id = generator.newId();

        // Then
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void GivenGenerator_WhenGeneratingManyIds_ThenAllAreUnique() {
        // When
        var ids = IntStream.range(0, 10_000)
                .mapToObj(i -> generator.newId())
                .collect(Collectors.toCollection(HashSet::new));

        // Then
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void GivenIdsGeneratedMillisecondsApart_WhenComparingThem_ThenLaterIdSortsAfterEarlierId()
            throws InterruptedException {
        // Given
        var earlier = generator.newId();
        Thread.sleep(5);

        // When
        var later = generator.newId();

        // Then
        assertThat(later).isGreaterThan(earlier);
    }

    @Test
    void GivenGenerator_WhenGeneratingId_ThenReturnsJavaUtilUUID() {
        // When
        var id = generator.newId();

        // Then
        assertThat(id).isInstanceOf(UUID.class);
    }
}
