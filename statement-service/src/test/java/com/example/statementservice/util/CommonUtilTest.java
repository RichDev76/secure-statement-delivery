package com.example.statementservice.util;

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
@DisplayName("CommonUtil Tests")
class CommonUtilTest {

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("Should build URI with HTTP and default port 80")
    void testBuildProblemDetailTypeURI_HttpDefaultPort() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://example.com", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTPS and default port 443")
    void testBuildProblemDetailTypeURI_HttpsDefaultPort() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(443);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("https://example.com", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTP and custom port")
    void testBuildProblemDetailTypeURI_HttpCustomPort() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8080);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://example.com:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTPS and custom port")
    void testBuildProblemDetailTypeURI_HttpsCustomPort() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8443);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("https://example.com:8443", result.toString());
    }

    @Test
    @DisplayName("Should build URI with context path")
    void testBuildProblemDetailTypeURI_WithContextPath() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        var contextPath = "/api/v1";
        var result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("http://example.com/api/v1", result.toString());
    }

    @Test
    @DisplayName("Should build URI with context path and custom port")
    void testBuildProblemDetailTypeURI_WithContextPathAndCustomPort() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.example.com");
        when(request.getServerPort()).thenReturn(9443);
        var contextPath = "/statement-service";
        var result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("https://api.example.com:9443/statement-service", result.toString());
    }

    @Test
    @DisplayName("Should build URI with empty context path")
    void testBuildProblemDetailTypeURI_EmptyContextPath() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        var contextPath = "";
        var result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);
        assertNotNull(result);
        assertEquals("http://localhost:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with localhost")
    void testBuildProblemDetailTypeURI_Localhost() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(80);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://localhost", result.toString());
    }

    @Test
    @DisplayName("Should build URI with IP address")
    void testBuildProblemDetailTypeURI_IpAddress() {

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("192.168.1.100");
        when(request.getServerPort()).thenReturn(8080);
        var result = CommonUtil.buildProblemDetailTypeURI(request, null);
        assertNotNull(result);
        assertEquals("http://192.168.1.100:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with subdomain")
    void testBuildProblemDetailTypeURI_Subdomain() {

        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.dev.example.com");
        when(request.getServerPort()).thenReturn(443);
        var result = CommonUtil.buildProblemDetailTypeURI(request, "/errors");
        assertNotNull(result);
        assertEquals("https://api.dev.example.com/errors", result.toString());
    }
}
