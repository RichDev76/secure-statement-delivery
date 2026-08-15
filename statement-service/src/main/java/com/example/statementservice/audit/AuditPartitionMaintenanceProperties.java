package com.example.statementservice.audit;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "statement.audit.partition-maintenance")
public class AuditPartitionMaintenanceProperties {

    private boolean enabled = true;
    private String cron = "0 0 0 1 * *";
    private int monthsAhead = 2;
    private Duration lockAtMostFor = Duration.ofMinutes(5);
    private Duration lockAtLeastFor = Duration.ofSeconds(10);
}
