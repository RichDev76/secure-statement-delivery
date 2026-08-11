package com.example.statementservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "statement.encryption.signature")
public class SignatureProperties {

    @NotBlank(message = "Signature secret must be configured")
    private String secret;
}
