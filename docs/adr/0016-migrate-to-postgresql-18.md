# 0016 — Migrate to PostgreSQL 18

## Context

The Postgres version was pinned inconsistently across the project: `postgres:15` in
`infra/docker-compose.yml`, `postgres:17-alpine` in an unwired `infra/db/Dockerfile`, and
`postgres:15` again in the Testcontainers-based integration test base class. PostgreSQL 18 is the
current stable release.

## Problem

None of the pins were on a current, consistent version, and the upcoming work — UUIDv7 primary
keys, `audit_logs` partitioning — benefits from being built against the version the project will
actually run in production.

## Decision

We moved every pin to `postgres:18-alpine`: `infra/docker-compose.yml`, `infra/db/Dockerfile`, and
`AbstractIntegrationTest`'s Testcontainers image. We checked the PG15→18 release notes for breaking
changes relevant to this stack: data checksums default on for a fresh `initdb` (irrelevant here,
since dev volumes get recreated rather than upgraded in place), and MD5 auth is deprecated (we
verified `infra/db/init/01-init.sh` doesn't use it). Nothing this codebase's Flyway/Hibernate/JDBC
usage depends on is deprecated.

Postgres 18 also ships a native `uuidv7()` SQL function, which we considered and rejected as the
ID-generation mechanism itself — see [0017](0017-uuid-v7-primary-keys.md) — but the version bump
stands independently of that choice.

## Alternatives

Staying on PostgreSQL 15 didn't make sense — there was no real reason not to build against current.
Upgrading in place against existing data wasn't applicable either, since no production data exists
yet.

## Consequences

Dev, CI, and test environments all now run the same Postgres major version as intended production.
`infra/db/Dockerfile` remains unwired to any build step in this repo — updating its version doesn't
resolve whether it should exist at all, which we're flagging for the reviewer rather than resolving
here.

## References

- [PostgreSQL 18 Release Notes](https://www.postgresql.org/docs/release/18.0/)
- [0017 — UUIDv7 primary keys](0017-uuid-v7-primary-keys.md)
