package com.example.statementservice.infrastructure.security;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;
import static com.example.statementservice.infrastructure.web.CommonUtil.createProblemDetail;

import com.example.statementservice.infrastructure.web.EndpointLabel;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityEndpointsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ERROR_CODE_UNAUTHENTICATED = "UNAUTHENTICATED";
    private static final String ERROR_CODE_ACCESS_DENIED = "ACCESS_DENIED";

    private final SecurityEndpointsProperties endpoints;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint problemDetailAuthEntryPoint,
            AccessDeniedHandler problemDetailAccessDeniedHandler)
            throws Exception {

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless, bearer-token-only API with no cookie/session auth anywhere - CSRF
                // protection has nothing to protect. See ADR 0012.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    var registry = auth;

                    for (var group : groupByMethod(endpoints.getWhitelist()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .permitAll();
                    }
                    for (var group : groupByMethod(endpoints.getUpload()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .hasRole(AppRole.UPLOAD.getRoleName());
                    }
                    for (var group : groupByMethod(endpoints.getAudit()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .hasRole(AppRole.AUDIT_LOGS_SEARCH.getRoleName());
                    }
                    for (var group : groupByMethod(endpoints.getSearch()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .hasRole(AppRole.SEARCH.getRoleName());
                    }
                    for (var group : groupByMethod(endpoints.getLink()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .hasRole(AppRole.GENERATE_SIGNED_LINK.getRoleName());
                    }

                    registry.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemDetailAuthEntryPoint)
                        .accessDeniedHandler(problemDetailAccessDeniedHandler))
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    private static Map<HttpMethod, List<String>> groupByMethod(List<SecurityEndpointsProperties.EndpointRule> rules) {
        return rules.stream()
                .collect(Collectors.groupingBy(
                        rule -> HttpMethod.valueOf(rule.getMethod().toUpperCase(Locale.ROOT)),
                        Collectors.mapping(SecurityEndpointsProperties.EndpointRule::getPattern, Collectors.toList())));
    }

    private static String[] toArray(List<String> patterns) {
        return patterns.toArray(String[]::new);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    @Bean
    public AuthenticationEntryPoint problemDetailAuthEntryPoint(JsonMapper jsonMapper) {
        return (request, response, authException) -> {
            log.warn(
                    "Unauthenticated access - path={}, method={}",
                    EndpointLabel.of(request.getRequestURI()),
                    request.getMethod());

            var pd = createProblemDetail(
                    HttpStatus.UNAUTHORIZED,
                    buildProblemDetailTypeURI(request, "/errors/authentication"),
                    "Unauthenticated",
                    "Authentication required to access this resource",
                    ERROR_CODE_UNAUTHENTICATED);

            try {
                pd.setInstance(URI.create(request.getRequestURI()));
            } catch (Exception ignored) {
            }

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            jsonMapper.writeValue(response.getOutputStream(), pd);
        };
    }

    @Bean
    public AccessDeniedHandler problemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, accessDeniedException) -> {
            log.warn(
                    "Access denied - path={}, method={}",
                    EndpointLabel.of(request.getRequestURI()),
                    request.getMethod());

            var pd = createProblemDetail(
                    HttpStatus.FORBIDDEN,
                    buildProblemDetailTypeURI(request, "/errors/authorization"),
                    "Forbidden",
                    "You do not have permission to access this resource",
                    ERROR_CODE_ACCESS_DENIED);
            try {
                pd.setInstance(URI.create(request.getRequestURI()));
            } catch (Exception ignored) {
            }

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            jsonMapper.writeValue(response.getOutputStream(), pd);
        };
    }
}
