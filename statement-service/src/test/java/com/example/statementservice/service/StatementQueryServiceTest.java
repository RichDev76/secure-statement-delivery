package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.mapper.StatementApiMapper;
import com.example.statementservice.model.api.BaseStatement;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.util.AuditHelper;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementQueryService Unit Tests (Refactored)")
class StatementQueryServiceTest {

    @Mock
    private StatementService statementService;

    @Mock
    private StatementApiMapper statementApiMapper;

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private RequestInfoProvider requestInfoProvider;

    @InjectMocks
    private StatementQueryService statementQueryService;

    private UUID testStatementId;
    private String testAccountNumber;
    private LocalDate testDate;
    private StatementDto testStatementDto;
    private StatementSummary testStatementSummary;
    private BaseStatement testBaseStatement;
    private Statement testStatement;
    private RequestInfo testRequestInfo;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testAccountNumber = "123456789";
        testDate = LocalDate.of(2024, 1, 15);

        testStatementDto = new StatementDto();
        testStatementDto.setStatementId(testStatementId);
        testStatementDto.setAccountNumber(testAccountNumber);
        testStatementDto.setStatementDate(testDate);
        testStatementDto.setFileName("statement.pdf");
        testStatementDto.setFileSize(1024L);
        testStatementDto.setUploadedAt(OffsetDateTime.now());

        testStatementSummary = new StatementSummary();
        testStatementSummary.setStatementId(testStatementId);
        testStatementSummary.setAccountNumber(testAccountNumber);

        testBaseStatement = new BaseStatement();
        testBaseStatement.setStatementId(testStatementId);
        testBaseStatement.setAccountNumber(testAccountNumber);
        testBaseStatement.setDate(testDate.toString());

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber(testAccountNumber);
        testStatement.setStatementDate(testDate);
        testStatement.setUploadedAt(OffsetDateTime.now());

        testRequestInfo = new RequestInfo("127.0.0.1", "JUnit", "test-user");
    }

    @Test
    @DisplayName("getStatementSummaryWithSignedDownloadLinkById - should return summary when statement exists")
    void getStatementSummaryWithSignedDownloadLinkById_Found() {

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);

        String basePath = "http://localhost/files/" + testStatementDto.getFileName();
        SignedLink signedLink = new SignedLink();
        signedLink.setId(UUID.randomUUID());
        signedLink.setStatementId(testStatementId);

        when(signedLinkService.getFilesBaseUrl(testStatementDto.getFileName())).thenReturn(basePath);
        when(signedLinkService.createSignedLink(testStatementId, true, "test-user", basePath))
                .thenReturn(signedLink);
        when(signedLinkService.buildSignedDownloadLink(signedLink, basePath))
                .thenReturn(java.net.URI.create("http://localhost/download/statement.pdf"));

        Optional<StatementSummary> result =
                statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testStatementSummary);
        verify(statementService).getStatementDtoById(testStatementId);
        verify(statementApiMapper).toApi(testStatementDto);
        verify(auditHelper)
                .recordLinkGenerated(
                        eq(testStatementId),
                        eq(testAccountNumber),
                        eq(signedLink.getId()),
                        eq("test-user"),
                        eq("127.0.0.1"),
                        eq("JUnit"));
    }

    @Test
    @DisplayName("getStatementSummaryWithSignedDownloadLinkById - should return empty when statement not found")
    void getStatementSummaryWithSignedDownloadLinkById_NotFound() {

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.getStatementDtoById(testStatementId))
                .thenThrow(new StatementNotFoundException("Not found"));

        Optional<StatementSummary> result =
                statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId);

        assertThat(result).isEmpty();
        verify(statementService).getStatementDtoById(testStatementId);
        verify(statementApiMapper, never()).toApi(any());
        verify(auditHelper).recordStatementNotFound(eq(testStatementId), eq("test-user"), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    @DisplayName("searchByAccount - should use defaults when parameters are null")
    void searchByAccount_DefaultPagination() {
        var dtos = Arrays.asList(testStatementDto);
        var summaries = Arrays.asList(testStatementSummary);

        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(summaries);

        var result = statementQueryService.searchByAccount(testAccountNumber, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testStatementSummary);
        verify(statementService).getStatementsDtoByAccountNumber(testAccountNumber);
    }

    @Test
    @DisplayName("searchByAccount - should apply limit correctly")
    void searchByAccount_WithLimit() {

        var dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        statementQueryService.searchByAccount(testAccountNumber, 5, 0);

        verify(statementApiMapper).toApis(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("searchByAccount - should apply offset correctly")
    void searchByAccount_WithOffset() {

        var dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        statementQueryService.searchByAccount(testAccountNumber, 5, 3);

        verify(statementApiMapper).toApis(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("searchByAccount - should return empty list when no statements found")
    void searchByAccount_NotFound() {

        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenThrow(new StatementNotFoundException("Not found"));

        var result = statementQueryService.searchByAccount(testAccountNumber, 50, 0);

        assertThat(result).isEmpty();
        verify(statementApiMapper, never()).toApis(any());
    }

    @Test
    @DisplayName("searchByAccount - should handle offset beyond list size")
    void searchByAccount_OffsetBeyondSize() {

        var dtos = createMultipleDtos(5);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        statementQueryService.searchByAccount(testAccountNumber, 10, 100);

        verify(statementApiMapper).toApis(argThat(List::isEmpty));
    }

    @Test
    @DisplayName("searchByAccountAndDate - should return statement when found")
    void searchByAccountAndDate_Found() {

        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.of(testStatementDto));
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);

        var result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testStatementSummary);
        verify(statementService).getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate);
    }

    @Test
    @DisplayName("searchByAccountAndDate - should return empty list when not found")
    void searchByAccountAndDate_NotFound() {

        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.empty());

        var result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        assertThat(result).isEmpty();
        verify(statementApiMapper, never()).toApi(any());
    }

    @Test
    @DisplayName("searchByAccountAndDate - invalid date should not reach service (OpenAPI validation)")
    void searchByAccountAndDate_InvalidDateFormat() {
        assertThatThrownBy(() -> statementQueryService.searchByAccountAndDate(testAccountNumber, "invalid-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("searchPaged - should return paged results with all mandatory parameters")
    void searchPaged_WithAllMandatoryParams() {

        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);
        when(statementApiMapper.toBase(any(StatementDto.class))).thenReturn(testBaseStatement);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, 0, 50, null);

        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("searchPaged - should use defaults when pagination parameters are null")
    void searchPaged_DefaultPagination() {

        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);
        when(statementApiMapper.toBase(any(StatementDto.class))).thenReturn(testBaseStatement);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, null, null, null);

        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("searchPaged - should return result for account and date range")
    void searchPaged_AccountAndDate() {

        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);
        when(statementApiMapper.toBase(any(StatementDto.class))).thenReturn(testBaseStatement);

        var result = statementQueryService.searchPaged(testAccountNumber, "2024-01-15", "2024-01-31", 0, 50, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchPaged - should handle valid pagination parameters")
    void searchPaged_ValidPagination() {

        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);
        when(statementApiMapper.toBase(any(StatementDto.class))).thenReturn(testBaseStatement);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, 2, 25, null);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("searchPaged - invalid startDate format should throw exception")
    void searchPaged_InvalidStartDateFormat() {

        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "invalid-date", "2024-01-31", 0, 50, null))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("searchPaged - invalid endDate format should throw exception")
    void searchPaged_InvalidEndDateFormat() {

        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "2024-01-01", "invalid-date", 0, 50, null))
                .isInstanceOf(DateTimeParseException.class);
    }

    private List<StatementDto> createMultipleDtos(int count) {

        List<StatementDto> dtos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StatementDto dto = new StatementDto();
            dto.setStatementId(UUID.randomUUID());
            dto.setAccountNumber(testAccountNumber);
            dtos.add(dto);
        }
        return dtos;
    }
}
