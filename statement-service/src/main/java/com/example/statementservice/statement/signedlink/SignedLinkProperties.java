package com.example.statementservice.statement.signedlink;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "statement.signed-link")
public class SignedLinkProperties {

    @NotNull(message = "statement.signed-link.expiry must be configured")
    private Duration expiry = Duration.ofMinutes(3);

    @NotBlank(message = "statement.signed-link.download-path must not be blank")
    @Pattern(regexp = "^/.*/$", message = "statement.signed-link.download-path must start and end with '/'")
    private String downloadPath = "/api/v1/statements/download/";

    // Doubles as the resource-cost ceiling for a leaked link (each redemption re-fetches and
    // decrypts the file), not just retry-tolerance - kept deliberately tight.
    @Min(value = 1, message = "statement.signed-link.max-redemptions must be at least 1")
    private int maxRedemptions = 3;

    @Min(value = 1, message = "statement.signed-link.rate-limit-per-minute must be at least 1")
    private int rateLimitPerMinute = 10;

    // Public base for minted links; unset derives it from the request (dev only).
    @Pattern(
            regexp = "^https?://\\S+$",
            message = "statement.signed-link.external-base-url must be an absolute http(s) URL")
    private String externalBaseUrl;
}
