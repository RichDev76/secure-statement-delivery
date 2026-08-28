package com.example.statementservice.infrastructure.security;

import com.example.statementservice.infrastructure.web.EndpointLabel;
import com.example.statementservice.infrastructure.web.SecurityProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityEndpointsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityEndpointsProperties endpoints;

    // Roles are enforced via @PreAuthorize; authenticated() is the backstop for unannotated handlers.
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
            writeProblemDetail(response, SecurityProblemDetailFactory.unauthenticated(request), jsonMapper);
        };
    }

    // Unreachable on current paths (403s arise in MVC); kept as backstop for future URL-level rules.
    @Bean
    public AccessDeniedHandler problemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, accessDeniedException) ->
                writeProblemDetail(response, SecurityProblemDetailFactory.loggedAccessDenied(request), jsonMapper);
    }

    private static void writeProblemDetail(
            HttpServletResponse response, ProblemDetail problemDetail, JsonMapper jsonMapper) throws IOException {
        response.setStatus(problemDetail.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
