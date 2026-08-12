package com.example.statementservice.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {

    private static final String BUCKET = "statements";

    @Mock
    private S3Client s3Client;

    private S3HealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        var properties = new S3StorageProperties();
        properties.setBucket(BUCKET);
        properties.setRegion("eu-west-1");
        healthIndicator = new S3HealthIndicator(s3Client, properties);
    }

    @Test
    void GivenBucketIsReachable_WhenHealthChecked_ThenStatusIsUp() {
        // Given
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // When
        var health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", BUCKET);
    }

    @Test
    void GivenBucketHeadFails_WhenHealthChecked_ThenStatusIsDown() {
        // Given
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(503)
                        .message("unavailable")
                        .build());

        // When
        var health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("bucket", BUCKET);
    }
}
