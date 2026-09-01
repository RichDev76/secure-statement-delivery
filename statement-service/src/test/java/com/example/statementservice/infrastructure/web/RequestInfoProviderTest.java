package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.support.LogCapture;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    void GivenAuthenticatedUser_WhenGettingRequestInfo_ThenPerformedByIsUsername() {
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
        assertEquals("192.168.1.100", result.clientIp());
        assertEquals("Mozilla/5.0", result.userAgent());
        assertEquals("john.doe", result.performedBy());
    }

    @Test
    void GivenUnauthenticatedUser_WhenGettingRequestInfo_ThenPerformedByIsSystem() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("User-Agent")).thenReturn("Chrome/90.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("10.0.0.5", result.clientIp());
        assertEquals("Chrome/90.0", result.userAgent());
        assertEquals("system", result.performedBy());
    }

    @Test
    void GivenNullAuthentication_WhenGettingRequestInfo_ThenPerformedByIsSystem() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("172.16.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Safari/14.0");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("172.16.0.1", result.clientIp());
        assertEquals("Safari/14.0", result.userAgent());
        assertEquals("system", result.performedBy());
    }

    @Test
    void GivenMalformedPreferredUsernameClaim_WhenGettingRequestInfo_ThenFallsBackToAuthName() {
        // Given: a wrong-typed claim must fall back to the token subject, not "system"
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(request.getRemoteAddr()).thenReturn("192.168.10.20");
        when(request.getHeader("User-Agent")).thenReturn("Firefox/89.0");
        var jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("preferred_username")).thenThrow(new IllegalArgumentException("wrong claim type"));
        var jwtAuth = mock(JwtAuthenticationToken.class);
        when(jwtAuth.isAuthenticated()).thenReturn(true);
        when(jwtAuth.getToken()).thenReturn(jwt);
        when(jwtAuth.getName()).thenReturn("jwt-subject");
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);

        try (var logs = LogCapture.forClass(RequestInfoProvider.class)) {
            // When
            var result = requestInfoProvider.get();

            // Then
            assertEquals("jwt-subject", result.performedBy());
            assertThat(logs.lines())
                    .as("a malformed claim must be visible in logs, not silent")
                    .anyMatch(line -> line.contains("preferred_username"));
        }
    }

    @Test
    void GivenUnexpectedFailureResolvingUsername_WhenGettingRequestInfo_ThenExceptionPropagates() {
        // Given: an unexpected bug must not be masked as performedBy="system" in audit rows.
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(request);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        var bug = new IllegalStateException("unexpected");
        when(authentication.getName()).thenThrow(bug);

        // When / Then
        assertThatThrownBy(() -> requestInfoProvider.get()).isSameAs(bug);
    }

    @Test
    void GivenNullRequestAttributes_WhenGettingRequestInfo_ThenUnknownValuesAreReturned() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.clientIp());
        assertEquals("unknown", result.userAgent());
        assertEquals("admin", result.performedBy());
    }

    @Test
    void GivenNullRequest_WhenGettingRequestInfo_ThenUnknownValuesAreReturned() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
        when(requestAttributes.getRequest()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("testuser");
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.clientIp());
        assertEquals("unknown", result.userAgent());
        assertEquals("testuser", result.performedBy());
    }

    @Test
    void GivenNullUserAgentHeader_WhenGettingRequestInfo_ThenUserAgentIsNull() {
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
        assertEquals("127.0.0.1", result.clientIp());
        assertNull(result.userAgent()); // Returns null when header is null but request is not
        assertEquals("apiuser", result.performedBy());
    }

    @Test
    void GivenNullRemoteAddress_WhenGettingRequestInfo_ThenClientIpIsNull() {
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
        assertNull(result.clientIp()); // Returns null when remote address is null but request is not
        assertEquals("Postman/7.0", result.userAgent());
        assertEquals("service-account", result.performedBy());
    }

    @Test
    void GivenAllNullRequestValues_WhenGettingRequestInfo_ThenNullsAreReturned() {
        when(RequestContextHolder.getRequestAttributes()).thenReturn(null);
        when(SecurityContextHolder.getContext()).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);
        var result = requestInfoProvider.get();
        assertNotNull(result);
        assertEquals("unknown", result.clientIp());
        assertEquals("unknown", result.userAgent());
        assertEquals("system", result.performedBy());
    }
}
