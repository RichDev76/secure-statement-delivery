# 0002 — Contract-first OpenAPI

## Context

`statement-service` exposes a REST API for uploading, searching, linking, downloading, and
auditing statements.

## Problem

Hand-written controllers and DTOs drift from their documentation over time, and API changes are
hard to review when the spec is generated from code rather than the other way round.

## Decision

`statement-service-v1-openapi.yaml` is the source of truth. `openapi-generator-maven-plugin`
(`interfaceOnly: true`) generates server interfaces and models at build time; controllers
implement the generated interfaces and never hand-edit generated code.

## Alternatives

- Code-first (springdoc/Swagger annotations): implementation and documentation can silently
  diverge; API changes aren't reviewable as a single diff.

## Consequences

Every endpoint change starts in the YAML; generated sources are never committed. Contributors
must know the generator's config and limitations.

## Implementation Notes

Generator configured in `statement-service/pom.xml`; output under
`target/generated-sources/openapi`.

## References

- `docs/standards/api.md`
- [OpenAPI Specification](https://swagger.io/specification/)
