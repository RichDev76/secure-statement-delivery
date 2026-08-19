# 0016 — Migrate to PostgreSQL 18

## Context

Postgres version was inconsistently pinned across the project: `postgres:15` in
`infra/docker-compose.yml`, `postgres:17-alpine` in an unwired `infra/db/Dockerfile`, and
`postgres:15` in the Testcontainers-based integration test base class. PostgreSQL 18 is the
current stable release.

## Problem

None of the pins were on a current, consistent version, and the upcoming work (UUIDv7
primary keys, `audit_logs` partitioning) benefits from being built against the version the
project will actually run in production.

## Decision

Move every pin to `postgres:18-alpine`: `infra/docker-compose.yml`, `infra/db/Dockerfile`, and
`AbstractIntegrationTest`'s Testcontainers image. Checked release notes for PG15→18 breaking
changes relevant to this stack: data checksums default on for fresh `initdb` (irrelevant — dev
volumes are recreated, not upgraded in place), MD5 auth deprecated (verified
`infra/db/init/01-init.sh` doesn't use it). No deprecated feature this codebase's
Flyway/Hibernate/JDBC usage depends on.

Postgres 18 ships a native `uuidv7()` SQL function, considered and rejected as the ID-generation
mechanism itself — see [0017](0017-uuid-v7-primary-keys.md) — but the version bump stands
independently of that choice.

## Alternatives

- Stay on PostgreSQL 15: rejected — no reason not to build against current.
- Upgrade in place against existing data: not applicable — no production data exists yet.

## Consequences

Dev/CI/test environments all run the same Postgres major version as intended production.
`infra/db/Dockerfile` remains unwired to any build step in this repo — updating its version
doesn't resolve whether it should exist at all; flagged for the reviewer, not resolved here.

## References

- [PostgreSQL 18 Release Notes](https://www.postgresql.org/docs/release/18.0/)
- [0017 — UUIDv7 primary keys](0017-uuid-v7-primary-keys.md)
