package com.example.statementservice.statement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.StatementNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class StatementExceptionHandlerTest {

    private final StatementExceptionHandler handler = new StatementExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/statements/00000000-0000-0000-0000-000000000000");

    @Test
    void GivenStatementNotFoundException_WhenHandleStatementNotFound_ThenReturnsNotFoundWithStatementNotFoundCode() {
        // Given
        var ex = new StatementNotFoundException("Statement not found for id: 123");

        // When
        var response = handler.handleStatementNotFound(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getTitle()).isEqualTo("Not Found");
        assertThat(response.getDetail()).isEqualTo(ex.getMessage());
        assertThat(response.getProperties()).containsEntry("errorCode", "STATEMENT_NOT_FOUND");
    }
}
