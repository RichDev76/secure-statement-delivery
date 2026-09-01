# 0012 — Method-level role authorization with config-driven whitelist

## Context

`SecurityConfig` previously built its authorization rules from five `security.endpoints.*` yml
groups (`whitelist`, `upload`, `audit`, `search`, `link`), each a Bean-Validated
`List<{method, pattern}>` mapped to a fixed `AppRole` — config-driven and method-scoped, fixing
an earlier generation of hardcoded, path-only matchers. That design worked, but role requirements
are part of the API contract — one fixed role per operation, identical in every environment —
while only the open endpoints (signed-link download, actuator, Swagger) legitimately vary per
environment.

## Problem

Keeping role rules in yml made them invisible at the endpoint they protect: reading a controller
told you nothing about who could call it, and a forgotten or mistyped yml entry for a new
endpoint silently degraded it to any-authenticated-caller, with nothing to catch that at build
time. All three environment profiles carried byte-identical role config, confirming the rules
were contract, not environment, data.

## Decision

1. **Roles are enforced in code, at the HTTP boundary**: each controller handler carries
   `@PreAuthorize` (enabled via `@EnableMethodSecurity`), with role strings sourced from
   `AppRole` compile-time constants so every annotation shares one definition per role.
   Role names are fixed by the Keycloak realm import.
2. **Only the environment-varying whitelist stays in config**: `security.endpoints.whitelist`
   holds `List<{method, pattern}>` rules via a Bean-Validated `EndpointRule` nested class, with
   `@NotEmpty` + cascading `@Valid` failing startup fast on an empty or malformed group.
3. **The filter chain floors everything else at `anyRequest().authenticated()`** — the
   Spring-Security-docs-mandated backstop, since unannotated methods are not secured by method
   security.
4. **Method-security denials route through a dedicated `AccessDeniedException` handler** in
   `GlobalExceptionHandler`, answering with the same RFC 9457 body as the filter-level
   `AccessDeniedHandler` via a shared `SecurityProblemDetailFactory` — without it, the
   `Exception.class` catch-all would convert a legitimate 403 into a 500.
5. **Every handler must state its authorization decision**: an ArchUnit rule requires
   `@PreAuthorize` or an explicit `@PublicEndpoint(reason)` marker on every public
   `@RestController` method, so a new endpoint can never ship without one.
6. **CSRF is disabled outright**, replacing the ignore-list mechanism entirely — the correct
   mechanism for a purely token-based API with no cookie auth.

## Alternatives

- Keep the status quo (yml-driven role matchers): rejected — roles are contract, not environment
  config (see Problem).
- Keeping both layers (yml matchers *and* `@PreAuthorize`): rejected — every rule stated twice
  with no compiler link, and a passing test cannot tell which layer satisfied it.
- Group-name-to-role via a `Map<String, String>` lookup: rejected — a yml key typo would silently
  produce no applied rule instead of a compiler error.
- Real-JWT-signing integration test infrastructure: deliberately not adopted — the existing
  MockMvc `jwt()` post-processor already proves the authorization behavior in scope here.

## Consequences

- The role protecting an operation is visible on the method that implements it; whitelist intent
  is equally explicit via `@PublicEndpoint(reason)`.
- A non-`POST` request to `/upload` is not gated by the upload role — method security only guards
  the mapped handler, and only `authenticated()` applies elsewhere; verified by
  `SecurityRoleMatrixIT`.
- Authenticated wrong-role callers reach argument resolution before denial: a malformed request
  is answered 400 by validation before authorization is checked (documented by
  `MethodSecurityDenialIT`), and for upload the multipart body is parsed pre-rejection —
  accepted: both outcomes are denials visible only to authenticated clients, parsing is bounded
  by multipart size limits, and anonymous callers still receive 401 at the filter chain.
- All yml profiles carry only the whitelist group, updated together.

## References

- docs/standards/security.md
- [0003 — HMAC-signed single-use download links](0003-hmac-signed-single-use-download-links.md)
- [0006 — Keycloak JWT RBAC](0006-keycloak-jwt-rbac.md)
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
