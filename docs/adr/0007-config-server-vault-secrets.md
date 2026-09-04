# 0007 — Config Server and Vault for configuration and secrets

## Context

Configuration (endpoint whitelist rules, cleanup schedules, storage paths) and secrets (master
encryption key, HMAC signing secret, DB credentials) both need to vary per environment without
being baked into the image.

## Problem

Hardcoding configuration into the image, or relying on plain environment variables, is insecure
for secrets and hard to manage consistently across environments.

## Decision

A Spring Cloud Config Server module centralizes non-secret configuration from a Git-style repo
(`infra/config-repo`). Secrets come from HashiCorp Vault, reachable only by trusted services
(`statement-service`, `config-server`).

## Alternatives

Plain environment variables alone are fine for non-secrets, but don't give us enough of a
rotation/audit story for secrets. Kubernetes Secrets was another option, but it's Kubernetes-native
only, and this project also runs under plain Docker Compose.

## Consequences

Configuration is centralized and environment-specific without code changes, but both services now
depend on Config Server — and, transitively, Vault — being available at startup.

## Implementation Notes

`config-server` module; `infra/config-repo/statement-service/*.yml`; Vault wiring in
`infra/bootstrap_vault.sh`.

## References

- [Spring Cloud Config](https://spring.io/projects/spring-cloud-config)
- [HashiCorp Vault](https://www.vaultproject.io/)
