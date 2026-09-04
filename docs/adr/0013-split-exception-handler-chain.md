# 0013 — Split exception handler chain with a safe generic catch-all

## Context

`GlobalExceptionHandler` was one `@RestControllerAdvice` importing exception types from every
feature — exactly the cross-feature coupling [0011](0011-adopt-feature-first-hexagonal-packaging.md)
avoids everywhere else. Its catch-all mapped any unmapped `RuntimeException`/`Exception` to a
400/500 with `ex.getMessage()` attached verbatim, which leaked a server filesystem path in one case
and PII (account number, IP, user-agent) in another. No test proved any exception mapped to the
right, safe response.

## Problem

Close the information-disclosure gaps and the miscategorization, resolve the cross-feature
coupling, and add the missing test coverage — without changing any response that was already
correct.

## Decision

The catch-all is now safe and `ErrorResponse`-aware: a framework-classified 4xx exposes its
message, but every other case — including a misclassified 5xx — logs at ERROR server-side and
returns a fixed generic detail. The old blanket "unmapped → 400 with raw message" handler is
retired.

In its place, per-feature `@RestControllerAdvice` beans (`Statement`, `Upload`, `Download`,
`Search`) each carry an explicit `@Order` so overlaps resolve deterministically instead of via
Spring's bean-registration tie-break. `ExceptionHandlerDisjointnessTest` fails the build if any
exception type is ever claimed by more than one handler. `GlobalExceptionHandler` itself shrinks
down to framework validation exceptions plus the catch-all, at lowest precedence.

A few targeted fixes rode along in the same pass: local file-store errors no longer include the
filesystem path; audit-write failure logs stop including PII; a URI-construction failure in
signed-link building now propagates instead of silently returning a broken link; and
`LocalDate.parse` call sites in search now raise the same typed exception as every sibling
date-parsing path instead of leaking a raw `DateTimeParseException`.

## Alternatives

Fixing only the message leak and deferring the structural split was tempting, but it would have
left the cross-feature coupling unresolved with nothing forcing a revisit. A shared abstract base
class for the handler beans didn't earn its keep either — the only shared logic is two static
helpers, and each handler differs enough that inheritance would add indirection rather than remove
code.

## Consequences

Every already-correct response is unchanged, which the pre-existing IT suite passing with zero
assertion changes proves. An unmapped exception now returns a generic 500 instead of a 400 with its
raw message. A few things are deferred rather than silently dropped: I/O-failure status codes on
two upload exceptions still return 400 for server-side failures, duplicate AOP logging across the
service/controller boundary is cosmetic, and a handful of pre-existing bare
`catch (Exception ignored) {}` sites are out of scope here.

## References

- [0011 — Adopt feature-first hexagonal packaging](0011-adopt-feature-first-hexagonal-packaging.md)
- [0012 — Method-level role authorization with config-driven whitelist](0012-method-level-role-authorization.md)
