# 0010 — Migrate to Spring Boot 4.0.7 / Spring Cloud 2025.1.2

## Context

statement-service and config-server ran Spring Boot 3.5.7 / Spring Cloud 2025.0.0.
Spring Boot 3.5 reached OSS end-of-life on 30 June 2026; continuing on it means no
further security patches for a service handling encrypted customer statements.
Spring Boot 4 reorganizes starters into focused modules, replaces Jackson 2 with
Jackson 3 (`tools.jackson.*`) as the default JSON library, ships Spring Security 7,
and requires the Spring Cloud 2025.1.x train.

## Problem

The upgrade touches the Jackson configuration (`JacksonConfig`, `SecurityConfig`
ProblemDetail handlers), starter artifact names, ShedLock (5.16.0 predates Boot 4
support), and the Spring Cloud Config server/client pair, which must move in
lockstep. No test started a Spring context, so the riskiest changes had no
automated safety net.

## Decision

Migrate both modules to Spring Boot 4.0.7 / Spring Cloud 2025.1.2 in a single
coordinated change, applying starter renames directly (no transitional
`spring-boot-starter-classic`), and add a minimal integration-test safety net:
a Testcontainers-backed context smoke test and a security filter-chain test
covering the endpoint role matrix and ProblemDetail 401/403 responses.

Key changes: starter renames (`-web`→`-webmvc`, `-aop`→`-aspectj`,
`-oauth2-resource-server`→`-security-oauth2-resource-server`); `JacksonConfig` moves to a Jackson 3
`JsonMapperBuilderCustomizer`; explicit version bumps Boot 4 no longer manages (ShedLock 7.7.0,
springdoc 3.1.0, MapStruct 1.6.3); integration tests run via maven-failsafe with the new
`webmvc-test`/`security-test`/`testcontainers` test modules.

## Alternatives

- Stay on Spring Boot 3.5 without vendor patches: rejected — unacceptable risk.
- Two-step migration via `spring-boot-starter-classic`: rejected — the full starter
  mapping was known up front and small (4 renames); the safety net adds pom churn
  and a second removal phase without reducing real risk here.
- Bump straight to Spring Boot 4.1.0: deferred — 4.0.x isolates migration variables;
  4.1 can follow as a routine minor bump.

## Consequences

- Jackson 2 remains on the classpath only transitively (springdoc, `jackson-databind-nullable`);
  no `spring-boot-jackson2` shim needed since generated models don't use `JsonNullable`.
- Regenerated OpenAPI sources diffed byte-identical against the pre-migration baseline.
- CSRF remains "explicitly ignored per matcher" — disabling it outright is a separate security
  decision, not bundled here.

## Implementation Notes

Verified by `mvn clean verify` (395 unit + 10 integration tests) and a standalone config-server
boot.

## References

- Spring Boot 4.0 Migration Guide (spring-projects/spring-boot wiki)
- Spring Security 7.0 Migration Guide
- docs/standards/architecture.md, testing.md, api.md, security.md
