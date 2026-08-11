package com.example.statementservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.service.AuditQueryService;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController Unit Tests")
class AuditControllerTest {

    @Mock
    private AuditQueryService auditQueryService;

    @InjectMocks
    private AuditController auditController;

    private AuditLogPage testAuditLogPage;

    @BeforeEach
    void setUp() {
        testAuditLogPage = new AuditLogPage();
        testAuditLogPage.setContent(new ArrayList<>());
        testAuditLogPage.setPage(0);
        testAuditLogPage.setSize(50);
        testAuditLogPage.setTotalElements(0L);
        testAuditLogPage.setTotalPages(0);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should return OK status with audit log page")
    void getFilteredAuditLogs_Success() {

        String accountNumber = "123456789";
        String startDate = "2024-01-01";
        String endDate = "2024-01-31";
        Integer page = 0;
        Integer size = 50;

        when(auditQueryService.getFilteredAuditLogs(accountNumber, startDate, endDate, page, size))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, accountNumber, startDate, endDate, page, size);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(testAuditLogPage);

        verify(auditQueryService).getFilteredAuditLogs(accountNumber, startDate, endDate, page, size);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should pass all parameters to service")
    void getFilteredAuditLogs_PassesAllParameters() {

        String accountNumber = "987654321";
        String startDate = "2024-02-01";
        String endDate = "2024-02-28";
        Integer page = 1;
        Integer size = 100;

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        auditController.getFilteredAuditLogs(null, accountNumber, startDate, endDate, page, size);

        verify(auditQueryService)
                .getFilteredAuditLogs(eq(accountNumber), eq(startDate), eq(endDate), eq(page), eq(size));
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle null account number")
    void getFilteredAuditLogs_NullAccountNumber() {

        when(auditQueryService.getFilteredAuditLogs(isNull(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, null, "2024-01-01", "2024-01-31", 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs(null, "2024-01-01", "2024-01-31", 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle null date range")
    void getFilteredAuditLogs_NullDates() {

        String accountNumber = "123456789";
        when(auditQueryService.getFilteredAuditLogs(anyString(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, accountNumber, null, null, 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs(accountNumber, null, null, 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle null pagination parameters")
    void getFilteredAuditLogs_NullPagination() {

        String accountNumber = "123456789";
        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, accountNumber, "2024-01-01", "2024-01-31", null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs(accountNumber, "2024-01-01", "2024-01-31", null, null);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle all null parameters")
    void getFilteredAuditLogs_AllNullParameters() {

        when(auditQueryService.getFilteredAuditLogs(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, null, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs(null, null, null, null, null);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should propagate service exceptions")
    void getFilteredAuditLogs_ServiceException() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() ->
                        auditController.getFilteredAuditLogs("corr", "123456789", "2024-01-01", "2024-01-31", 0, 50))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should propagate InvalidDateException")
    void getFilteredAuditLogs_InvalidDateException() {

        String invalidDate = "invalid-date";
        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new InvalidDateException("Invalid date format"));

        assertThatThrownBy(() ->
                        auditController.getFilteredAuditLogs("corr", "123456789", invalidDate, "2024-01-31", 0, 50))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle various page sizes")
    void getFilteredAuditLogs_VariousPageSizes() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 10);
        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 25);
        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 100);

        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 0, 10);
        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 0, 25);
        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 0, 100);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle various page numbers")
    void getFilteredAuditLogs_VariousPageNumbers() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 50);
        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 1, 50);
        auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 5, 50);

        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 0, 50);
        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 1, 50);
        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", "2024-01-31", 5, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should return page with content")
    void getFilteredAuditLogs_WithContent() {

        AuditLogPage pageWithContent = new AuditLogPage();
        pageWithContent.setContent(new ArrayList<>());
        pageWithContent.setPage(0);
        pageWithContent.setSize(50);
        pageWithContent.setTotalElements(100L);
        pageWithContent.setTotalPages(2);

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(pageWithContent);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(100L);
        assertThat(response.getBody().getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle empty account number string")
    void getFilteredAuditLogs_EmptyAccountNumber() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, "", "2024-01-01", "2024-01-31", 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs("", "2024-01-01", "2024-01-31", 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle date range spanning multiple months")
    void getFilteredAuditLogs_LargeDateRange() {

        String startDate = "2024-01-01";
        String endDate = "2024-12-31";

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, "123456789", startDate, endDate, 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs("123456789", startDate, endDate, 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should always return OK status for successful queries")
    void getFilteredAuditLogs_AlwaysReturnsOkStatus() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response1 =
                auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", "2024-01-31", 0, 50);
        ResponseEntity<AuditLogPage> response2 =
                auditController.getFilteredAuditLogs(null, null, null, null, null, null);
        ResponseEntity<AuditLogPage> response3 =
                auditController.getFilteredAuditLogs(null, "987654321", "2024-02-01", "2024-02-28", 1, 100);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle only start date provided")
    void getFilteredAuditLogs_OnlyStartDate() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), anyString(), isNull(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, "123456789", "2024-01-01", null, 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs("123456789", "2024-01-01", null, 0, 50);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle only end date provided")
    void getFilteredAuditLogs_OnlyEndDate() {

        when(auditQueryService.getFilteredAuditLogs(anyString(), isNull(), anyString(), anyInt(), anyInt()))
                .thenReturn(testAuditLogPage);

        ResponseEntity<AuditLogPage> response =
                auditController.getFilteredAuditLogs(null, "123456789", null, "2024-01-31", 0, 50);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditQueryService).getFilteredAuditLogs("123456789", null, "2024-01-31", 0, 50);
    }
}
