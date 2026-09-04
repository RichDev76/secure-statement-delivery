# 0011 — Adopt feature-first hexagonal packaging

## Context

`statement-service` is packaged by technical layer today (`controller/`, `service/`, `util/`, …).
We want Screaming Architecture instead — package names that shout what the system does, not how
it's wired — built on hexagonal ports and adapters.

## Problem

Layer packaging hides business capabilities. `util/` has turned into a dependency hub, audit gets
reached through two inconsistent paths, and IO concerns — file system, crypto, servlet context —
keep leaking into domain services because nothing stops them. Violations pile up silently since
there's no enforcement anywhere.

## Decision

Top-level packages now name business capabilities: `statement/` (with sub-capabilities `upload/`,
`search/`, `download/`, `signedlink/`), `audit/`, plus `infrastructure/` for shared technical
concerns and `shared/` for dependency-free values. Controllers and other adapters move into an
`infrastructure/` sub-package inside their own feature. `signedlink` sits as a sibling of
`upload`/`search`/`download` rather than nesting inside one of them, which keeps the dependency
graph acyclic.

Outbound ports belong to the domain: `StatementFileStore`, `FileCipher`, `LinkSigner`,
`DownloadUrlProvider`, and a `java.time.Clock` bean. There are no inbound use-case ports — the
generated `AdminApi`/`StatementsApi`/`AuditApi` interfaces already serve as the inbound contracts,
so we didn't add a redundant layer on top. Generated OpenAPI packages stay wherever the generator
puts them; we treat them as adapter-only by rule rather than physically relocating them.

None of this holds unless it's enforced, so an ArchUnit suite (`ArchitectureTest`) checks slice
acyclicity, feature/infrastructure/shared dependency direction, adapter-only generated types,
constructor injection, and File/crypto confinement on every build. Existing violations are frozen
in place and can only shrink from there; once the migration is finished, the freeze store goes
away.

## Alternatives

A big-bang restructure was tempting but would have produced an unreviewable diff with no
protection while it was in flight, so we ruled it out. Adding ArchUnit only after the migration
was done would have left the migration itself unguarded — exactly the period we most needed
coverage. We also considered making `signedlink` a top-level feature, or nesting it under
`download`, but it's meaningless without statements to sign links for, and nesting it under
`download` would have forced an awkward `search → download` dependency. Finally, we thought about
physically relocating generated sources into an adapter package, but the house convention already
fixes where the generator writes its output — an ArchUnit rule gets us the same guarantee without
fighting the generator.

## Consequences

The package tree now states business intent on its face, and boundary regressions fail the build
immediately instead of surviving review. The frozen debt is visible and only ever shrinks — you
can see it in the store diff on every PR that touches it.

## Implementation Notes

Migration is complete; the rules live directly in `ArchitectureTest.java`. The freeze store
mentioned above has been deleted along with `archunit.properties`.

## References

- [0010 — Migrate to Spring Boot 4](0010-migrate-to-spring-boot-4.md)
- https://alistair.cockburn.us/hexagonal-architecture/
- https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules
