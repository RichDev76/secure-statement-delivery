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
        try {
            var auth = SecurityContextHolder.getContext() != null
                    ? SecurityContextHolder.getContext().getAuthentication()
                    : null;

            if (auth == null || !auth.isAuthenticated()) {
                return SYSTEM_DEFAULT;
            }

            // Prefer Keycloak's preferred_username claim when using JWT auth
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                var jwt = jwtAuth.getToken();
                var preferredUsername = jwt.getClaimAsString(JWT_CLAIM_PREFERRED_USERNAME);

                if (preferredUsername != null && !preferredUsername.isBlank()) {
                    return preferredUsername;
                }
            }

            // Fallback to the standard Spring Security username
            var name = auth.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve authenticated username, falling back to '{}': {}", SYSTEM_DEFAULT, e.toString());
        }

        return SYSTEM_DEFAULT;
    }
}
