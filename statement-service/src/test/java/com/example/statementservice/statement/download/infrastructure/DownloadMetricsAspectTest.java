package com.example.statementservice.statement.download.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.download.DownloadOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DownloadMetricsAspectTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DownloadMetricsAspect aspect = new DownloadMetricsAspect(meterRegistry);

    @ParameterizedTest
    @EnumSource(DownloadOutcome.class)
    void GivenAnyDownloadOutcome_WhenRecorded_ThenIncrementsOutcomeCounterWithLowercaseTag(DownloadOutcome outcome) {
        // When
        aspect.recordDownloadOutcome(outcome);

        // Then
        var counter = meterRegistry.counter(
                DownloadMetricsAspect.DOWNLOAD_OUTCOME_METRIC,
                "outcome",
                outcome.name().toLowerCase(Locale.ROOT));
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void GivenSameOutcomeTwice_WhenRecorded_ThenCounterAccumulates() {
        // When
        aspect.recordDownloadOutcome(DownloadOutcome.RATE_LIMITED);
        aspect.recordDownloadOutcome(DownloadOutcome.RATE_LIMITED);

        // Then
        var counter = meterRegistry.counter(DownloadMetricsAspect.DOWNLOAD_OUTCOME_METRIC, "outcome", "rate_limited");
        assertThat(counter.count()).isEqualTo(2.0);
    }
}
