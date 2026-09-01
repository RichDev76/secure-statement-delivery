package com.example.statementservice.infrastructure.web;

import com.example.statementservice.shared.RequestInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class RequestInfoProvider {

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String UNKNOWN = "unknown";
    public static final String SYSTEM_DEFAULT = "system";
    public static final String JWT_CLAIM_PREFERRED_USERNAME = "preferred_username";

    public RequestInfo get() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        var request = attributes != null ? attributes.getRequest() : null;

        var clientIp = request != null ? request.getRemoteAddr() : UNKNOWN;
        var userAgent = request != null ? request.getHeader(USER_AGENT_HEADER) : UNKNOWN;
        var performedBy = resolveUsername();

        return new RequestInfo(clientIp, userAgent, performedBy);
    }

    private String resolveUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return SYSTEM_DEFAULT;
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            var preferredUsername = preferredUsername(jwtAuth);
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        var name = auth.getName();
        return (name != null && !name.isBlank()) ? name : SYSTEM_DEFAULT;
    }

    // Only the claim read may fall back - any other failure must propagate, not mislabel audit rows.
    private String preferredUsername(JwtAuthenticationToken jwtAuth) {
        try {
            return jwtAuth.getToken().getClaimAsString(JWT_CLAIM_PREFERRED_USERNAME);
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Malformed {} claim, falling back to token subject: {}",
                    JWT_CLAIM_PREFERRED_USERNAME,
                    e.toString());
            return null;
        }
    }
}
