# 0017 — UUIDv7 primary keys

## Context

`Statement`, `SignedLink`, and `AuditLog` all use random UUID (v4) primary keys, generated in
application code (`UUID.randomUUID()`) and set before every insert — no entity uses
`@GeneratedValue`. `signed_links.id` in particular must be known before insert regardless of
generation scheme, since it's embedded in the HMAC signature.

## Problem

Random (v4) primary keys scatter inserts uniformly across a B-tree index regardless of insertion
order, which degrades index locality (page splits, cache misses, WAL volume) on append-heavy
tables as they grow — most acutely `audit_logs`.

## Decision

1. **Application-generated UUIDv7 behind a port.** New `shared/IdGenerator` (`UUID newId()`),
   implemented by `infrastructure/id/UuidV7IdGenerator` via `com.github.f4b6a3:uuid-creator`'s
   `UuidCreator.getTimeOrderedEpoch()`. `StatementService`, `SignedLinkService`, and
   `AuditService` depend on the port instead of calling `UUID.randomUUID()` directly. Not
   DB-side generation (Postgres 18's native `uuidv7()`): `SignedLinkService` structurally needs
   the id before insert, so app-side generation for all three is the only design that's uniform
   across every call site — not a style preference.
2. **DB-level `DEFAULT gen_random_uuid()` dropped, not replaced.** `V5` drops the default on all
   three `id` columns with no substitute. It was already dead code (every insert path sets the id
   explicitly); dropping it outright means a manual/raw insert that skips application code fails
   loudly on `NOT NULL` instead of silently minting a v4.
3. **No backfill of existing rows.** v4 and v7 are both opaque 128-bit values in the same `uuid`
   column and FK type; existing rows are left as-is. The locality benefit is prospective — it only
   applies to new writes — so rewriting historical keys (and every FK referencing them) would be
   real risk for no real benefit.

## Alternatives

- Postgres 18's native `uuidv7()` as the column default: rejected — doesn't work for
  `signed_links` (id needed pre-insert), so using it for the other two tables only would mean two
  different generation mechanisms for the same concern.
- Backfilling existing rows to v7: rejected — no production data exists yet to justify the FK
  rewrite risk, and the benefit is write-path-only regardless.
- Hibernate's native `@UuidGenerator(style = VERSION_7)`: rejected — generates at flush time, not
  construction time, which doesn't fit `SignedLinkService`'s need for the id before the entity is
  ever persisted (it's part of the signed payload).

## Consequences

- New writes cluster near the end of each index instead of scattering randomly.
- `uuid-creator` is a new runtime dependency — pin and periodically re-verify its version.
- IDs embed a creation timestamp; acceptable here since none of these entities' IDs are the
  security boundary (`SignedLink` security rests on its HMAC token, not ID unpredictability).

## References

- [RFC 9562 — UUID version 7](https://www.rfc-editor.org/rfc/rfc9562)
- [0003 — HMAC-signed, single-use, time-limited download links](0003-hmac-signed-single-use-download-links.md)
- [0016 — Migrate to PostgreSQL 18](0016-migrate-to-postgresql-18.md)
