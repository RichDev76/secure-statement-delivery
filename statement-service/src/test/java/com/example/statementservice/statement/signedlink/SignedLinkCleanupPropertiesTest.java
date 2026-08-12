package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SignedLinkCleanupPropertiesTest {

    @EnableConfigurationProperties(SignedLinkCleanupProperties.class)
    @Configuration(proxyBeanMethods = false)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void GivenSingularPrefixProperties_WhenContextBinds_ThenAllSixValuesArePopulated() {
        // Non-default values: defaults would mask a failed binding.
        var runner = contextRunner.withPropertyValues(
                "statement.signed-link.cleanup.enabled=false",
                "statement.signed-link.cleanup.cron=0 0 3 * * *",
                "statement.signed-link.cleanup.retention-period=PT1H",
                "statement.signed-link.cleanup.batch-size=100",
                "statement.signed-link.cleanup.lock-at-most-for=PT10M",
                "statement.signed-link.cleanup.lock-at-least-for=PT30S");

        runner.run(context -> {
            var properties = context.getBean(SignedLinkCleanupProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getCron()).isEqualTo("0 0 3 * * *");
            assertThat(properties.getRetentionPeriod()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.getBatchSize()).isEqualTo(100);
            assertThat(properties.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.getLockAtLeastFor()).isEqualTo(Duration.ofSeconds(30));
        });
    }
}
