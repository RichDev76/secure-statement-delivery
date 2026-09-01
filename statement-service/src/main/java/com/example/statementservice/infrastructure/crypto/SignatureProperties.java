package com.example.statementservice.infrastructure.crypto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "statement.encryption.signature")
public class SignatureProperties {

    // Sole cryptographic guard on the unauthenticated download endpoint - fail startup on weakness.
    @NotBlank(message = "Signature secret must be configured")
    @Size(min = 32, message = "Signature secret must be at least 32 characters")
    private String secret;
}
