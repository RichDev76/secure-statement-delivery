# 0001 — Record architecture decisions

## Context

The project's initial commit imported a working, opinionated architecture wholesale, with no ADRs
explaining any of the reasoning behind it — just code and a static overview doc.

## Problem

Decisions that get discussed and implemented but never written down lead to knowledge loss and
quiet drift as the system evolves and nobody remembers why something is the way it is.

## Decision

We're using Architecture Decision Records under `docs/adr/`, one file per significant decision,
following Context/Problem/Decision/Alternatives/Consequences/Implementation Notes/References. ADRs
0002–0009 backfill the decisions already embedded in the initial commit; later ADRs get added as
new decisions are made.

## Alternatives

A wiki or Confluence page was an option, but those tend to drift out of sync with the code they
describe once nobody's watching. Keeping rationale in the issue tracker alone wasn't structured
enough either — it doesn't give you a durable, browsable record of why.

## Consequences

We get a traceable history of why the system looks the way it does, at the cost of a small amount
of ongoing documentation overhead.

## References

- [Michael Nygard's ADR template](https://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions)
