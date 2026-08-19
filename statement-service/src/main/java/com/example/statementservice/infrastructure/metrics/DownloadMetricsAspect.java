package com.example.statementservice.infrastructure.metrics;

import com.example.statementservice.statement.download.DownloadService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class DownloadMetricsAspect {

    public static final String DOWNLOAD_OUTCOME_METRIC = "statement.download.outcome";

    private static final String OUTCOME_TAG = "outcome";

    private final MeterRegistry meterRegistry;

    @AfterReturning(
            pointcut =
                    "execution(* com.example.statementservice.statement.download.DownloadService.validateAndStreamDetailed(..))",
            returning = "result")
    public void recordDownloadOutcome(DownloadService.DownloadStreamResult result) {
        meterRegistry
                .counter(
                        DOWNLOAD_OUTCOME_METRIC,
                        OUTCOME_TAG,
                        result.outcome().name().toLowerCase(Locale.ROOT))
                .increment();
    }
}
