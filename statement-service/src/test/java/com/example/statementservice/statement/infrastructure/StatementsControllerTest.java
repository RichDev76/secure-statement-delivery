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
import com.example.statementservice.statement.download.DownloadOutcome;
import com.example.statementservice.statement.download.DownloadService;
import com.example.statementservice.statement.download.infrastructure.DownloadResponseFactory;
import com.example.statementservice.statement.search.StatementQueryService;
import java.io.ByteArrayInputStream;
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

        testStatementDto = new StatementDto();
        testStatementDto.setStatementId(testStatementId);
        testStatementDto.setAccountNumber("123456789");

        testStatementSummary = new StatementSummary();
        testStatementSummary.setStatementId(testStatementId);
        testStatementSummary.setAccountNumber("123456789");
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return OK response with file content")
    void downloadStatementByFileName_Success() {

        var fileName = "statement-2024-01.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "test-signature";

        var testStream = new ByteArrayInputStream("test content".getBytes());
        DownloadService.DownloadStreamResult successResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(testStream));

        var resource = new InputStreamResource(testStream);
        ResponseEntity<Resource> expectedResponse = ResponseEntity.ok(resource);

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        signature, expires, linkId, fileName, "192.168.1.1", "Mozilla/5.0", "testUser"))
                .thenReturn(successResult);
        when(downloadResponseFactory.build(fileName, successResult)).thenReturn(expectedResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        verify(requestInfoProvider).get();
        verify(downloadService)
                .validateAndStreamDetailed(
                        signature, expires, linkId, fileName, "192.168.1.1", "Mozilla/5.0", "testUser");
        verify(downloadResponseFactory).build(fileName, successResult);
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return FORBIDDEN for invalid signature")
    void downloadStatementByFileName_InvalidSignature() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "invalid-signature";

        DownloadService.DownloadStreamResult failureResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.INVALID_SIGNATURE, Optional.empty());
        ResponseEntity<Resource> forbiddenResponse =
                ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(failureResult);
        when(downloadResponseFactory.build(fileName, failureResult)).thenReturn(forbiddenResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return NOT_FOUND for expired link")
    void downloadStatementByFileName_ExpiredLink() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "expired-signature";

        DownloadService.DownloadStreamResult expiredResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.LINK_EXPIRED, Optional.empty());
        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expiredResult);
        when(downloadResponseFactory.build(fileName, expiredResult)).thenReturn(notFoundResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("downloadStatementByFileName - should use request info from provider")
    void downloadStatementByFileName_UsesRequestInfo() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "signature";

        var customRequestInfo = new RequestInfo("10.0.0.1", "Custom-Agent", "customUser");
        DownloadService.DownloadStreamResult result = new DownloadService.DownloadStreamResult(
                DownloadOutcome.OK, Optional.of(new ByteArrayInputStream(new byte[0])));

        when(requestInfoProvider.get()).thenReturn(customRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
        when(downloadResponseFactory.build(anyString(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        verify(downloadService)
                .validateAndStreamDetailed(
                        signature, expires, linkId, fileName, "10.0.0.1", "Custom-Agent", "customUser");
    }

    @Test
    @DisplayName("downloadStatementByFileName - should pass signature to download service")
    void downloadStatementByFileName_PassesSignature() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "specific-signature-value";

        DownloadService.DownloadStreamResult result = new DownloadService.DownloadStreamResult(
                DownloadOutcome.OK, Optional.of(new ByteArrayInputStream(new byte[0])));

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        eq(signature), anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
        when(downloadResponseFactory.build(anyString(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        verify(downloadService)
                .validateAndStreamDetailed(
                        eq(signature), anyLong(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("downloadStatementByFileName - should handle statement not found")
    void downloadStatementByFileName_StatementNotFound() {

        var fileName = "statement.pdf";
        var expires = 1234567890L;
        var linkId = UUID.randomUUID();
        var signature = "signature";

        DownloadService.DownloadStreamResult notFoundResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(
                        anyString(), anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(notFoundResult);
        when(downloadResponseFactory.build(fileName, notFoundResult)).thenReturn(notFoundResponse);

        var response = statementsController.downloadStatementByFileName(fileName, expires, linkId, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should return OK with statement summary when found")
    void getDownloadSignedLinkById_Found() {

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo))
                .thenReturn(Optional.of(testStatementDto));
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);

        ResponseEntity<StatementSummary> response =
                statementsController.getDownloadSignedLinkById(testStatementId, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(testStatementId);
        assertThat(response.getBody().getAccountNumber()).isEqualTo("123456789");

        verify(statementQueryService).getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should throw StatementNotFoundException when not found")
    void getDownloadSignedLinkById_NotFound() {

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, null))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement(s) not found for Id: " + testStatementId);

        verify(statementQueryService).getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should pass correct statement ID to service")
    void getDownloadSignedLinkById_PassesCorrectId() {

        var specificId = UUID.randomUUID();
        var dto = new StatementDto();
        dto.setStatementId(specificId);
        var summary = new StatementSummary();
        summary.setStatementId(specificId);

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(specificId, testRequestInfo))
                .thenReturn(Optional.of(dto));
        when(statementApiMapper.toApi(dto)).thenReturn(summary);

        var response = statementsController.getDownloadSignedLinkById(specificId, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(specificId);
        verify(statementQueryService).getStatementWithSignedDownloadLinkById(eq(specificId), eq(testRequestInfo));
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should propagate service exceptions")
    void getDownloadSignedLinkById_ServiceException() {

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementQueryService.getStatementWithSignedDownloadLinkById(any(), any()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementQueryService).getStatementWithSignedDownloadLinkById(testStatementId, testRequestInfo);
    }

    private Page<StatementDto> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
    }

    @Test
    @DisplayName("searchStatements - should return OK with results when all mandatory parameters provided")
    void searchStatements_WithAllMandatoryParameters() {

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
    @DisplayName("searchStatements - should pass pagination parameters to service")
    void searchStatements_WithPagination() {

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
    @DisplayName("searchStatements - should pass sort parameter to service")
    void searchStatements_WithSort() {

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
    @DisplayName("searchStatements - should pass all parameters to service")
    void searchStatements_AllParameters() {

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
    @DisplayName("searchStatements - should propagate service exceptions")
    void searchStatements_ServiceException() {

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
    @DisplayName("searchStatements - should handle empty result page")
    void searchStatements_EmptyResults() {

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
    @DisplayName("searchStatements - should handle large result sets")
    void searchStatements_LargeResults() {

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
