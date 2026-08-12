package com.example.statementservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockConfigTest {

    @Test
    void GivenClockConfig_WhenContextStarts_ThenUtcClockBeanIsAvailable() {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class).run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }
}
