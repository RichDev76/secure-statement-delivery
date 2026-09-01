package com.example.statementservice.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SignaturePropertiesTest {

    // Generated per run so no secret-shaped literal lives in the repo.
    private static final String STRONG_SECRET = randomBase64Secret();

    @EnableConfigurationProperties(SignatureProperties.class)
    @Configuration(proxyBeanMethods = false)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void GivenStrongSecret_WhenContextBinds_ThenSecretIsAvailable() {
        contextRunner
                .withPropertyValues("statement.encryption.signature.secret=" + STRONG_SECRET)
                .run(context -> {
                    var properties = context.getBean(SignatureProperties.class);

                    assertThat(properties.getSecret()).isEqualTo(STRONG_SECRET);
                });
    }

    @Test
    void GivenSecretShorterThanThirtyTwoChars_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.encryption.signature.secret=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("at least 32");
                });
    }

    @Test
    void GivenMissingSecret_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("secret");
        });
    }

    private static String randomBase64Secret() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
