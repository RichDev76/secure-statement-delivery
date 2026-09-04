# 0024 — GitHub Actions CI pipeline

## Context

No CI existed: no `.github/` directory, no automated quality gates. Spotless's `apply` and `check`
are both bound to `validate`, so `apply` silently fixes formatting before `check` ever runs — no
lifecycle build can actually fail on it. The AI review checklist already names `api-compat`,
`dependency-scan`, and Trivy as required gates that had no implementation behind them.

## Problem

How do we introduce CI with parity to a known-good reference pipeline, and how do we gate
formatting given the `apply`-on-`validate` binding?

## Decision

GitHub Actions, seven jobs, Maven wrapper pinned (3.9.11 at authoring, kept current by Dependabot).
`format` invokes `spotless:check` directly, bypassing the lifecycle so the bound `apply` never
fires — one job owns the formatting verdict, and every other job passes `-Dspotless.skip=true`.
`build`, `unit-tests`, and `integration-tests` run plain `./mvnw verify`, with Testcontainers on the
runner's own Docker daemon and Ryuk disabled. `dependency-scan` runs Trivy (`vuln,secret`,
HIGH/CRITICAL, fail-closed). `api-compat` is PR-only (oasdiff, skips with a notice when the
contract is absent on the base branch). And `api-regression` runs a Bruno pack against the live
compose stack, with ephemeral masked secrets per run, bounded health polling, a compose-logs
artifact on failure, and unconditional teardown. Every `uses:` is pinned to a full commit SHA, the
workflow grants only `permissions: contents: read`, every job carries a timeout, and in-progress
runs cancel except on `main`. `CiWorkflowConventionsTest` (Surefire, no Docker) turns those
invariants into a regression test instead of a one-time review. PIT mutation testing is deferred —
see Consequences.

## Alternatives

GitLab CI or CircleCI didn't make sense — the repository lives on GitHub. One mega-job would mean
no fail-fast and the slowest possible signal, so that was out too. Self-hosted runners or
Testcontainers Cloud carry an ops burden and cost that isn't justified at this scale. And gating
coverage on a JaCoCo threshold right away didn't seem right either — we kept it report-only until a
baseline existed, since a failing gate bolted onto unmeasured code is arbitrary.

## Consequences

Every push and PR now gets automated formatting, compile, unit (including ArchUnit), integration
(Testcontainers), dependency-scan, and contract-compatibility gates. Formatting can actually fail a
build now, though local `mvn verify` still auto-formats — no change to the developer experience
there. PIT mutation testing still isn't implemented. `api-regression` runs on every push/PR but
only joins branch protection after a sustained stretch of green runs — a flaky required check is
worse than none. Docker Hub rate limits on unauthenticated pulls are mitigated by
pre-pull-with-retry, not eliminated (see Implementation Notes). And `permissions: contents: read`
makes "CI never writes to the repo" structural rather than just a convention.

## Implementation Notes

`mvnw`, `mvnw.cmd`, `.mvn/wrapper/` were generated via `mvn wrapper:wrapper` (version pinned in
`maven-wrapper.properties`, bumped by Dependabot). `.github/workflows/ci.yml` holds `format`,
`build`, `unit-tests`, `integration-tests`, `dependency-scan`, `api-compat`, `api-regression`.
`.github/dependabot.yml` runs weekly across `github-actions`, `maven`, and `docker` (both
Dockerfile directories), grouping minor and patch bumps. `.trivyignore` is empty; every future
entry needs a CVE ID, justification, and expiry. `statement-service/pom.xml` picked up
`jackson-dataformat-yaml` (test scope) for `CiWorkflowConventionsTest`, at
`statement-service/src/test/java/.../CiWorkflowConventionsTest.java`, which parses `ci.yml` and
`dependabot.yml` and asserts SHA pinning, least-privilege permissions, per-job timeouts,
ref-conditional concurrency cancellation, no `pull_request_target`, and Dependabot ecosystem
coverage. Running Trivy locally surfaced something worth noting: its Java scanner resolves POM
parents/BOMs against Maven Central unless offline, and unauthenticated shared-runner IPs can hit
its 429 rate limit. So `dependency-scan` runs `setup-java` plus `mvn dependency:resolve` first to
populate `.m2`, and sets `TRIVY_OFFLINE_SCAN: true`. Rollback is simple: revert the `.github/`
directory, since no other code path depends on it.

## Addendum — Image job and coverage gate

A new `image-build` job builds both Dockerfiles from a clean context and Trivy-scans the resulting
images (HIGH/CRITICAL, ignore-unfixed) — the images are what actually ships, and the filesystem
scan alone left base-layer CVEs invisible. JaCoCo now runs `check` at `verify` with 90%
instruction / 80% branch bundle thresholds; the "report-only until a baseline" condition above is
now met (verified baseline 93%/82.7%), and the thresholds sit just below it so the gate protects
the status quo without flaking. Publish/deploy stages remain deliberately absent — this project has
no registry or environment to deploy to, and a fake stage would just be pipeline theater.

## References

- ADR-0002 (contract-first API development)
