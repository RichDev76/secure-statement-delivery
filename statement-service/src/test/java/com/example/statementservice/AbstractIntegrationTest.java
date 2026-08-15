package com.example.statementservice;

import java.net.URI;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String STATEMENTS_BUCKET = "statements";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final int FLOCI_PORT = 4566;
    private static final String FLOCI_ACCESS_KEY = "test";
    private static final String FLOCI_SECRET_KEY = "test";
    private static final String FLOCI_REGION = "us-east-1";

    protected static final GenericContainer<?> FLOCI = new GenericContainer<>(
                    DockerImageName.parse("floci/floci:1.5.33-compat"))
            .withExposedPorts(FLOCI_PORT)
            .waitingFor(Wait.forLogMessage(".*Ready\\.\\n", 1));

    static {
        POSTGRES.start();
        FLOCI.start();
        // Picked up by the app's S3Client via the SDK's default credentials provider chain
        // (SystemPropertyCredentialsProvider) - Floci accepts any non-empty static keys.
        System.setProperty("aws.accessKeyId", FLOCI_ACCESS_KEY);
        System.setProperty("aws.secretAccessKey", FLOCI_SECRET_KEY);
        createStatementsBucket();
    }

    protected static String flociEndpoint() {
        return "http://" + FLOCI.getHost() + ":" + FLOCI.getMappedPort(FLOCI_PORT);
    }

    private static void createStatementsBucket() {
        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(flociEndpoint()))
                .region(Region.of(FLOCI_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(FLOCI_ACCESS_KEY, FLOCI_SECRET_KEY)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            s3Client.createBucket(b -> b.bucket(STATEMENTS_BUCKET));
        }
    }

    @DynamicPropertySource
    static void registerFlociProperties(DynamicPropertyRegistry registry) {
        registry.add("statement.storage.s3.bucket", () -> STATEMENTS_BUCKET);
        registry.add("statement.storage.s3.endpoint", AbstractIntegrationTest::flociEndpoint);
        registry.add("statement.storage.s3.region", () -> FLOCI_REGION);
        registry.add("statement.storage.s3.path-style-access", () -> "true");
    }
}
