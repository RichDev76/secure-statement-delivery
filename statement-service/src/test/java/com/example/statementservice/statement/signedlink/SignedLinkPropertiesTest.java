package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SignedLinkPropertiesTest {

    @EnableConfigurationProperties(SignedLinkProperties.class)
    @Configuration(proxyBeanMethods = false)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void GivenExpiryAndDownloadPathSet_WhenContextBinds_ThenEachValueIsPopulated() {
        contextRunner
                .withPropertyValues(
                        "statement.signed-link.expiry=5m",
                        "statement.signed-link.download-path=/api/v1/statements/download/")
                .run(context -> {
                    var properties = context.getBean(SignedLinkProperties.class);
                    assertThat(properties.getExpiry()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getDownloadPath()).isEqualTo("/api/v1/statements/download/");
                });
    }

    @Test
    void GivenNothingSet_WhenContextBinds_ThenDefaultsApply() {
        contextRunner.run(context -> {
            var properties = context.getBean(SignedLinkProperties.class);
            assertThat(properties.getExpiry()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.getDownloadPath()).isEqualTo("/api/v1/statements/download/");
        });
    }

    @Test
    void GivenDownloadPathWithoutLeadingSlash_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.signed-link.download-path=api/v1/statements/download/")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("download-path");
                });
    }

    @Test
    void GivenDownloadPathWithoutTrailingSlash_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.signed-link.download-path=/api/v1/statements/download")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("download-path");
                });
    }

    @Test
    void GivenDownloadPathBlank_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner.withPropertyValues("statement.signed-link.download-path=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("download-path");
        });
    }
}
