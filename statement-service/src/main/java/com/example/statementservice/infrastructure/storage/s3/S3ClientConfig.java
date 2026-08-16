package com.example.statementservice.infrastructure.storage.s3;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3ClientConfig {

    // Bounded well inside the 3-minute signed-link TTL. apiCallTimeout doesn't cover a stalled
    // body read, hence the separate socket timeout below.
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECTION_ACQUISITION_TIMEOUT = Duration.ofSeconds(5);

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .overrideConfiguration(buildOverrideConfiguration())
                .httpClientBuilder(buildHttpClientBuilder());

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    static ClientOverrideConfiguration buildOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                .build();
    }

    static Apache5HttpClient.Builder buildHttpClientBuilder() {
        return Apache5HttpClient.builder()
                .connectionTimeout(CONNECTION_TIMEOUT)
                .socketTimeout(SOCKET_TIMEOUT)
                .connectionAcquisitionTimeout(CONNECTION_ACQUISITION_TIMEOUT);
    }
}
