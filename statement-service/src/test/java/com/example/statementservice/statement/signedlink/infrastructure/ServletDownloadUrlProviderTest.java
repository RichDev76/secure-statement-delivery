package com.example.statementservice.statement.signedlink.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ServletDownloadUrlProviderTest {

    private SignedLinkProperties properties;
    private ServletDownloadUrlProvider provider;

    @BeforeEach
    void bindServletRequest() {
        properties = new SignedLinkProperties();
        provider = new ServletDownloadUrlProvider(properties);
        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("statements.example.com");
        request.setServerPort(8443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearServletRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void GivenBoundServletRequest_WhenResolvingAbsoluteUrl_ThenSchemeHostPortAndRelativePathAreCombined() {
        // When
        var url = provider.toAbsoluteUrl("/api/v1/statements/download/statement-2026-07.pdf");

        // Then
        assertThat(url)
                .isEqualTo("https://statements.example.com:8443/api/v1/statements/download/statement-2026-07.pdf");
    }

    @Test
    void GivenDefaultPortRequest_WhenResolvingAbsoluteUrl_ThenPortIsOmitted() {
        // Given
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        var url = provider.toAbsoluteUrl("/api/v1/statements/download/file.pdf");

        // Then
        assertThat(url).isEqualTo("http://localhost/api/v1/statements/download/file.pdf");
    }

    @Test
    void GivenExternalBaseUrlConfigured_WhenResolvingAbsoluteUrl_ThenConfiguredBaseWinsOverRequestHost() {
        // Given: the request arrived on an internal host, but the public base is configured
        properties.setExternalBaseUrl("https://statements.public.example.com");

        // When
        var url = provider.toAbsoluteUrl("/api/v1/statements/download/file.pdf");

        // Then
        assertThat(url).isEqualTo("https://statements.public.example.com/api/v1/statements/download/file.pdf");
    }

    @Test
    void GivenExternalBaseUrlWithTrailingSlash_WhenResolvingAbsoluteUrl_ThenNoDoubleSlashInResult() {
        // Given
        properties.setExternalBaseUrl("https://statements.public.example.com/");

        // When
        var url = provider.toAbsoluteUrl("/api/v1/statements/download/file.pdf");

        // Then
        assertThat(url).isEqualTo("https://statements.public.example.com/api/v1/statements/download/file.pdf");
    }
}
