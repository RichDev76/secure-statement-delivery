package com.example.statementservice.infrastructure.storage.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3HealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    @Override
    public Health health() {
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            return Health.up().withDetail("bucket", properties.getBucket()).build();
        } catch (Exception e) {
            // No stack trace: this runs on every actuator poll (often every few seconds), and a
            // sustained outage would otherwise flood logs with one full trace per poll. The cause
            // is already captured structurally in the returned Health.down(e) for the one request
            // that reads it; the log line only needs enough to spot the outage in a log stream.
            log.warn("S3 health check failed for bucket={}: {}", properties.getBucket(), e.toString());
            return Health.down(e).withDetail("bucket", properties.getBucket()).build();
        }
    }
}
