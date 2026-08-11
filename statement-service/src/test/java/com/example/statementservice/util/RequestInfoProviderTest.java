package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestInfoProvider Tests")
class RequestInfoProviderTest {

    @InjectMocks
    private RequestInfoProvider requestInfoProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletRequestAttributes requestAttributes;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<RequestContextHolder> requestContextHolderMock;
    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    @BeforeEach
    void setUp() {
        requestContextHolderMock = mockStatic(RequestContextHolder.class);
        securityContextHolderMock = mockStatic(SecurityContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        requestContextHolderMock.close();
        securityContextHolderMock.close();
    }

    @Test
    @DisplayName("Should get request info with authenticated user")
    void testGet_WithAuthenticatedUser() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("john.doe");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("192.168.1.100", result.getClientIp());
        assertEquals("Mozilla/5.0", result.getUserAgent());
        assertEquals("john.doe", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should get request info with unauthenticated user")
    void testGet_WithUnauthenticatedUser() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("User-Agent")).thenReturn("Chrome/90.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("10.0.0.5", result.getClientIp());
        assertEquals("Chrome/90.0", result.getUserAgent());
        assertEquals("system", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should get request info when authentication is null")
    void testGet_NullAuthentication() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("172.16.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Safari/14.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("172.16.0.1", result.getClientIp());
        assertEquals("Safari/14.0", result.getUserAgent());
        assertEquals("system", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should get request info when security context is null")
    void testGet_NullSecurityContext() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("192.168.0.50");
        when(request.getHeader("User-Agent")).thenReturn("Edge/91.0");
        when(SecurityContextHolder.getContext()).thenReturn(null);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("192.168.0.50", result.getClientIp());
        assertEquals("Edge/91.0", result.getUserAgent());
        assertEquals("system", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle exception during authentication resolution")
    void testGet_ExceptionDuringAuthResolution() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("192.168.10.20");
        when(request.getHeader("User-Agent")).thenReturn("Firefox/89.0");
        when(SecurityContextHolder.getContext()).thenThrow(new RuntimeException("Auth error"));
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("192.168.10.20", result.getClientIp());
        assertEquals("Firefox/89.0", result.getUserAgent());
        assertEquals("system", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should return unknown when request attributes are null")
    void testGet_NullRequestAttributes() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.getClientIp());
        assertEquals("unknown", result.getUserAgent());
        assertEquals("admin", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should return unknown when request is null")
    void testGet_NullRequest() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("testuser");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.getClientIp());
        assertEquals("unknown", result.getUserAgent());
        assertEquals("testuser", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle null User-Agent header")
    void testGet_NullUserAgent() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("apiuser");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("127.0.0.1", result.getClientIp());
        assertNull(result.getUserAgent()); // Returns null when header is null but request is not
        assertEquals("apiuser", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle null remote address")
    void testGet_NullRemoteAddress() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("Postman/7.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("service-account");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertNull(result.getClientIp()); // Returns null when remote address is null but request is not
        assertEquals("Postman/7.0", result.getUserAgent());
        assertEquals("service-account", result.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle all null values")
    void testGet_AllNullValues() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(null);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.getClientIp());
        assertEquals("unknown", result.getUserAgent());
        assertEquals("system", result.getPerformedBy());
    }
}
