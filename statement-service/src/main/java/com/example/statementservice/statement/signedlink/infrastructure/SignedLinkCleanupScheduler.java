package com.example.statementservice.statement.signedlink.infrastructure;

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

    private static final String CORRELATION_ID_KEY = "correlationId";

    private final SignedLinkCleanupService cleanupService;

    @Scheduled(cron = "${statement.signed-link.cleanup.cron}")
    @SchedulerLock(
            name = "statement.signed-link.cleanup.job",
            lockAtMostFor = "#{@signedLinkCleanupProperties.lockAtMostFor}",
            lockAtLeastFor = "#{@signedLinkCleanupProperties.lockAtLeastFor}")
    public void runCleanup() {
        MDC.put(CORRELATION_ID_KEY, UUID.randomUUID().toString());
        try {
            cleanupService.cleanup();
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
