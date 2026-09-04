# 0017 — UUIDv7 primary keys

## Context

`Statement`, `SignedLink`, and `AuditLog` all use random UUID (v4) primary keys, generated in
application code (`UUID.randomUUID()`) and set before every insert — no entity uses
`@GeneratedValue`. `signed_links.id` in particular has to be known before insert regardless of
generation scheme, since it's embedded in the HMAC signature.

## Problem

Random (v4) primary keys scatter inserts uniformly across a B-tree index regardless of insertion
order, which degrades index locality — page splits, cache misses, WAL volume — on append-heavy
tables as they grow, most acutely `audit_logs`.

## Decision

We're generating UUIDv7 in application code, behind a port. A new `shared/IdGenerator` (`UUID
newId()`) is implemented by `infrastructure/id/UuidV7IdGenerator` via
`com.github.f4b6a3:uuid-creator`'s `UuidCreator.getTimeOrderedEpoch()`. `StatementService`,
`SignedLinkService`, and `AuditService` all depend on the port instead of calling
`UUID.randomUUID()` directly. We didn't go with DB-side generation (Postgres 18's native
`uuidv7()`) because `SignedLinkService` structurally needs the id before insert, so app-side
generation for all three tables is the only design that's uniform across every call site — not a
style preference.

The DB-level `DEFAULT gen_random_uuid()` is dropped, not replaced. `V5` drops the default on all
three `id` columns with nothing standing in for it. It was already dead code, since every insert
path sets the id explicitly; dropping it outright means a manual or raw insert that skips
application code now fails loudly on `NOT NULL` instead of silently minting a v4.

And we're not backfilling existing rows. v4 and v7 are both opaque 128-bit values in the same
`uuid` column and FK type, so existing rows are left as-is. The locality benefit is prospective — it
only applies to new writes — so rewriting historical keys, and every FK referencing them, would be
real risk for no real benefit.

## Alternatives

Postgres 18's native `uuidv7()` as the column default doesn't work for `signed_links`, since its id
is needed pre-insert, so using it for the other two tables only would mean running two different
generation mechanisms for the same concern. Backfilling existing rows to v7 wasn't justified either
— there's no production data yet to justify the FK rewrite risk, and the benefit is write-path-only
regardless. We also looked at Hibernate's native `@UuidGenerator(style = VERSION_7)`, but it
generates at flush time rather than construction time, which doesn't fit `SignedLinkService`'s need
for the id before the entity is ever persisted — it's part of the signed payload.

## Consequences

New writes now cluster near the end of each index instead of scattering randomly. `uuid-creator`
becomes a new runtime dependency to pin and periodically re-verify. IDs embed a creation timestamp,
which is fine here since none of these entities' IDs are the security boundary — `SignedLink`
security rests on its HMAC token, not on ID unpredictability.

## References

- [RFC 9562 — UUID version 7](https://www.rfc-editor.org/rfc/rfc9562)
- [0003 — HMAC-signed, single-use, time-limited download links](0003-hmac-signed-single-use-download-links.md)
- [0016 — Migrate to PostgreSQL 18](0016-migrate-to-postgresql-18.md)
