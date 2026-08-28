package com.example.statementservice.statement.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.shared.InvalidDateException;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.Statement;
import com.example.statementservice.statement.StatementDto;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.StatementService;
import com.example.statementservice.statement.signedlink.SignedLink;
import com.example.statementservice.statement.signedlink.SignedLinkGenerationException;
import com.example.statementservice.statement.signedlink.SignedLinkService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
    private StatementSearchAuditRecorder auditRecorder;

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

        testStatementDto = StatementDto.builder()
                .statementId(testStatementId)
                .accountNumber(testAccountNumber)
                .statementDate(testDate)
                .fileName("statement.pdf")
                .fileSize(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();

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

        String fileName = testStatementDto.fileName();
        SignedLink signedLink = new SignedLink();
        signedLink.setId(UUID.randomUUID());
        signedLink.setStatementId(testStatementId);

        when(signedLinkService.createSignedLink(testStatementId, "test-user", fileName))
                .thenReturn(signedLink);
        when(signedLinkService.buildSignedDownloadLink(signedLink, fileName))
                .thenReturn(java.net.URI.create("http://localhost/download/statement.pdf"));

        Optional<StatementDto> result = statementQueryService.getStatementWithSignedDownloadLinkById(
                testStatementId, testAccountNumber, testRequestInfo);

        assertThat(result).isPresent();
        assertThat(result.get())
                .isEqualTo(testStatementDto.withDownloadLink(
                        java.net.URI.create("http://localhost/download/statement.pdf")));
        verify(statementService).getStatementDtoById(testStatementId);
        verify(auditRecorder)
                .recordLinkGenerated(
                        eq(testStatementId),
                        eq(testAccountNumber),
                        eq(signedLink.getId()),
                        eq("test-user"),
                        eq("127.0.0.1"),
                        eq("JUnit"));
    }

    @Test
    void
            GivenSignedLinkCreationFails_WhenGettingSignedDownloadLink_ThenDtoWithoutDownloadLinkIsReturnedAndFailureAudited() {
        // Given
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);
        String fileName = testStatementDto.fileName();
        SignedLinkGenerationException linkFailure =
                new SignedLinkGenerationException("signing key unavailable", new RuntimeException("cause"));
        when(signedLinkService.createSignedLink(testStatementId, "test-user", fileName))
                .thenThrow(linkFailure);

        // When
        Optional<StatementDto> result = statementQueryService.getStatementWithSignedDownloadLinkById(
                testStatementId, testAccountNumber, testRequestInfo);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().downloadLink()).isNull();
        verify(auditRecorder)
                .recordLinkGenerationFailed(
                        eq(testStatementId),
                        eq(testAccountNumber),
                        eq("test-user"),
                        eq(linkFailure),
                        eq("127.0.0.1"),
                        eq("JUnit"));
    }

    @Test
    void
            GivenUnexpectedBugDuringLinkGeneration_WhenGettingSignedDownloadLink_ThenExceptionPropagatesInsteadOfBeingSwallowed() {
        // Given: a NullPointerException is not a known signed-link-generation failure mode -
        // it must not be masked as a routine "link generation failed" audit event.
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);
        String fileName = testStatementDto.fileName();
        NullPointerException bug = new NullPointerException("unexpected null");
        when(signedLinkService.createSignedLink(testStatementId, "test-user", fileName))
                .thenThrow(bug);

        // When / Then
        assertThatThrownBy(() -> statementQueryService.getStatementWithSignedDownloadLinkById(
                        testStatementId, testAccountNumber, testRequestInfo))
                .isSameAs(bug);
        verify(auditRecorder, never()).recordLinkGenerationFailed(any(), any(), any(), any(), any(), any());
        verify(auditRecorder)
                .recordUnexpectedError(
                        eq(testStatementId), isNull(), eq("test-user"), eq(bug), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void GivenMissingStatement_WhenGettingSignedDownloadLink_ThenEmptyIsReturned() {
        when(statementService.getStatementDtoById(testStatementId))
                .thenThrow(new StatementNotFoundException("Not found"));

        Optional<StatementDto> result = statementQueryService.getStatementWithSignedDownloadLinkById(
                testStatementId, testAccountNumber, testRequestInfo);

        assertThat(result).isEmpty();
        verify(statementService).getStatementDtoById(testStatementId);
        verify(auditRecorder)
                .recordStatementNotFound(eq(testStatementId), eq("test-user"), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void
            GivenStatementBelongsToDifferentAccount_WhenGettingSignedDownloadLink_ThenEmptyIsReturnedAndAuditedAsNotFound() {
        // Given
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);

        // When
        Optional<StatementDto> result = statementQueryService.getStatementWithSignedDownloadLinkById(
                testStatementId, "999999999", testRequestInfo);

        // Then
        assertThat(result).isEmpty();
        verify(auditRecorder)
                .recordStatementNotFound(eq(testStatementId), eq("test-user"), eq("127.0.0.1"), eq("JUnit"));
        verify(signedLinkService, never()).createSignedLink(any(UUID.class), any(String.class), any(String.class));
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
    void GivenMalformedStartDate_WhenSearchingPaged_ThenInvalidDateExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "invalid-date", "2024-01-31", 0, 50, null))
                .isInstanceOf(InvalidDateException.class);
    }

    @Test
    void GivenMalformedEndDate_WhenSearchingPaged_ThenInvalidDateExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "2024-01-01", "invalid-date", 0, 50, null))
                .isInstanceOf(InvalidDateException.class);
    }

    @Test
    void GivenStartDateAfterEndDate_WhenSearchingPaged_ThenInvalidInputExceptionIsThrown() {
        assertThatThrownBy(() ->
                        statementQueryService.searchPaged(testAccountNumber, "2024-02-01", "2024-01-01", 0, 50, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("startDate cannot be after endDate");
    }
}
