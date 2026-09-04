# 0010 — Migrate to Spring Boot 4.0.7 / Spring Cloud 2025.1.2

**Addendum (2026-08):** the deferred 4.1 bump has since happened, as the routine minor upgrade we
anticipated below — the poms now pin Boot 4.1.1 / Cloud 2025.1.3 / ShedLock 7.9.0 via ordinary
dependency bumps. The migration decision itself is unchanged.

## Context

statement-service and config-server were running Spring Boot 3.5.7 / Spring Cloud 2025.0.0. Spring
Boot 3.5 reached OSS end-of-life on 30 June 2026, so staying on it means no further security
patches for a service handling encrypted customer statements. Spring Boot 4 reorganizes starters
into focused modules, replaces Jackson 2 with Jackson 3 (`tools.jackson.*`) as the default JSON
library, ships Spring Security 7, and requires the Spring Cloud 2025.1.x train.

## Problem

The upgrade touches the Jackson configuration (`JacksonConfig`, `SecurityConfig` ProblemDetail
handlers), starter artifact names, ShedLock (5.16.0 predates Boot 4 support), and the Spring Cloud
Config server/client pair, which has to move in lockstep. No test started a Spring context, so the
riskiest changes had no automated safety net.

## Decision

We migrated both modules to Spring Boot 4.0.7 / Spring Cloud 2025.1.2 in a single coordinated
change, applying starter renames directly rather than going through a transitional
`spring-boot-starter-classic`, and added a minimal integration-test safety net: a
Testcontainers-backed context smoke test and a security filter-chain test covering the endpoint
role matrix and ProblemDetail 401/403 responses.

The key changes: starter renames (`-web`→`-webmvc`, `-aop`→`-aspectj`,
`-oauth2-resource-server`→`-security-oauth2-resource-server`); `JacksonConfig` moved to a Jackson 3
`JsonMapperBuilderCustomizer`; explicit version bumps for what Boot 4 no longer manages (ShedLock
7.7.0, springdoc 3.1.0, MapStruct 1.6.3); and integration tests now run via maven-failsafe with the
new `webmvc-test`/`security-test`/`testcontainers` test modules.

## Alternatives

Staying on Spring Boot 3.5 without vendor patches was never really an option — the risk was
unacceptable. A two-step migration through `spring-boot-starter-classic` didn't earn its keep
either: the full starter mapping was known up front and small (four renames), so the safety net
would have added pom churn and a second removal phase without reducing any real risk here. Jumping
straight to Spring Boot 4.1.0 was tempting, but we deferred it — 4.0.x isolates the migration
variables, and 4.1 can follow later as a routine minor bump.

## Consequences

Jackson 2 stays on the classpath only transitively (springdoc, `jackson-databind-nullable`); no
`spring-boot-jackson2` shim is needed since the generated models don't use `JsonNullable`.
Regenerated OpenAPI sources diffed byte-identical against the pre-migration baseline. CSRF remains
"explicitly ignored per matcher" — disabling it outright is a separate security decision we're not
bundling in here.

## Implementation Notes

Verified by `mvn clean verify` (395 unit + 10 integration tests) and a standalone config-server
boot.

## References

- Spring Boot 4.0 Migration Guide (spring-projects/spring-boot wiki)
- Spring Security 7.0 Migration Guide
