package com.example.statementservice.infrastructure.storage.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

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
            return Health.down(e).withDetail("bucket", properties.getBucket()).build();
        }
    }
}
