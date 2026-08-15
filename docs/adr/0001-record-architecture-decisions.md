# 0001 — Record architecture decisions

## Context

The project's initial commit imported a working, opinionated architecture wholesale, with no ADRs
recording the reasoning behind it — only code and a static overview doc.

## Problem

Decisions discussed and implemented but never recorded lead to knowledge loss and silent drift
as the system evolves.

## Decision

Use Architecture Decision Records under `docs/adr/`, one file per significant decision, following
Context/Problem/Decision/Alternatives/Consequences/Implementation Notes/References. ADRs 0002–0009
backfill the decisions embedded in the initial commit; later ADRs are added as decisions are made.

## Alternatives

- Wiki/Confluence: drifts out of sync with the code it describes.
- Issue tracker only: not structured for durable, browsable rationale.

## Consequences

Traceable history of why the system looks the way it does; small ongoing documentation overhead.

## References

- `docs/standards/adr-guidelines.md`
- [Michael Nygard's ADR template](https://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions)
