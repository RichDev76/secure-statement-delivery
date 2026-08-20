package com.example.statementservice.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.audit.AuditLogDto;
import com.example.statementservice.audit.AuditQueryService;
import com.example.statementservice.model.api.AuditLogEntry;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.shared.InvalidDateException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditQueryService auditQueryService;

    @Mock
    private AuditApiMapper auditApiMapper;

    @InjectMocks
    private AuditController auditController;

    private AuditLogDto auditLogDto;
    private AuditLogEntry auditLogEntry;

    @BeforeEach
    void setUp() {
        auditLogDto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .accountNumber("123456789")
                .action("DOWNLOAD")
                .build();

        auditLogEntry = new AuditLogEntry();
        auditLogEntry.setAccountNumber("123456789");
        auditLogEntry.setAction("DOWNLOAD");
    }

    @Test
    void GivenServiceReturnsDomainPage_WhenGettingFilteredAuditLogs_ThenApiPageIsAssembledFromIt() {
        // Given
        var domainPage = new PageImpl<>(List.of(auditLogDto), PageRequest.of(1, 50), 120);
        when(auditQueryService.getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 1, 50))
                .thenReturn(domainPage);
        var apiPage = new AuditLogPage().content(List.of(auditLogEntry));
        when(auditApiMapper.toPage(List.of(auditLogDto))).thenReturn(apiPage);

        // When
        var response = auditController.getFilteredAuditLogs("123456789", null, "2024-01-01", "2024-01-31", 1, 50);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getContent()).containsExactly(auditLogEntry);
        assertThat(body.getPage()).isEqualTo(1);
        assertThat(body.getSize()).isEqualTo(50);
        assertThat(body.getTotalElements()).isEqualTo(120L);
        assertThat(body.getTotalPages()).isEqualTo(3);
    }

    @Test
    void GivenRequestParameters_WhenGettingFilteredAuditLogs_ThenAllAreForwardedToTheService() {
        // Given
        var emptyPage = new PageImpl<AuditLogDto>(List.of(), PageRequest.of(0, 50), 0);
        when(auditQueryService.getFilteredAuditLogs(any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);
        when(auditApiMapper.toPage(List.of())).thenReturn(new AuditLogPage().content(List.of()));

        // When
        auditController.getFilteredAuditLogs("987654321", "corr-id", "2024-02-01", "2024-02-28", 2, 25);

        // Then
        verify(auditQueryService).getFilteredAuditLogs("987654321", "2024-02-01", "2024-02-28", 2, 25);
    }

    @Test
    void GivenServiceThrowsInvalidDateException_WhenGettingFilteredAuditLogs_ThenItPropagates() {
        // Given
        when(auditQueryService.getFilteredAuditLogs(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidDateException("Invalid date format"));

        // When / Then
        assertThatThrownBy(
                        () -> auditController.getFilteredAuditLogs("123456789", null, "bad-date", "2024-01-31", 0, 50))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid date format");
    }
}
