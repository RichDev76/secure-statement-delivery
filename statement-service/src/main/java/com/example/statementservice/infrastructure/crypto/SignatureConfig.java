package com.example.statementservice.infrastructure.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    @Bean
    public SignatureUtil signatureUtil(SignatureProperties properties) {
        return new SignatureUtil(properties.getSecret());
    }
}
