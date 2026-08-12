package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignedLinkCleanupServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private SignedLinkRepository repository;

    private SignedLinkCleanupProperties properties;
    private SignedLinkCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        properties = new SignedLinkCleanupProperties();
        cleanupService =
                new SignedLinkCleanupService(repository, properties, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void GivenRetentionPeriod_WhenCleaningUp_ThenCutoffIsClockNowMinusRetention() {
        // Given
        properties.setRetentionPeriod(Duration.ofHours(2));
        when(repository.deleteExpiredOrUsed(any(), anyInt())).thenReturn(0);

        // When
        cleanupService.cleanup();

        // Then
        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteExpiredOrUsed(cutoffCaptor.capture(), anyInt());
        assertThat(cutoffCaptor.getValue())
                .isEqualTo(
                        OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC).minusHours(2));
    }

    @Test
    void GivenFullBatches_WhenCleaningUp_ThenDeletionRepeatsUntilPartialBatch() {
        // Given
        properties.setBatchSize(500);
        when(repository.deleteExpiredOrUsed(any(), anyInt())).thenReturn(500, 500, 120);

        // When
        cleanupService.cleanup();

        // Then
        verify(repository, times(3)).deleteExpiredOrUsed(any(), anyInt());
    }

    @Test
    void GivenCleanupDisabled_WhenCleaningUp_ThenNothingIsDeleted() {
        // Given
        properties.setEnabled(false);

        // When
        cleanupService.cleanup();

        // Then
        verify(repository, never()).deleteExpiredOrUsed(any(), anyInt());
    }
}
