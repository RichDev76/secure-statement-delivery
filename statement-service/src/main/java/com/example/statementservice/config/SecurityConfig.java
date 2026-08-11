package com.example.statementservice.config;

import static com.example.statementservice.util.CommonUtil.buildProblemDetailTypeURI;

import com.example.statementservice.security.KeycloakRoleConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

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
                .csrf(csrf ->
                        csrf.ignoringRequestMatchers(endpoints.getCsrfIgnored().toArray(String[]::new)))
                .authorizeHttpRequests(auth -> {
                    if (!endpoints.getWhitelist().isEmpty()) {
                        auth.requestMatchers(endpoints.getWhitelist().toArray(String[]::new))
                                .permitAll();
                    }

                    auth.requestMatchers("/api/v1/statements/upload").hasRole("Upload");
                    auth.requestMatchers("/api/v1/statements/audit/logs").hasRole("AuditLogsSearch");
                    auth.requestMatchers("/api/v1/statements/search").hasRole("Search");
                    auth.requestMatchers("/api/v1/statements/*/link").hasRole("GenerateSignedLink");

                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemDetailAuthEntryPoint)
                        .accessDeniedHandler(problemDetailAccessDeniedHandler))
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    @Bean
    public AuthenticationEntryPoint problemDetailAuthEntryPoint(ObjectMapper objectMapper) {
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
            objectMapper.writeValue(response.getOutputStream(), pd);
        };
    }

    @Bean
    public AccessDeniedHandler problemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
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
            objectMapper.writeValue(response.getOutputStream(), pd);
        };
    }
}
