# 0011 — Adopt feature-first hexagonal packaging

## Context

`statement-service` is packaged by technical layer (`controller/`, `service/`, `util/`, …).
House standards (`docs/standards/architecture.md`) mandate Screaming Architecture with
hexagonal ports & adapters.

## Problem

Layer packaging hides business capabilities, permits cross-feature tangles
(`util/` is a dependency hub; audit is reached via two inconsistent paths), and lets IO
concerns (file system, crypto, servlet context) leak into domain services. Nothing enforces
boundaries, so violations accumulate silently.

## Decision

1. Top-level packages name business capabilities: `statement/` (sub-capabilities `upload/`,
   `search/`, `download/`, `signedlink/`), `audit/`, plus `infrastructure/` (shared technical) and
   `shared/` (dependency-free values). Controllers and other adapters live in an `infrastructure/`
   sub-package inside their feature; `signedlink` is a sibling of `upload`/`search`/`download`,
   keeping the dependency graph acyclic.
2. Outbound ports owned by the domain: `StatementFileStore`, `FileCipher`, `LinkSigner`,
   `DownloadUrlProvider`, plus a `java.time.Clock` bean. No inbound use-case ports — the generated
   `AdminApi`/`StatementsApi`/`AuditApi` interfaces are the inbound contracts. Generated OpenAPI
   packages stay where the generator puts them and are adapter-only by rule, not relocation.
3. Boundaries are executable: an ArchUnit suite (`ArchitectureTest`) enforces slice acyclicity,
   feature/infrastructure/shared directions, adapter-only generated types, constructor injection,
   and File/crypto confinement. Legacy violations are frozen and may only shrink; the freeze store
   is deleted when the migration completes.

## Alternatives

- Big-bang restructure: rejected — unreviewable diff, no mid-flight protection.
- ArchUnit after migration: rejected — the migration itself would be unguarded.
- `signedlink` as top-level feature or inside `download`: rejected — it is meaningless without
  statements, and nesting under `download` forces a `search → download` edge.
- Relocating generated sources into an adapter package: rejected — house standard fixes the
  generator packages; a rule achieves the same constraint without fighting the generator.

## Consequences

- Package tree states business intent; boundary regressions fail the build immediately.
- Frozen debt is visible and monotonically decreasing (store diff per PR).

## Implementation Notes

Rules live in `ArchitectureTest.java`; freeze config in `src/test/resources/archunit.properties`.

## References

- docs/standards/architecture.md
- [0010 — Migrate to Spring Boot 4](0010-migrate-to-spring-boot-4.md)
- https://alistair.cockburn.us/hexagonal-architecture/
- https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules
