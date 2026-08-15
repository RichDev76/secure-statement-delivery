# 0012 — Config-driven security endpoint matchers

## Context

`SecurityConfig` hardcoded four `requestMatchers(literalPath).hasRole(literalRole)` rules;
`SecurityEndpointsProperties` separately bound `admin`/`audit` from yml but nothing ever read
them, and the bound `whitelist` matched on path only, not HTTP method. The target design also
guards against a known near-miss pattern: an ungated wildcard sub-path.

## Problem

Two of four endpoint-matcher groups were dead configuration; matchers were path-only
(`hasRole("Upload")` on `/upload` matched every HTTP method, not just `POST`); `csrfIgnored` was a
no-op ignore-list on a stateless bearer-token API; nothing tested any of it, so a config typo
could silently downgrade an endpoint's required role.

## Decision

1. **`security.endpoints.*` groups hold `List<{method, pattern}>` rules**, not flat pattern
   strings, via a Bean-Validated `EndpointRule` nested class.
2. **Group name is a grouping label, not a role string.** Role names are fixed by the Keycloak
   realm import and captured in a new `AppRole` enum; `SecurityConfig` maps each yml group to its
   `AppRole` with one explicit line, not a string-keyed lookup that could silently mismatch.
3. **Fail-fast via Jakarta Bean Validation**: `@NotEmpty` on every group (including `whitelist`)
   plus `@Valid` cascading into each rule — an empty or malformed group now fails startup instead
   of creating a silent authorization gap.
4. **CSRF is disabled outright**, replacing the ignore-list mechanism entirely — the correct
   mechanism for a purely token-based API with no cookie auth.
5. **Matchers are batched per method per group**, one `requestMatchers` call per group.

## Alternatives

- Group-name-to-role via a `Map<String, String>` lookup: rejected — a yml key typo would silently
  produce no applied rule instead of a compiler error.
- Rename Keycloak roles to match the yml group keys: rejected — would break already-issued tokens.
- Real-JWT-signing integration test infrastructure: deliberately not adopted — the existing
  MockMvc `jwt()` post-processor already proves the authorization-manager behavior in scope here.

## Consequences

- No path or role is hardcoded in Java anymore.
- A non-`POST` request to `/upload` is no longer blocked by the admin rule at all — a deliberate
  tightening, verified by `SecurityRoleMatrixIT`.
- All three yml profiles carry the new shape, updated together since a missed environment is
  exactly the failure mode this ADR closes.

## Implementation Notes

Three phases (property model → wiring → integration tests), each TDD and gated on
`mvn clean verify`.

## References

- docs/standards/security.md
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
