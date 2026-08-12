package com.example.statementservice.statement.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementDto;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StatementQueryServiceTest {

    @Mock
    private StatementService statementService;

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private AuditHelper auditHelper;

    @InjectMocks
    private StatementQueryService statementQueryService;

    private UUID testStatementId;
    private String testAccountNumber;
    private LocalDate testDate;
    private StatementDto testStatementDto;
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

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber(testAccountNumber);
        testStatement.setStatementDate(testDate);
        testStatement.setUploadedAt(OffsetDateTime.now());

        testRequestInfo = new RequestInfo("127.0.0.1", "JUnit", "test-user");
    }

    @Test
    void GivenExistingStatement_WhenGettingSignedDownloadLink_ThenDtoWithDownloadLinkIsReturned() {
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);

        String basePath = "http://localhost/files/" + testStatementDto.getFileName();
        SignedLink signedLink = new SignedLink();
        signedLink.setId(UUID.randomUUID());
        signedLink.setStatementId(testStatementId);

        when(signedLinkService.getFilesBaseUrl(testStatementDto.getFileName())).thenReturn(basePath);
        when(signedLinkService.createSignedLink(testStatementId, true, "test-user", basePath))
                .thenReturn(signedLink);
        when(signedLinkService.buildSignedDownloadLink(signedLink, basePath))
                .thenReturn(java.net.URI.create("http://localhost/download/statement.pdf"));

        Optional<StatementDto> result =
                statementQueryService.getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testStatementDto);
        assertThat(result.get().getDownloadLink())
                .isEqualTo(java.net.URI.create("http://localhost/download/statement.pdf"));
        verify(statementService).getStatementDtoById(testStatementId);
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
    void GivenMissingStatement_WhenGettingSignedDownloadLink_ThenEmptyIsReturned() {
        when(statementService.getStatementDtoById(testStatementId))
                .thenThrow(new StatementNotFoundException("Not found"));

        Optional<StatementDto> result =
                statementQueryService.getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo);

        assertThat(result).isEmpty();
        verify(statementService).getStatementDtoById(testStatementId);
        verify(auditHelper).recordStatementNotFound(eq(testStatementId), eq("test-user"), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void GivenNullPagination_WhenSearchingByAccount_ThenDefaultsApply() {
        var dtos = Arrays.asList(testStatementDto);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);

        var result = statementQueryService.searchByAccount(testAccountNumber, null, null);

        assertThat(result).containsExactly(testStatementDto);
        verify(statementService).getStatementsDtoByAccountNumber(testAccountNumber);
    }

    @Test
    void GivenLimit_WhenSearchingByAccount_ThenResultIsTruncatedToLimit() {
        var dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);

        var result = statementQueryService.searchByAccount(testAccountNumber, 5, 0);

        assertThat(result).hasSize(5);
    }

    @Test
    void GivenOffset_WhenSearchingByAccount_ThenResultSkipsOffsetEntries() {
        var dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);

        var result = statementQueryService.searchByAccount(testAccountNumber, 5, 3);

        assertThat(result).hasSize(5);
        assertThat(result).isEqualTo(dtos.subList(3, 8));
    }

    @Test
    void GivenNoStatementsFound_WhenSearchingByAccount_ThenEmptyListIsReturned() {
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenThrow(new StatementNotFoundException("Not found"));

        var result = statementQueryService.searchByAccount(testAccountNumber, 50, 0);

        assertThat(result).isEmpty();
    }

    @Test
    void GivenOffsetBeyondListSize_WhenSearchingByAccount_ThenEmptyListIsReturned() {
        var dtos = createMultipleDtos(5);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);

        var result = statementQueryService.searchByAccount(testAccountNumber, 10, 100);

        assertThat(result).isEmpty();
    }

    @Test
    void GivenExistingAccountAndDate_WhenSearchingByAccountAndDate_ThenSingleDtoIsReturned() {
        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.of(testStatementDto));

        var result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        assertThat(result).containsExactly(testStatementDto);
        verify(statementService).getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate);
    }

    @Test
    void GivenNoMatchForAccountAndDate_WhenSearchingByAccountAndDate_ThenEmptyListIsReturned() {
        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.empty());

        var result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        assertThat(result).isEmpty();
    }

    @Test
    void GivenMalformedDate_WhenSearchingByAccountAndDate_ThenDateTimeParseExceptionIsThrown() {
        assertThatThrownBy(() -> statementQueryService.searchByAccountAndDate(testAccountNumber, "invalid-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void GivenAllMandatoryParams_WhenSearchingPaged_ThenDomainPageCarriesContentAndMetadata() {
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, 0, 50, null);

        assertThat(result).isNotNull();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(testStatementDto);
    }

    @Test
    void GivenNullPagination_WhenSearchingPaged_ThenDefaultsOfPageZeroSizeFiftyApply() {
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, null, null, null);

        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(50);
    }

    @Test
    void GivenAccountAndDateRange_WhenSearchingPaged_ThenMatchingContentIsReturned() {
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        var result = statementQueryService.searchPaged(testAccountNumber, "2024-01-15", "2024-01-31", 0, 50, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void GivenValidPaginationParameters_WhenSearchingPaged_ThenTheyArePropagated() {
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumberAndDateRange(
                        eq(testAccountNumber), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        var result = statementQueryService.searchPaged(testAccountNumber, startDate, endDate, 2, 25, null);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(25);
    }

    @Test
    void GivenMalformedStartDate_WhenSearchingPaged_ThenDateTimeParseExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "invalid-date", "2024-01-31", 0, 50, null))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void GivenMalformedEndDate_WhenSearchingPaged_ThenDateTimeParseExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "2024-01-01", "invalid-date", 0, 50, null))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void GivenStartDateAfterEndDate_WhenSearchingPaged_ThenInvalidInputExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "2024-02-01", "2024-01-01", 0, 50, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("startDate cannot be after endDate");
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
