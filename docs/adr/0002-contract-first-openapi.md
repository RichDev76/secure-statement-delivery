# 0002 — Contract-first OpenAPI

## Context

`statement-service` exposes a REST API for uploading, searching, linking, downloading, and
auditing statements.

## Problem

Hand-written controllers and DTOs tend to drift from their documentation over time, and API
changes get hard to review when the spec is generated from code instead of the other way round.

## Decision

`statement-service-v1-openapi.yaml` is the source of truth. `openapi-generator-maven-plugin`
(`interfaceOnly: true`) generates server interfaces and models at build time, and controllers
implement those generated interfaces — nobody hand-edits generated code.

## Alternatives

We considered a code-first approach with springdoc/Swagger annotations, but implementation and
documentation can silently diverge that way, and API changes stop being reviewable as a single
diff.

## Consequences

Every endpoint change now starts in the YAML, and generated sources are never committed.
Contributors do need to know the generator's config and its limitations.

## Implementation Notes

Generator is configured in `statement-service/pom.xml`; output lands under
`target/generated-sources/openapi`.

## References

- [OpenAPI Specification](https://swagger.io/specification/)
