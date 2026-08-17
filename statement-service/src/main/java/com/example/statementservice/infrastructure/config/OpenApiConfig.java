package com.example.statementservice.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Statement Upload & Search API",
                        version = "1.12.0",
                        description =
                                "Secure bank statement upload and download service with encrypted storage and time-limited access"),
        servers = @Server(url = "http://localhost:8080/api/v1/statements"))
public class OpenApiConfig {}
