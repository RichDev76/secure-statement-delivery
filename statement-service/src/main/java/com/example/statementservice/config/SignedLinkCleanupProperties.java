package com.example.statementservice.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "statements.signed-link.cleanup")
public class SignedLinkCleanupProperties {

    private boolean enabled = true;
    private String cron = "0 0/5 * * * *";
    private Duration retentionPeriod = Duration.ZERO;
    private int batchSize = 500;
    private Duration lockAtMostFor = Duration.ofMinutes(5);
    private Duration lockAtLeastFor = Duration.ofSeconds(10);
}
