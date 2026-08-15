# 0013 — Split exception handler chain with a safe generic catch-all

## Context

`GlobalExceptionHandler` was one `@RestControllerAdvice` importing exception types from every
feature — the cross-feature coupling [0011](0011-adopt-feature-first-hexagonal-packaging.md)
avoided everywhere else. Its catch-all mapped *any* unmapped `RuntimeException`/`Exception` to a
400/500 with `ex.getMessage()` attached verbatim, leaking a server filesystem path in one case and
PII (account number, IP, user-agent) in another. No test proved any exception mapped to the right,
safe response.

## Problem

Close the information-disclosure gaps and miscategorization, resolve the cross-feature coupling,
and add the missing test coverage — without changing any response that was already correct.

## Decision

1. **Safe, `ErrorResponse`-aware catch-all**: a framework-classified 4xx exposes its message; every
   other case (including a misclassified 5xx) logs at ERROR server-side and returns a fixed
   generic detail. The old blanket "unmapped → 400 with raw message" handler is retired.
2. **Per-feature `@RestControllerAdvice` beans** (`Statement`, `Upload`, `Download`, `Search`),
   each with an explicit `@Order` so overlaps resolve deterministically rather than via Spring's
   bean-registration tie-break. `ExceptionHandlerDisjointnessTest` fails the build if any exception
   type is ever claimed by more than one handler. `GlobalExceptionHandler` shrinks to framework
   validation exceptions plus the catch-all, at lowest precedence.
3. Targeted fixes bundled in the same pass: local file-store errors no longer include the
   filesystem path; audit-write failure logs stop including PII; a URI-construction failure in
   signed-link building now propagates instead of silently returning a broken link;
   `LocalDate.parse` call sites in search now raise the same typed exception as every sibling
   date-parsing path instead of leaking a raw `DateTimeParseException`.

## Alternatives

- Fix only the message leak, defer the structural split: rejected — leaves the cross-feature
  coupling unresolved with no forcing function to revisit it.
- Shared abstract base class for the handler beans: rejected — the only shared logic is two
  static helpers; each handler differs enough that inheritance adds indirection, not less code.

## Consequences

- Every already-correct response is unchanged (proved by the pre-existing IT suite passing with
  zero assertion changes).
- An unmapped exception now returns a generic 500 instead of a 400 with its raw message.
- Deferred, not silently dropped: I/O-failure status codes on two upload exceptions still return
  400 for server-side failures; duplicate AOP logging across the service/controller boundary is
  cosmetic; a few pre-existing bare `catch (Exception ignored) {}` sites are out of scope.

## Implementation Notes

Four phases (safety-net → structural split → targeted fixes → contract test), each gated on
`mvn clean verify`.

## References

- docs/standards/security.md, docs/standards/testing.md
- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0012 — Config-driven security endpoint matchers](0012-config-driven-security-endpoint-matchers.md)
