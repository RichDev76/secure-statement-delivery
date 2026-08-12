package com.example.statementservice.infrastructure.security;

import static com.example.statementservice.infrastructure.web.CommonUtil.buildProblemDetailTypeURI;

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
import org.springframework.http.ProblemDetail;
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
                // protection has nothing to protect. See ADR 0024.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    var registry = auth;

                    for (var group : groupByMethod(endpoints.getWhitelist()).entrySet()) {
                        registry = registry.requestMatchers(group.getKey(), toArray(group.getValue()))
                                .permitAll();
                    }
                    for (var group : groupByMethod(endpoints.getAdmin()).entrySet()) {
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
            log.warn("Unauthenticated access - path={}, method={}", request.getRequestURI(), request.getMethod());

            var pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            pd.setType(buildProblemDetailTypeURI(request, "/errors/authentication"));
            pd.setTitle("Unauthenticated");
            pd.setDetail("Authentication required to access this resource");

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
            log.warn("Access denied - path={}, method={}", request.getRequestURI(), request.getMethod());

            var pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            pd.setType(buildProblemDetailTypeURI(request, "/errors/authorization"));
            pd.setTitle("Forbidden");
            pd.setDetail("You do not have permission to access this resource");
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
