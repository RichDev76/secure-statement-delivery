package com.example.statementservice.statement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.infrastructure.web.RequestInfoProvider;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.shared.RequestInfo;
import com.example.statementservice.statement.StatementDto;
import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.download.DownloadAttempt;
import com.example.statementservice.statement.download.DownloadOutcome;
import com.example.statementservice.statement.download.DownloadService;
import com.example.statementservice.statement.download.infrastructure.DownloadResponseFactory;
import com.example.statementservice.statement.search.StatementQueryService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementsController Unit Tests")
class StatementsControllerTest {

    @Mock
    private DownloadService downloadService;

    @Mock
    private StatementQueryService statementQueryService;

    @Mock
    private StatementApiMapper statementApiMapper;

    @Mock
    private RequestInfoProvider requestInfoProvider;

    @Mock
    private DownloadResponseFactory downloadResponseFactory;

    @InjectMocks
    private StatementsController statementsController;

    private RequestInfo testRequestInfo;
    private UUID testStatementId;
    private StatementDto testStatementDto;
    private StatementSummary testStatementSummary;

    @BeforeEach
    void setUp() {
        testRequestInfo = new RequestInfo("192.168.1.1", "Mozilla/5.0", "testUser");
        testStatementId = UUID.randomUUID();

        testStatementDto = StatementDto.builder()
                .statementId(testStatementId)
                .accountNumber("123456789")
                .build();

        testStatementSummary = new StatementSummary();
        testStatementSummary.setStatementId(testStatementId);
        testStatementSummary.setAccountNumber("123456789");
    }

    @Test
    void GivenValidSignedLink_WhenDownloadByFileName_ThenReturnsOkWithFileContent() {

        var fileName = "statement-2024-01.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "test-signature";

        var testStream = new ByteArrayInputStream("test content".getBytes());
        var resource = new InputStreamResource(testStream);
        ResponseEntity<Resource> expectedResponse = ResponseEntity.ok(resource);

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        eq(signature),
                        eq(expires),
                        eq(linkId),
                        eq(fileName),
                        eq("192.168.1.1"),
                        eq("Mozilla/5.0"),
                        eq("testUser"),
                        any()))
                .thenAnswer(invocation -> {
                    Function<InputStream, ResponseEntity<Resource>> onSuccess = invocation.getArgument(7);
                    return DownloadAttempt.success(onSuccess.apply(testStream));
                });
        when(downloadResponseFactory.ok(fileName, testStream)).thenReturn(expectedResponse);
        when(downloadResponseFactory.build(DownloadOutcome.OK, expectedResponse))
                .thenReturn(expectedResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        verify(requestInfoProvider).get();
        verify(downloadService)
                .validateAndStreamDetailed(
                        eq(signature),
                        eq(expires),
                        eq(linkId),
                        eq(fileName),
                        eq("192.168.1.1"),
                        eq("Mozilla/5.0"),
                        eq("testUser"),
                        any());
        verify(downloadResponseFactory).build(DownloadOutcome.OK, expectedResponse);
    }

    @Test
    void GivenInvalidSignature_WhenDownloadByFileName_ThenReturnsForbiddenWithoutBody() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "invalid-signature";

        ResponseEntity<Resource> forbiddenResponse =
                ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(DownloadAttempt.failure(DownloadOutcome.INVALID_SIGNATURE));
        when(downloadResponseFactory.build(DownloadOutcome.INVALID_SIGNATURE, null))
                .thenReturn(forbiddenResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void GivenExpiredLink_WhenDownloadByFileName_ThenReturnsNotFound() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "expired-signature";

        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(DownloadAttempt.failure(DownloadOutcome.LINK_EXPIRED));
        when(downloadResponseFactory.build(DownloadOutcome.LINK_EXPIRED, null)).thenReturn(notFoundResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void GivenDownloadRequest_WhenDownloadByFileName_ThenRequestInfoFromProviderIsUsed() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "signature";

        var customRequestInfo = new RequestInfo("10.0.0.1", "Custom-Agent", "customUser");

        when(requestInfoProvider.get()).thenReturn(customRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        DownloadAttempt.success(ResponseEntity.<Resource>ok().build()));
        when(downloadResponseFactory.build(any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        verify(downloadService)
                .validateAndStreamDetailed(
                        eq(signature),
                        eq(expires),
                        eq(linkId),
                        eq(fileName),
                        eq("10.0.0.1"),
                        eq("Custom-Agent"),
                        eq("customUser"),
                        any());
    }

    @Test
    void GivenDownloadRequest_WhenDownloadByFileName_ThenSignatureIsPassedToService() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "specific-signature-value";

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        eq(signature), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        DownloadAttempt.success(ResponseEntity.<Resource>ok().build()));
        when(downloadResponseFactory.build(any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        verify(downloadService)
                .validateAndStreamDetailed(
                        eq(signature), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void GivenUnknownStatement_WhenDownloadByFileName_ThenReturnsNotFound() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "signature";

        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(DownloadAttempt.failure(DownloadOutcome.STATEMENT_NOT_FOUND));
        when(downloadResponseFactory.build(DownloadOutcome.STATEMENT_NOT_FOUND, null))
                .thenReturn(notFoundResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void GivenExistingStatement_WhenGettingSignedLinkById_ThenReturnsOkWithSummary() {

        var accountNumber = "123456789";
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(
                        testStatementId, accountNumber, testRequestInfo))
                .thenReturn(Optional.of(testStatementDto));
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);

        ResponseEntity<StatementSummary> response =
                statementsController.getDownloadSignedLinkById(testStatementId, accountNumber, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(testStatementId);
        assertThat(response.getBody().getAccountNumber()).isEqualTo("123456789");

        verify(statementQueryService)
                .getStatementWithSignedDownloadLinkById(testStatementId, accountNumber, testRequestInfo);
    }

    @Test
    void GivenUnknownStatement_WhenGettingSignedLinkById_ThenThrowsStatementNotFoundException() {

        var accountNumber = "123456789";
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(
                        testStatementId, accountNumber, testRequestInfo))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, accountNumber, null))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement(s) not found for Id: " + testStatementId);

        verify(statementQueryService)
                .getStatementWithSignedDownloadLinkById(testStatementId, accountNumber, testRequestInfo);
    }

    @Test
    void GivenSpecificStatementId_WhenGettingSignedLinkById_ThenThatIdReachesService() {

        var specificId = UUID.randomUUID();
        var accountNumber = "123456789";
        var dto = StatementDto.builder().statementId(specificId).build();
        var summary = new StatementSummary();
        summary.setStatementId(specificId);

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(specificId, accountNumber, testRequestInfo))
                .thenReturn(Optional.of(dto));
        when(statementApiMapper.toApi(dto)).thenReturn(summary);

        var response = statementsController.getDownloadSignedLinkById(specificId, accountNumber, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(specificId);
        verify(statementQueryService)
                .getStatementWithSignedDownloadLinkById(eq(specificId), eq(accountNumber), eq(testRequestInfo));
    }

    @Test
    void GivenServiceThrows_WhenGettingSignedLinkById_ThenExceptionPropagates() {

        var accountNumber = "123456789";
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(any(), any(), any()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, accountNumber, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementQueryService)
                .getStatementWithSignedDownloadLinkById(testStatementId, accountNumber, testRequestInfo);
    }

    private Page<StatementDto> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
    }

    @Test
    void GivenMandatoryParameters_WhenSearchingStatements_ThenReturnsOkPage() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, null, null, null))
                .thenReturn(emptyPage());
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        var response = statementsController.searchStatements(accountNumber, startDate, endDate, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(statementQueryService).searchPaged(accountNumber, startDate, endDate, null, null, null);
    }

    @Test
    void GivenPaginationParameters_WhenSearchingStatements_ThenTheyReachTheService() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        var page = 1;
        var size = 25;
        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, page, size, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        statementsController.searchStatements(accountNumber, startDate, endDate, null, page, size, null);

        verify(statementQueryService)
                .searchPaged(eq(accountNumber), eq(startDate), eq(endDate), eq(page), eq(size), isNull());
    }

    @Test
    void GivenSortParameter_WhenSearchingStatements_ThenItReachesTheService() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        var sort = "uploadedAt:desc";
        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, null, null, sort))
                .thenReturn(emptyPage());
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        statementsController.searchStatements(accountNumber, startDate, endDate, null, null, null, sort);

        verify(statementQueryService)
                .searchPaged(eq(accountNumber), eq(startDate), eq(endDate), isNull(), isNull(), eq(sort));
    }

    @Test
    void GivenAllParameters_WhenSearchingStatements_ThenAllReachTheService() {

        var accountNumber = "123456789";
        var startDate = "2024-01-15";
        var endDate = "2024-01-31";
        var page = 2;
        var size = 50;
        var sort = "statementDate:asc";

        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, page, size, sort))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        statementsController.searchStatements(accountNumber, startDate, endDate, null, page, size, sort);

        verify(statementQueryService)
                .searchPaged(eq(accountNumber), eq(startDate), eq(endDate), eq(page), eq(size), eq(sort));
    }

    @Test
    void GivenServiceThrows_WhenSearchingStatements_ThenExceptionPropagates() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        when(statementQueryService.searchPaged(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() -> statementsController.searchStatements(
                        accountNumber, startDate, endDate, null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementQueryService).searchPaged(accountNumber, startDate, endDate, null, null, null);
    }

    @Test
    void GivenNoMatches_WhenSearchingStatements_ThenReturnsOkWithEmptyPage() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";

        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, null, null, null))
                .thenReturn(emptyPage());
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        var response = statementsController.searchStatements(accountNumber, startDate, endDate, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0L);
    }

    @Test
    void GivenLargeResultSet_WhenSearchingStatements_ThenPageMetadataIsMapped() {

        var accountNumber = "123456789";
        var startDate = "2024-01-01";
        var endDate = "2024-01-31";
        var largePage = new PageImpl<StatementDto>(List.of(), PageRequest.of(0, 50), 1000L);

        when(statementQueryService.searchPaged(accountNumber, startDate, endDate, null, null, null))
                .thenReturn(largePage);
        when(statementApiMapper.toBases(List.of())).thenReturn(List.of());

        var response = statementsController.searchStatements(accountNumber, startDate, endDate, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1000L);
        assertThat(response.getBody().getTotalPages()).isEqualTo(20);
    }
}
