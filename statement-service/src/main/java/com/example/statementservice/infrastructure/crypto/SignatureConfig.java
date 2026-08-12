package com.example.statementservice.infrastructure.crypto;

import com.example.statementservice.statement.signedlink.LinkSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    @Bean
    public LinkSigner linkSigner(SignatureProperties properties) {
        return new HmacSha256LinkSigner(properties.getSecret());
    }
}
