package com.example.statementservice.statement.signedlink;

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
    private Duration expiry = Duration.ofMinutes(15);

    @NotBlank(message = "statement.signed-link.download-path must not be blank")
    @Pattern(regexp = "^/.*/$", message = "statement.signed-link.download-path must start and end with '/'")
    private String downloadPath = "/api/v1/statements/download/";
}
