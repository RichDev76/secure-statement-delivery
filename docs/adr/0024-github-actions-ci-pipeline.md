# ADR-0024: GitHub Actions CI pipeline

## Context

No CI existed: no `.github/` directory, no automated quality gates. Spotless's
`apply`+`check` are both bound to `validate`, so `apply` silently fixes formatting
before `check` ever runs — no lifecycle build can fail on it. The AI review checklist
already names `api-compat`, `dependency-scan`, and Trivy as required gates that had no
implementation.

## Problem

How to introduce CI with parity to the reference pipeline
(`RichDev76/statement-service-platform`), and how to gate formatting given the
`apply`-on-`validate` binding.

## Decision

GitHub Actions, seven jobs, Maven wrapper pinned to 3.9.11: `format` invokes
`spotless:check` directly (bypasses the lifecycle, so the bound `apply` never fires —
one job owns the formatting verdict; every other job passes `-Dspotless.skip=true`);
`build`, `unit-tests`, `integration-tests` (plain `./mvnw verify`, Testcontainers on
the runner's own Docker daemon, Ryuk disabled); `dependency-scan` (Trivy `vuln,secret`,
HIGH/CRITICAL, fail-closed); PR-only `api-compat` (oasdiff, skip-with-notice when the
contract is absent on the base branch); and `api-regression` (Bruno pack against the
live compose stack: ephemeral masked secrets per run, bounded health polling,
compose-logs artifact on failure, unconditional teardown). Every `uses:` is pinned to a
full commit SHA; the workflow grants only `permissions: contents: read`; every job has
a timeout; in-progress runs cancel except on `main`. `CiWorkflowConventionsTest`
(Surefire, no Docker) makes those invariants a regression test rather than a one-time
review. PIT mutation testing is deferred — see Consequences.

## Alternatives

- GitLab CI / CircleCI — rejected: the repository lives on GitHub.
- One mega-job — rejected: no fail-fast, slowest possible signal.
- Self-hosted runners / Testcontainers Cloud — rejected: ops burden/cost unjustified at
  this scale.
- Gate coverage on a JaCoCo threshold now — rejected: report-only until a baseline
  exists; a failing gate bolted onto unmeasured code is arbitrary.

## Consequences

- Every push/PR gets automated formatting, compile, unit (incl. ArchUnit), integration
  (Testcontainers), dependency-scan, and contract-compatibility gates.
- Formatting can now actually fail a build; local `mvn verify` still auto-formats
  (unchanged developer experience).
- PIT mutation testing is not implemented. `api-regression` runs on every push/PR but
  joins branch protection only after a sustained stretch of green runs — a flaky
  required check is worse than none.
- Docker Hub rate limits on unauthenticated pulls are mitigated by pre-pull-with-retry,
  not eliminated (see Implementation Notes).
- `permissions: contents: read` makes "CI never writes to the repo" structural.

## Implementation Notes

- `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`: generated via `mvn wrapper:wrapper -Dmaven=3.9.11`.
- `.github/workflows/ci.yml`: `format`, `build`, `unit-tests`, `integration-tests`,
  `dependency-scan`, `api-compat`, `api-regression`.
- `.github/dependabot.yml`: weekly, `github-actions`, `maven`, `docker` (both
  Dockerfile directories), minor+patch grouped.
- `.trivyignore`: empty; every future entry needs a CVE ID, justification, and expiry.
- `statement-service/pom.xml`: `jackson-dataformat-yaml` (test scope) for
  `CiWorkflowConventionsTest`.
- `statement-service/src/test/java/.../CiWorkflowConventionsTest.java`: parses `ci.yml`
  and `dependabot.yml`; asserts SHA pinning, least-privilege permissions, per-job
  timeouts, ref-conditional concurrency cancellation, no `pull_request_target`, and
  Dependabot ecosystem coverage.
- Discovered running Trivy locally: its Java scanner resolves POM parents/BOMs against
  Maven Central unless offline, and unauthenticated shared-runner IPs can hit its 429
  rate limit. `dependency-scan` therefore runs `setup-java` + `mvn dependency:resolve`
  to populate `.m2` first and sets `TRIVY_OFFLINE_SCAN: true`.
- Rollback: revert the `.github/` directory; no other code path depends on it.

## References

- `docs/standards/ai-review-checklist.md`
- ADR-0002 (contract-first API development)
