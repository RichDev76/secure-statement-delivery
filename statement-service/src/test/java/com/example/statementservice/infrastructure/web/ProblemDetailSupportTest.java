package com.example.statementservice.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemDetailSupport Tests")
class ProblemDetailSupportTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void GivenHttpDefaultPort_WhenBuildingProblemTypeUri_ThenPortIsOmitted() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://example.com", result.toString());
    }

    @Test
    void GivenHttpsDefaultPort_WhenBuildingProblemTypeUri_ThenPortIsOmitted() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(443);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("https://example.com", result.toString());
    }

    @Test
    void GivenHttpCustomPort_WhenBuildingProblemTypeUri_ThenPortIsIncluded() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8080);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://example.com:8080", result.toString());
    }

    @Test
    void GivenHttpsCustomPort_WhenBuildingProblemTypeUri_ThenPortIsIncluded() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8443);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("https://example.com:8443", result.toString());
    }

    @Test
    void GivenContextPath_WhenBuildingProblemTypeUri_ThenContextPathIsIncluded() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        var contextPath = "/api/v1";
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("http://example.com/api/v1", result.toString());
    }

    @Test
    void GivenContextPathAndCustomPort_WhenBuildingProblemTypeUri_ThenBothAreIncluded() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.example.com");
        when(request.getServerPort()).thenReturn(9443);
        var contextPath = "/statement-service";
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("https://api.example.com:9443/statement-service", result.toString());
    }

    @Test
    void GivenEmptyContextPath_WhenBuildingProblemTypeUri_ThenUriHasNoContextPath() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        var contextPath = "";
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("http://localhost:8080", result.toString());
    }

    @Test
    void GivenLocalhostHost_WhenBuildingProblemTypeUri_ThenLocalhostUriIsBuilt() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(80);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://localhost", result.toString());
    }

    @Test
    void GivenIpAddressHost_WhenBuildingProblemTypeUri_ThenIpUriIsBuilt() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("192.168.1.100");
        when(request.getServerPort()).thenReturn(8080);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://192.168.1.100:8080", result.toString());
    }

    @Test
    void GivenSubdomainHost_WhenBuildingProblemTypeUri_ThenSubdomainUriIsBuilt() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.dev.example.com");
        when(request.getServerPort()).thenReturn(443);
        var result = ProblemDetailSupport.buildProblemDetailTypeURI(request, "/errors");
        assertNotNull(result);
        assertEquals("https://api.dev.example.com/errors", result.toString());
    }
}
