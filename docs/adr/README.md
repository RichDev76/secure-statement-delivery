# Architecture Decision Records

Index of ADRs for `secure-statement-delivery`. See `docs/standards/adr-guidelines.md`
for format and process.

| # | Title | Status |
|---|---|---|
| 0001 | [Record architecture decisions](0001-record-architecture-decisions.md) | Accepted |
| 0002 | [Contract-first OpenAPI](0002-contract-first-openapi.md) | Accepted |
| 0003 | [HMAC-signed, single-use, time-limited download links](0003-hmac-signed-single-use-download-links.md) | Accepted |
| 0004 | [AES-GCM encryption at rest](0004-aes-gcm-encryption-at-rest.md) | Accepted |
| 0005 | [Local filesystem storage for encrypted statements](0005-local-filesystem-statement-storage.md) | Accepted |
| 0006 | [Keycloak JWT with per-endpoint RBAC](0006-keycloak-jwt-rbac.md) | Accepted |
| 0007 | [Config Server and Vault for configuration and secrets](0007-config-server-vault-secrets.md) | Accepted |
| 0008 | [Scheduled signed-link cleanup with ShedLock](0008-scheduled-cleanup-shedlock.md) | Accepted |
| 0009 | [Asynchronous, fail-open audit logging](0009-asynchronous-audit-logging.md) | Accepted |
| 0010 | [Migrate to Spring Boot 4.0.7 / Spring Cloud 2025.1.2](0010-migrate-to-spring-boot-4.md) | Accepted |
| 0011 | [Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md) | Accepted |
| 0012 | [Method-level role authorization with config-driven whitelist](0012-method-level-role-authorization.md) | Accepted |
| 0013 | [Split exception handler chain with a safe generic catch-all](0013-split-exception-handler-chain.md) | Accepted |
| 0014 | [Local disk storage to Floci/S3 migration](0014-local-storage-to-floci-s3-migration.md) | Accepted |
| 0015 | [Time-based signed links and envelope encryption](0015-time-based-signed-links-and-envelope-encryption.md) | Accepted |
| 0016 | [Migrate to PostgreSQL 18](0016-migrate-to-postgresql-18.md) | Accepted |
| 0017 | [UUIDv7 primary keys](0017-uuid-v7-primary-keys.md) | Accepted |
| 0018 | [`audit_logs` partitioning and index cleanup](0018-audit-log-partitioning-and-index-cleanup.md) | Accepted |
| 0019 | [Keep ShedLock over a hand-rolled `SKIP LOCKED` scheduler mutex](0019-shedlock-over-skip-locked.md) | Accepted |
| 0020 | [Signed-link abuse hardening](0020-signed-link-abuse-hardening.md) | Accepted |
| 0021 | [Dependency failure policy and health groups](0021-dependency-failure-policy-and-health-groups.md) | Accepted |
| 0022 | [Fail-closed error delivery with audit-on-failure for statement transfer paths](0022-fail-closed-error-delivery-and-audit-on-failure.md) | Accepted |
| 0023 | [Compute the upload content hash in one streaming pass; defer upload streaming and bulkhead](0023-single-streaming-digest-pass-and-deferred-upload-streaming.md) | Accepted |
| 0024 | [GitHub Actions CI pipeline](0024-github-actions-ci-pipeline.md) | Accepted |
| 0025 | [Structured JSON logging and no-PII-in-logs policy](0025-structured-json-logging-and-no-pii-policy.md) | Accepted |
| 0026 | [Defer ClamAV virus/malware scanning on statement uploads](0026-defer-clamav-malware-scanning.md) | Accepted |
