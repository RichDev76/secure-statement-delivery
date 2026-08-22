package com.example.statementservice.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");

    @Bean
    public Clock clock() {
        return Clock.system(JOHANNESBURG);
    }
}
