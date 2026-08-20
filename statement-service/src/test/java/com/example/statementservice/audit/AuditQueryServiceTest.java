package com.example.statementservice.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.support.LogCapture;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    private static final String ACCOUNT_NUMBER = "123456789";

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogEntityMapper auditLogEntityMapper;

    @InjectMocks
    private AuditQueryService auditQueryService;

    private AuditLog testAuditLog;
    private AuditLogDto testAuditLogDto;

    @BeforeEach
    void setUp() {
        testAuditLog = new AuditLog();
        testAuditLog.setId(UUID.randomUUID());
        testAuditLog.setAccountNumber(ACCOUNT_NUMBER);
        testAuditLog.setAction("DOWNLOAD");
        testAuditLog.setPerformedAt(OffsetDateTime.now());
        testAuditLog.setPerformedBy("testUser");

        testAuditLogDto = AuditLogDto.builder()
                .id(UUID.randomUUID())
                .accountNumber(ACCOUNT_NUMBER)
                .action("DOWNLOAD")
                .build();
    }

    private void stubRepositoryPage(List<AuditLog> auditLogs, List<AuditLogDto> dtos, long totalElements) {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(auditLogs, invocation.getArgument(1), totalElements));
        when(auditLogEntityMapper.toDtos(auditLogs)).thenReturn(dtos);
    }

    private void stubEmptyRepositoryPage() {
        stubRepositoryPage(Collections.emptyList(), Collections.emptyList(), 0);
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void GivenAccountNumber_WhenQueryingAuditLogs_ThenDebugLogNeverContainsAccountNumberValue() {
        // Given
        stubEmptyRepositoryPage();

        try (var logs = LogCapture.forClassAtLevel(AuditQueryService.class, Level.DEBUG)) {
            // When
            auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, 0, 50);

            // Then
            assertThat(logs.lines())
                    .as("the query must still log something - a guard only checking absence "
                            + "would pass trivially if the log statement were deleted")
                    .isNotEmpty()
                    .as("the account number must never reach a log line")
                    .noneMatch(line -> line.contains(ACCOUNT_NUMBER));
        }
    }

    @Test
    void GivenAllFilters_WhenQueryingAuditLogs_ThenDomainPageCarriesContentAndMetadata() {
        // Given
        stubRepositoryPage(List.of(testAuditLog), List.of(testAuditLogDto), 1);

        // When
        Page<AuditLogDto> result =
                auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "2024-01-01", "2024-01-31", 0, 50);

        // Then
        assertThat(result.getContent()).containsExactly(testAuditLogDto);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void GivenBlankAccountNumber_WhenQueryingAuditLogs_ThenSpecificationBuiltWithBlankFilterNotNull() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs("   ", null, null, null, null);

            // Then: a blank accountNumber must never be silently treated as "no filter" - that was
            // exactly the object-level authorization gap being closed. The filter still runs, just
            // against a value ("") that matches no real account.
            specs.verify(() -> AuditLogSpecifications.filter(eq(""), isNull(), isNull()));
        }
    }

    @Test
    void GivenPaddedAccountNumber_WhenQueryingAuditLogs_ThenSpecificationBuiltWithTrimmedFilter() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs("  ACC123  ", null, null, null, null);

            // Then
            specs.verify(() -> AuditLogSpecifications.filter(eq("ACC123"), isNull(), isNull()));
        }
    }

    @Test
    void GivenNullPagination_WhenQueryingAuditLogs_ThenDefaultsOfPageZeroSizeFiftyApply() {
        // Given
        stubEmptyRepositoryPage();

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, null, null);

        // Then
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(50);
    }

    @Test
    void GivenNegativePageNumber_WhenQueryingAuditLogs_ThenPageIsNormalizedToZero() {
        // Given
        stubEmptyRepositoryPage();

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, -5, null);

        // Then
        assertThat(result.getNumber()).isZero();
    }

    @Test
    void GivenOversizedPageSize_WhenQueryingAuditLogs_ThenSizeIsCappedAtOneHundred() {
        // Given
        stubEmptyRepositoryPage();

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, null, 200);

        // Then
        assertThat(result.getSize()).isEqualTo(100);
    }

    @Test
    void GivenZeroPageSize_WhenQueryingAuditLogs_ThenSizeIsRaisedToOne() {
        // Given
        stubEmptyRepositoryPage();

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, null, 0);

        // Then
        assertThat(result.getSize()).isEqualTo(1);
    }

    @Test
    void GivenValidStartDate_WhenQueryingAuditLogs_ThenSpecificationReceivesStartOfDay() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "2024-01-15", null, null, null);

            // Then
            ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            specs.verify(() -> AuditLogSpecifications.filter(eq(ACCOUNT_NUMBER), startCaptor.capture(), isNull()));
            var capturedStart = startCaptor.getValue();
            assertThat(capturedStart.toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 15));
            assertThat(capturedStart.getHour()).isZero();
            assertThat(capturedStart.getMinute()).isZero();
        }
    }

    @Test
    void GivenValidEndDate_WhenQueryingAuditLogs_ThenSpecificationReceivesEndOfDay() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, "2024-01-31", null, null);

            // Then
            ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            specs.verify(() -> AuditLogSpecifications.filter(eq(ACCOUNT_NUMBER), isNull(), endCaptor.capture()));
            var capturedEnd = endCaptor.getValue();
            assertThat(capturedEnd.toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 31));
            assertThat(capturedEnd.getHour()).isEqualTo(23);
            assertThat(capturedEnd.getMinute()).isEqualTo(59);
            assertThat(capturedEnd.getSecond()).isEqualTo(59);
        }
    }

    @Test
    void GivenMalformedStartDate_WhenQueryingAuditLogs_ThenInvalidDateExceptionIsThrown() {
        assertThatThrownBy(
                        () -> auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "invalid-date", null, null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid start date format");
    }

    @Test
    void GivenMalformedEndDate_WhenQueryingAuditLogs_ThenInvalidDateExceptionIsThrown() {
        assertThatThrownBy(() -> auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, "2024/01/31", null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Invalid end date format");
    }

    @Test
    void GivenStartDateAfterEndDate_WhenQueryingAuditLogs_ThenInvalidDateExceptionIsThrown() {
        assertThatThrownBy(() ->
                        auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "2024-02-01", "2024-01-01", null, null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("Start date must be before or equal to end date");
    }

    @Test
    void GivenBlankDates_WhenQueryingAuditLogs_ThenSpecificationReceivesNullBounds() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "   ", "   ", null, null);

            // Then
            specs.verify(() -> AuditLogSpecifications.filter(eq(ACCOUNT_NUMBER), isNull(), isNull()));
        }
    }

    @Test
    void GivenPaddedDateStrings_WhenQueryingAuditLogs_ThenDatesAreTrimmedBeforeParsing() {
        try (MockedStatic<AuditLogSpecifications> specs = mockStatic(AuditLogSpecifications.class)) {
            // Given
            when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));
            when(auditLogEntityMapper.toDtos(Collections.emptyList())).thenReturn(Collections.emptyList());

            // When
            auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "  2024-01-01  ", "  2024-01-31  ", null, null);

            // Then
            ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            specs.verify(() ->
                    AuditLogSpecifications.filter(eq(ACCOUNT_NUMBER), startCaptor.capture(), endCaptor.capture()));
            assertThat(startCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(endCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        }
    }

    @Test
    void GivenEqualStartAndEndDate_WhenQueryingAuditLogs_ThenQuerySucceeds() {
        // Given
        stubEmptyRepositoryPage();

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, "2024-01-15", "2024-01-15", null, null);

        // Then
        assertThat(result).isNotNull();
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void GivenAnyQuery_WhenQueryingAuditLogs_ThenResultsAreSortedByPerformedAtDescending() {
        // Given
        stubEmptyRepositoryPage();

        // When
        auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, null, null);

        // Then
        var order = capturedPageable().getSort().getOrderFor("performedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void GivenSecondPageRequest_WhenQueryingAuditLogs_ThenPageMetadataReflectsRequestedPage() {
        // Given
        stubRepositoryPage(List.of(testAuditLog), List.of(testAuditLogDto), 120);

        // When
        var result = auditQueryService.getFilteredAuditLogs(ACCOUNT_NUMBER, null, null, 1, 50);

        // Then
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(120);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(capturedPageable()).isEqualTo(PageRequest.of(1, 50, Sort.by(Sort.Direction.DESC, "performedAt")));
    }
}
