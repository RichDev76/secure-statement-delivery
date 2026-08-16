package com.example.statementservice.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class S3ClientConfigTest {

    @Test
    void GivenOverrideConfigurationBuilt_WhenInspectingTimeouts_ThenApiCallAndAttemptTimeoutsAreTwentySeconds() {
        // When
        var overrideConfiguration = S3ClientConfig.buildOverrideConfiguration();

        // Then
        assertThat(overrideConfiguration.apiCallTimeout()).contains(Duration.ofSeconds(20));
        assertThat(overrideConfiguration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(20));
    }
}
