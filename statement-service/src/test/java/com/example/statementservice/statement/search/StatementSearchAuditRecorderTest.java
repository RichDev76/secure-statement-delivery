package com.example.statementservice.statement.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.statementservice.audit.AuditService;
import com.example.statementservice.support.LogCapture;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementSearchAuditRecorderTest {

    @Mock
    private AuditService auditService;

    private StatementSearchAuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        auditRecorder = new StatementSearchAuditRecorder(auditService);
    }

    @Test
    void GivenAccountNumber_WhenAnyStatementSearchAuditRecorderMethodLogs_ThenAccountNumberIsNeverEmitted() {
        // Given: the three methods that receive an account number
        var statementId = UUID.randomUUID();
        var signedLinkId = UUID.randomUUID();
        var accountNumber = "123456789";
        var performedBy = "test-user";
        var failure = new RuntimeException("downstream failure");

        try (var logs = LogCapture.forClass(StatementSearchAuditRecorder.class)) {
            // When
            auditRecorder.recordLinkGenerated(
                    statementId, accountNumber, signedLinkId, performedBy, "127.0.0.1", "JUnit");
            auditRecorder.recordLinkGenerationFailed(
                    statementId, accountNumber, performedBy, failure, "127.0.0.1", "JUnit");
            auditRecorder.recordUnexpectedError(statementId, accountNumber, performedBy, failure, "127.0.0.1", "JUnit");

            // Then
            assertThat(logs.lines())
                    .as("all three methods must still log - a guard that only asserts absence "
                            + "would pass trivially if the log statements were deleted")
                    .hasSize(3)
                    .as("the account number must never reach a log line")
                    .noneMatch(line -> line.contains(accountNumber))
                    .as("statementId is the non-sensitive join key that replaces it")
                    .allMatch(line -> line.contains(statementId.toString()));
        }

        // And
        var accountNumberCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(3)).record(any(), any(), accountNumberCaptor.capture(), any(), any(), any());
        assertThat(accountNumberCaptor.getAllValues()).containsOnly(accountNumber);
    }
}
