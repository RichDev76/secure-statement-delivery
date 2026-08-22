package com.example.statementservice.statement.download.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.DownloadOutcome;
import com.example.statementservice.statement.download.DownloadService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DownloadMetricsAspectTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DownloadMetricsAspect aspect = new DownloadMetricsAspect(meterRegistry);

    @ParameterizedTest
    @EnumSource(DownloadOutcome.class)
    void GivenAnyDownloadOutcome_WhenRecorded_ThenIncrementsOutcomeCounterWithLowercaseTag(DownloadOutcome outcome) {
        // Given
        var result = new DownloadService.DownloadStreamResult(outcome, Optional.empty());

        // When
        aspect.recordDownloadOutcome(result);

        // Then
        var counter = meterRegistry.counter(
                DownloadMetricsAspect.DOWNLOAD_OUTCOME_METRIC,
                "outcome",
                outcome.name().toLowerCase(Locale.ROOT));
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void GivenSameOutcomeTwice_WhenRecorded_ThenCounterAccumulates() {
        // Given
        var result = new DownloadService.DownloadStreamResult(DownloadOutcome.RATE_LIMITED, Optional.empty());

        // When
        aspect.recordDownloadOutcome(result);
        aspect.recordDownloadOutcome(result);

        // Then
        var counter = meterRegistry.counter(DownloadMetricsAspect.DOWNLOAD_OUTCOME_METRIC, "outcome", "rate_limited");
        assertThat(counter.count()).isEqualTo(2.0);
    }
}
