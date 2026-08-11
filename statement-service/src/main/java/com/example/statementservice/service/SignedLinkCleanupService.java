package com.example.statementservice.service;

import com.example.statementservice.config.SignedLinkCleanupProperties;
import com.example.statementservice.repository.SignedLinkRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignedLinkCleanupService {

    private static final String CORRELATION_ID_KEY = "correlationId";

    private final SignedLinkRepository repository;
    private final SignedLinkCleanupProperties properties;

    @Scheduled(cron = "${statement.signed-link.cleanup.cron}")
    @SchedulerLock(
            name = "statement.signed-link.cleanup.job",
            lockAtMostFor = "#{@signedLinkCleanupProperties.lockAtMostFor}",
            lockAtLeastFor = "#{@signedLinkCleanupProperties.lockAtLeastFor}")
    @Transactional
    public void cleanup() {
        var correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);

        try {
            if (!properties.isEnabled()) {
                log.debug("SignedLink cleanup is disabled (correlationId={})", correlationId);
                return;
            }

            var now = OffsetDateTime.now();
            var cutoff = now.minus(properties.getRetentionPeriod());

            int totalDeleted = 0;
            int deleted;

            do {
                deleted = repository.deleteExpiredOrUsed(cutoff, properties.getBatchSize());
                totalDeleted += deleted;
            } while (deleted == properties.getBatchSize());

            if (totalDeleted > 0) {
                log.info(
                        "SignedLink cleanup removed {} rows (cutoff={}, batchSize={})",
                        totalDeleted,
                        cutoff,
                        properties.getBatchSize());
            } else {
                log.info("SignedLink cleanup completed, no rows removed");
            }
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
