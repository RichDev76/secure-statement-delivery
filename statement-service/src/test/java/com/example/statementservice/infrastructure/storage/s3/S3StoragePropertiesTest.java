package com.example.statementservice.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class S3StoragePropertiesTest {

    @EnableConfigurationProperties(S3StorageProperties.class)
    @Configuration(proxyBeanMethods = false)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    private static final String[] VALID_PROPERTIES = {
        "statement.storage.s3.bucket=statements",
        "statement.storage.s3.region=eu-west-1",
        "statement.storage.s3.endpoint=http://floci:4566",
        "statement.storage.s3.path-style-access=true"
    };

    @Test
    void GivenAllPropertiesSet_WhenContextBinds_ThenEachValueIsPopulated() {
        contextRunner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            var properties = context.getBean(S3StorageProperties.class);

            assertThat(properties.getBucket()).isEqualTo("statements");
            assertThat(properties.getRegion()).isEqualTo("eu-west-1");
            assertThat(properties.getEndpoint()).isEqualTo("http://floci:4566");
            assertThat(properties.isPathStyleAccess()).isTrue();
        });
    }

    @Test
    void GivenEndpointNotSet_WhenContextBinds_ThenEndpointIsNull() {
        contextRunner
                .withPropertyValues("statement.storage.s3.bucket=statements", "statement.storage.s3.region=eu-west-1")
                .run(context -> {
                    var properties = context.getBean(S3StorageProperties.class);
                    assertThat(properties.getEndpoint()).isNull();
                });
    }

    @Test
    void GivenPathStyleAccessNotSet_WhenContextBinds_ThenDefaultsToTrue() {
        contextRunner
                .withPropertyValues("statement.storage.s3.bucket=statements", "statement.storage.s3.region=eu-west-1")
                .run(context -> {
                    var properties = context.getBean(S3StorageProperties.class);
                    assertThat(properties.isPathStyleAccess()).isTrue();
                });
    }

    @Test
    void GivenBucketMissing_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.storage.s3.region=eu-west-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("bucket");
                });
    }

    @Test
    void GivenBucketBlank_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.storage.s3.bucket=", "statement.storage.s3.region=eu-west-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("bucket");
                });
    }

    @Test
    void GivenRegionMissing_WhenContextBinds_ThenStartupFailsValidation() {
        contextRunner
                .withPropertyValues("statement.storage.s3.bucket=statements")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("region");
                });
    }
}
