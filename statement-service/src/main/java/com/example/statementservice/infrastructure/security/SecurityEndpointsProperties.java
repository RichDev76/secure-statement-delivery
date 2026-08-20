package com.example.statementservice.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "security.endpoints")
public class SecurityEndpointsProperties {

    @NotEmpty(message = "security.endpoints.whitelist must declare at least one rule")
    private List<@Valid EndpointRule> whitelist = new ArrayList<>();

    @Getter
    @Setter
    public static class EndpointRule {

        @NotBlank(message = "security.endpoints.*.method must not be blank")
        private String method;

        @NotBlank(message = "security.endpoints.*.pattern must not be blank")
        private String pattern;
    }
}
