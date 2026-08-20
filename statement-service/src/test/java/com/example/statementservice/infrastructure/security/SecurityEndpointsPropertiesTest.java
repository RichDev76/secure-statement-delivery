package com.example.statementservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SecurityEndpointsPropertiesTest {

    @EnableConfigurationProperties(SecurityEndpointsProperties.class)
    @Configuration(proxyBeanMethods = false)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    private static final String[] VALID_PROPERTIES = {
        "security.endpoints.whitelist[0].method=GET",
        "security.endpoints.whitelist[0].pattern=/api/v1/statements/download/**",
        "security.endpoints.whitelist[1].method=GET",
        "security.endpoints.whitelist[1].pattern=/api/v1/statements/actuator/health/**"
    };

    @Test
    void GivenWhitelistRules_WhenContextBinds_ThenEachEndpointRuleCarriesItsMethodAndPattern() {
        contextRunner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            var properties = context.getBean(SecurityEndpointsProperties.class);

            assertThat(properties.getWhitelist()).hasSize(2);
            assertThat(properties.getWhitelist().get(0).getMethod()).isEqualTo("GET");
            assertThat(properties.getWhitelist().get(0).getPattern()).isEqualTo("/api/v1/statements/download/**");
            assertThat(properties.getWhitelist().get(1).getPattern())
                    .isEqualTo("/api/v1/statements/actuator/health/**");
        });
    }

    @Test
    void GivenWhitelistGroupMissing_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("whitelist");
        });
    }

    @Test
    void GivenRuleWithBlankMethod_WhenContextBinds_ThenStartupFailsValidation() {
        var propertiesWithBlankMethod =
                replace("security.endpoints.whitelist[0].method=GET", "security.endpoints.whitelist[0].method=");

        contextRunner.withPropertyValues(propertiesWithBlankMethod).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("method");
        });
    }

    @Test
    void GivenRuleWithBlankPattern_WhenContextBinds_ThenStartupFailsValidation() {
        var propertiesWithBlankPattern = replace(
                "security.endpoints.whitelist[0].pattern=/api/v1/statements/download/**",
                "security.endpoints.whitelist[0].pattern=");

        contextRunner.withPropertyValues(propertiesWithBlankPattern).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("pattern");
        });
    }

    private static String[] replace(String original, String replacement) {
        var result = VALID_PROPERTIES.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i].equals(original)) {
                result[i] = replacement;
            }
        }
        return result;
    }
}
