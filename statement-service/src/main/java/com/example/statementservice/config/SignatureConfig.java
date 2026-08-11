package com.example.statementservice.config;

import com.example.statementservice.util.SignatureUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    @Bean
    public SignatureUtil signatureUtil(SignatureProperties properties) {
        return new SignatureUtil(properties.getSecret());
    }
}
