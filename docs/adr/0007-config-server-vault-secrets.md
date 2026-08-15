# 0007 — Config Server and Vault for configuration and secrets

## Context

Configuration (endpoint role matrices, cleanup schedules, storage paths) and secrets (master
encryption key, HMAC signing secret, DB credentials) both need to vary per environment without
being baked into the image.

## Problem

Hardcoding configuration in the image or plain environment variables is insecure for secrets and
hard to manage consistently across environments.

## Decision

A Spring Cloud Config Server module centralizes non-secret configuration from a Git-style repo
(`infra/config-repo`). Secrets are sourced from HashiCorp Vault, reachable only to trusted
services (`statement-service`, `config-server`).

## Alternatives

- Plain environment variables only: adequate for non-secrets, insufficient rotation/audit story
  for secrets.
- Kubernetes Secrets: Kubernetes-native only; the project also runs under plain Docker Compose.

## Consequences

Centralized, environment-specific configuration without code changes; both services depend on
Config Server (and, transitively, Vault) availability at startup.

## Implementation Notes

`config-server` module; `infra/config-repo/statement-service/*.yml`; Vault wiring in
`infra/bootstrap_vault.sh`.

## References

- [Spring Cloud Config](https://spring.io/projects/spring-cloud-config)
- [HashiCorp Vault](https://www.vaultproject.io/)
