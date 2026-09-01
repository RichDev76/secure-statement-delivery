# 0006 — Keycloak JWT with per-endpoint RBAC

## Context

The API distinguishes administrative operations (upload, audit search) from consumer operations
(search, link generation), and must do so statelessly.

## Problem

Access control needs to be enforceable per endpoint without the service owning user identity or
session state itself.

## Decision

`statement-service` is a JWT resource server (Spring Security) validating tokens issued by
Keycloak. `KeycloakRoleConverter` maps the JWT's `roles` (or `realm_access.roles`) claim to
`ROLE_<name>` authorities. `@PreAuthorize` on each controller handler enforces one distinct role per operation
(`Upload`, `GenerateSignedLink`, `Search`, `AuditLogsSearch` — see 0012); the download endpoint is whitelisted
and relies solely on its signed-link signature (see 0015, 0020).

## Alternatives

- API keys: harder to rotate and scope at fine granularity.
- Basic auth: no native fine-grained RBAC.

## Consequences

Stateless, horizontally-scalable authorization; startup and request-time dependency on Keycloak's
public keys being reachable.

## Implementation Notes

`SecurityConfig`, `KeycloakRoleConverter` in the `infrastructure`/`security` packages.

## References

- `docs/standards/security.md`
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [0012 — Method-level role authorization with config-driven whitelist](0012-method-level-role-authorization.md)
