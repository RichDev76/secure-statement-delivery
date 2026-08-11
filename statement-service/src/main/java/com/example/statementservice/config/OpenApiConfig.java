package com.example.statementservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Statement Service API",
                        version = "1.0.0",
                        description =
                                "Secure bank statement upload and download service with encrypted storage and time-limited access"),
        servers = @Server(url = "http://localhost:8080"))
public class OpenApiConfig {}
