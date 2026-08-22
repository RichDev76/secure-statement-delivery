package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class AuditPartitionMaintenanceServiceTest {

    @Mock
    private AuditPartitionRepository auditPartitionRepository;

    private AuditPartitionMaintenanceProperties properties;
    private AuditPartitionMaintenanceService service;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        properties = new AuditPartitionMaintenanceProperties();
        service = new AuditPartitionMaintenanceService(auditPartitionRepository, properties);

        logger = (Logger) LoggerFactory.getLogger(AuditPartitionMaintenanceService.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    void GivenMaintenanceEnabled_WhenCreatingUpcomingPartitions_ThenPartitionFunctionIsInvoked() {
        // Given
        when(auditPartitionRepository.countDefaultPartitionRows()).thenReturn(0);

        // When
        service.createUpcomingPartitions();

        // Then
        verify(auditPartitionRepository).createUpcomingPartitions(properties.getMonthsAhead());
    }

    @Test
    void GivenMaintenanceDisabled_WhenCreatingUpcomingPartitions_ThenNothingIsExecuted() {
        // Given
        properties.setEnabled(false);

        // When
        service.createUpcomingPartitions();

        // Then
        verify(auditPartitionRepository, never()).createUpcomingPartitions(anyInt());
    }

    @Test
    void GivenDefaultPartitionHasRows_WhenCreatingUpcomingPartitions_ThenErrorIsLogged() {
        // Given
        when(auditPartitionRepository.countDefaultPartitionRows()).thenReturn(3);

        // When
        service.createUpcomingPartitions();

        // Then
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("fallen behind"));
    }

    @Test
    void GivenDefaultPartitionIsEmpty_WhenCreatingUpcomingPartitions_ThenNoErrorIsLogged() {
        // Given
        when(auditPartitionRepository.countDefaultPartitionRows()).thenReturn(0);

        // When
        service.createUpcomingPartitions();

        // Then
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(message -> assertThat(message).contains("fallen behind"));
    }
}
