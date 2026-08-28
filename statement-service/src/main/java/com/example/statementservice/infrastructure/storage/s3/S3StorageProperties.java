package com.example.statementservice.infrastructure.storage.s3;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "statement.storage.s3")
public class S3StorageProperties {

    @NotBlank(message = "statement.storage.s3.bucket must not be blank")
    private String bucket;

    @NotBlank(message = "statement.storage.s3.region must not be blank")
    private String region;

    // Blank/unset in production (SDK resolves real AWS endpoints); set to the Floci URL in dev/test.
    private String endpoint;

    private boolean pathStyleAccess = true;
}
