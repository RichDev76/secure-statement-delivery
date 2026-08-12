package com.example.statementservice.statement.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.search.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class SearchExceptionHandlerTest {

    private final SearchExceptionHandler handler = new SearchExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/statements/search");

    @Test
    void GivenInvalidInputException_WhenHandleInvalidInput_ThenReturnsBadRequestWithOwnTitleAndErrorCode() {
        // Given
        var ex = new InvalidInputException("startDate cannot be after endDate");

        // When
        var response = handler.handleInvalidInput(ex, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getTitle()).isEqualTo("Invalid Search Parameters");
        assertThat(response.getDetail()).isEqualTo("startDate cannot be after endDate");
        assertThat(response.getProperties()).containsEntry("errorCode", "INVALID_SEARCH_PARAMETERS");
    }
}
