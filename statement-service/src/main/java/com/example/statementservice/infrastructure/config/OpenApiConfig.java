package com.example.statementservice.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Statement Upload & Search API",
                        version = "1.15.0",
                        description =
                                "Secure bank statement upload and download service with encrypted storage and time-limited access"))
public class OpenApiConfig {}
