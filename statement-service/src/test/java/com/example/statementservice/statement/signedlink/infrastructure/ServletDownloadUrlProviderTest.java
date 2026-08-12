package com.example.statementservice.statement.signedlink.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ServletDownloadUrlProviderTest {

    private final ServletDownloadUrlProvider provider = new ServletDownloadUrlProvider();

    @BeforeEach
    void bindServletRequest() {
        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("statements.example.com");
        request.setServerPort(8443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ReflectionTestUtils.setField(provider, "downloadPath", "/api/v1/statements/download/");
    }

    @AfterEach
    void clearServletRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void GivenBoundServletRequest_WhenResolvingDownloadBaseUrl_ThenSchemeHostPortAndPathAreCombined() {
        // Given

        // When
        var url = provider.downloadBaseUrl("statement-2026-07.pdf");

        // Then
        assertThat(url)
                .isEqualTo("https://statements.example.com:8443/api/v1/statements/download/statement-2026-07.pdf");
    }

    @Test
    void GivenDefaultPortRequest_WhenResolvingDownloadBaseUrl_ThenPortIsOmitted() {
        // Given
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        var url = provider.downloadBaseUrl("file.pdf");

        // Then
        assertThat(url).isEqualTo("http://localhost/api/v1/statements/download/file.pdf");
    }
}
