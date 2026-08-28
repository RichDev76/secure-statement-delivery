package com.example.statementservice.statement.signedlink.infrastructure;

import com.example.statementservice.infrastructure.web.CorrelationIdFilter;
import com.example.statementservice.statement.signedlink.SignedLinkCleanupService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignedLinkCleanupScheduler {

    private final SignedLinkCleanupService cleanupService;

    @Scheduled(cron = "${statement.signed-link.cleanup.cron}")
    @SchedulerLock(
            name = "statement.signed-link.cleanup.job",
            lockAtMostFor = "#{@signedLinkCleanupProperties.lockAtMostFor}",
            lockAtLeastFor = "#{@signedLinkCleanupProperties.lockAtLeastFor}")
    public void runCleanup() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, UUID.randomUUID().toString());
        try {
            cleanupService.cleanup();
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }
    }
}
