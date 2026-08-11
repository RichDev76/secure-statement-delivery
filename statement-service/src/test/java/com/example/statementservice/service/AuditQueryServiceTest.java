package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.mapper.AuditApiMapper;
import com.example.statementservice.mapper.AuditLogEntityMapper;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.model.dto.AuditLogDto;
import com.example.statementservice.model.entity.AuditLog;
import com.example.statementservice.repository.AuditLogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditQueryService Unit Tests")
class AuditQueryServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogEntityMapper auditLogEntityMapper;

    @Mock
    private AuditApiMapper auditApiMapper;

    @Mock
    private AuditLogPage testAuditLogPage;

    @InjectMocks
    private AuditQueryService auditQueryService;

    private AuditLog testAuditLog;
    private AuditLogDto testAuditLogDto;

    @BeforeEach
    void setUp() {
        testAuditLog = new AuditLog();
        testAuditLog.setId(UUID.randomUUID());
        testAuditLog.setAccountNumber("123456789");
        testAuditLog.setAction("DOWNLOAD");
        testAuditLog.setPerformedAt(OffsetDateTime.now());
        testAuditLog.setPerformedBy("testUser");

        testAuditLogDto = new AuditLogDto();
        testAuditLogDto.setId(UUID.randomUUID());
        testAuditLogDto.setAccountNumber("123456789");
        testAuditLogDto.setAction("DOWNLOAD");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should return paginated results with all filters")
    void getFilteredAuditLogs_AllFilters() {
        String accountNumber = "123456789";
        String startDate = "2024-01-01";
        String endDate = "2024-01-31";
        Integer page = 0;
        Integer size = 50;

        List<AuditLog> auditLogs = Arrays.asList(testAuditLog);
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Arrays.asList(testAuditLogDto);
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        AuditLogPage result = auditQueryService.getFilteredAuditLogs(accountNumber, startDate, endDate, page, size);
        assertThat(result).isNotNull();
        verify(auditLogRepository)
                .findFilteredAuditLogs(
                        eq(accountNumber), any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class));
        verify(auditLogEntityMapper).toDtos(auditLogs);
        verify(auditApiMapper).toPage(auditLogDtos);
        verify(testAuditLogPage).page(0);
        verify(testAuditLogPage).size(50);
        verify(testAuditLogPage).totalElements(1L);
        verify(testAuditLogPage).totalPages(1);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle null account number")
    void getFilteredAuditLogs_NullAccountNumber() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        var result = auditQueryService.getFilteredAuditLogs(null, null, null, null, null);
        ArgumentCaptor<String> accountCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogRepository).findFilteredAuditLogs(accountCaptor.capture(), any(), any(), any(Pageable.class));
        assertThat(accountCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should normalize blank account number to null")
    void getFilteredAuditLogs_BlankAccountNumber() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs("   ", null, null, null, null);
        ArgumentCaptor<String> accountCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogRepository).findFilteredAuditLogs(accountCaptor.capture(), any(), any(), any(Pageable.class));
        assertThat(accountCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should trim account number")
    void getFilteredAuditLogs_TrimAccountNumber() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs("  ACC123  ", null, null, null, null);
        ArgumentCaptor<String> accountCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogRepository).findFilteredAuditLogs(accountCaptor.capture(), any(), any(), any(Pageable.class));
        assertThat(accountCaptor.getValue()).isEqualTo("ACC123");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should use default page and size when null")
    void getFilteredAuditLogs_DefaultPagination() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, null, null, null);
        verify(testAuditLogPage).page(0); // DEFAULT_PAGE
        verify(testAuditLogPage).size(50); // DEFAULT_SIZE
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should normalize negative page to 0")
    void getFilteredAuditLogs_NegativePage() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, null, -5, null);
        verify(testAuditLogPage).page(0);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should cap size at MAX_SIZE (100)")
    void getFilteredAuditLogs_MaxSize() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, null, null, 200);
        verify(testAuditLogPage).size(100); // MAX_SIZE
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should enforce MIN_SIZE (1)")
    void getFilteredAuditLogs_MinSize() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, null, null, 0);
        verify(testAuditLogPage).size(1);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should parse valid start date")
    void getFilteredAuditLogs_ValidStartDate() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, "2024-01-15", null, null, null);
        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).findFilteredAuditLogs(any(), startCaptor.capture(), any(), any(Pageable.class));
        var capturedStart = startCaptor.getValue();
        assertThat(capturedStart).isNotNull();
        assertThat(capturedStart.toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(capturedStart.getHour()).isEqualTo(0);
        assertThat(capturedStart.getMinute()).isEqualTo(0);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should parse valid end date to end of day")
    void getFilteredAuditLogs_ValidEndDate() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, "2024-01-31", null, null);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).findFilteredAuditLogs(any(), any(), endCaptor.capture(), any(Pageable.class));
        var capturedEnd = endCaptor.getValue();
        assertThat(capturedEnd).isNotNull();
        assertThat(capturedEnd.toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(capturedEnd.getHour()).isEqualTo(23);
        assertThat(capturedEnd.getMinute()).isEqualTo(59);
        assertThat(capturedEnd.getSecond()).isEqualTo(59);
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should throw InvalidDateException for invalid start date format")
    void getFilteredAuditLogs_InvalidStartDateFormat() {
        assertThatThrownBy(() -> auditQueryService.getFilteredAuditLogs(null, "invalid-date", null, null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid start date format");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should throw InvalidDateException for invalid end date format")
    void getFilteredAuditLogs_InvalidEndDateFormat() {
        assertThatThrownBy(() -> auditQueryService.getFilteredAuditLogs(null, null, "2024/01/31", null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid end date format");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should throw InvalidDateException when start date is after end date")
    void getFilteredAuditLogs_StartAfterEnd() {
        assertThatThrownBy(() -> auditQueryService.getFilteredAuditLogs(null, "2024-02-01", "2024-01-01", null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Start date must be before or equal to end date");
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle blank start date as null")
    void getFilteredAuditLogs_BlankStartDate() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, "   ", null, null, null);
        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).findFilteredAuditLogs(any(), startCaptor.capture(), any(), any(Pageable.class));
        assertThat(startCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should handle blank end date as null")
    void getFilteredAuditLogs_BlankEndDate() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, "   ", null, null);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).findFilteredAuditLogs(any(), any(), endCaptor.capture(), any(Pageable.class));
        assertThat(endCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should trim date strings")
    void getFilteredAuditLogs_TrimDates() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, "  2024-01-01  ", "  2024-01-31  ", null, null);
        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository)
                .findFilteredAuditLogs(any(), startCaptor.capture(), endCaptor.capture(), any(Pageable.class));
        assertThat(startCaptor.getValue()).isNotNull();
        assertThat(endCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should allow same start and end date")
    void getFilteredAuditLogs_SameStartAndEndDate() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        var result = auditQueryService.getFilteredAuditLogs(null, "2024-01-15", "2024-01-15", null, null);
        assertThat(result).isNotNull();
        verify(auditLogRepository).findFilteredAuditLogs(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("getFilteredAuditLogs - should use descending sort by performedAt")
    void getFilteredAuditLogs_VerifySorting() {
        List<AuditLog> auditLogs = Collections.emptyList();
        Page<AuditLog> auditLogPage = new PageImpl<>(auditLogs);
        List<AuditLogDto> auditLogDtos = Collections.emptyList();
        when(auditLogRepository.findFilteredAuditLogs(any(), any(), any(), any()))
                .thenReturn(auditLogPage);
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(auditLogDtos);
        when(auditApiMapper.toPage(auditLogDtos)).thenReturn(testAuditLogPage);
        auditQueryService.getFilteredAuditLogs(null, null, null, null, null);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findFilteredAuditLogs(any(), any(), any(), pageableCaptor.capture());
        var capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort().getOrderFor("performedAt")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("performedAt").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }
}
