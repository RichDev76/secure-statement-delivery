# 0012 — Method-level role authorization with config-driven whitelist

## Context

`SecurityConfig` used to build its authorization rules from five `security.endpoints.*` yml groups
(`whitelist`, `upload`, `audit`, `search`, `link`), each a Bean-Validated `List<{method, pattern}>`
mapped to a fixed `AppRole` — config-driven and method-scoped, which fixed an earlier generation of
hardcoded, path-only matchers. That design worked, but role requirements are really part of the API
contract — one fixed role per operation, identical in every environment — while only the open
endpoints (signed-link download, actuator, Swagger) legitimately vary per environment.

## Problem

Keeping role rules in yml made them invisible at the endpoint they protect: reading a controller
told you nothing about who could call it, and a forgotten or mistyped yml entry for a new endpoint
silently degraded it to any-authenticated-caller, with nothing to catch that at build time. All
three environment profiles carried byte-identical role config, which confirmed the rules were
contract, not environment, data.

## Decision

Roles are now enforced in code, at the HTTP boundary: each controller handler carries
`@PreAuthorize` (enabled via `@EnableMethodSecurity`), with role strings sourced from `AppRole`
compile-time constants, so every annotation shares one definition per role. Role names themselves
are fixed by the Keycloak realm import.

Only the environment-varying whitelist stays in config. `security.endpoints.whitelist` holds
`List<{method, pattern}>` rules via a Bean-Validated `EndpointRule` nested class, with `@NotEmpty`
plus cascading `@Valid` failing startup fast on an empty or malformed group.

The filter chain floors everything else at `anyRequest().authenticated()` — the
Spring-Security-docs-mandated backstop, since unannotated methods aren't secured by method security
at all.

Method-security denials route through a dedicated `AccessDeniedException` handler in
`GlobalExceptionHandler`, answering with the same RFC 9457 body as the filter-level
`AccessDeniedHandler` via a shared `SecurityProblemDetailFactory`. Without that, the
`Exception.class` catch-all would turn a legitimate 403 into a 500.

Every handler has to state its authorization decision: an ArchUnit rule requires `@PreAuthorize` or
an explicit `@PublicEndpoint(reason)` marker on every public `@RestController` method, so a new
endpoint can never ship without one.

And CSRF is disabled outright, replacing the ignore-list mechanism entirely — the right approach
for a purely token-based API with no cookie auth.

## Alternatives

Keeping the status quo — yml-driven role matchers — didn't hold up once we recognized roles as
contract rather than environment config (see Problem). Running both layers, yml matchers and
`@PreAuthorize` together, would have meant every rule stated twice with no compiler link, and a
passing test couldn't tell you which layer actually satisfied it. A `Map<String, String>` lookup
from group name to role was another option, but a yml key typo there would silently produce no
applied rule instead of a compiler error. We also deliberately didn't build out real-JWT-signing
integration test infrastructure — the existing MockMvc `jwt()` post-processor already proves the
authorization behavior we care about here.

## Consequences

The role protecting an operation is now visible right on the method that implements it, and
whitelist intent is equally explicit via `@PublicEndpoint(reason)`. A non-`POST` request to
`/upload` isn't gated by the upload role — method security only guards the mapped handler, and only
`authenticated()` applies elsewhere — which `SecurityRoleMatrixIT` verifies. Authenticated
wrong-role callers reach argument resolution before denial: a malformed request gets a 400 from
validation before authorization is even checked (documented by `MethodSecurityDenialIT`), and for
upload the multipart body is parsed before the rejection happens. We accepted that: both outcomes
are denials visible only to authenticated clients, parsing is bounded by multipart size limits, and
anonymous callers still get a 401 at the filter chain regardless. All yml profiles now carry only
the whitelist group, and they get updated together.

## References

- [0003 — HMAC-signed single-use download links](0003-hmac-signed-single-use-download-links.md)
- [0006 — Keycloak JWT RBAC](0006-keycloak-jwt-rbac.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
