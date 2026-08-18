# ADR-0023: Compute the upload content hash in one streaming pass; defer upload streaming and bulkhead

## Context

Uploads held up to three full copies of the file in heap: two `getBytes()` reads for
digest checking/persistence, plus the buffered ciphertext in the S3 adapter. The 10MB
cap (ADR-0022 follow-up) bounds this at ~30MB per request.

## Problem

How much of the full streaming redesign (streamed ciphertext to S3, upload concurrency
bulkhead) to implement now, given the cap already bounds the risk.

## Decision

We will hash each upload exactly once, streaming (`Sha256Digest.hexOf(InputStream)`):
`StatementUploadService` computes the hash, validates it against `X-Message-Digest`
(now a pure string compare), and passes it to `uploadStatement` for persistence.
Streaming ciphertext to S3 and a concurrency bulkhead are deferred.

## Alternatives

- Full streaming redesign now — rejected: a wrong computed Content-Length silently corrupts uploads,
  and a misordered bulkhead filter is an unauthenticated DoS vector; not worth it at 10MB.
- Do nothing — rejected: the duplicate reads were pure waste, removable safely.
- Hash inside `ValidationUtil` — rejected: validators shouldn't do file I/O.

## Consequences

- Per-upload heap drops from ~3x to ~1x file size (the S3 adapter's ciphertext buffer).
- Accepted limitation: ciphertext still buffered; upload concurrency unbounded under
  virtual threads. Raising the size cap without the deferred streaming work reopens OOM risk.
- Externally observable behavior unchanged (validation order, error codes, audit reasons).

## Implementation Notes

- `Sha256Digest`: streaming overload, caller owns the stream.
- `StatementUploadService.computeContentHash`: wraps `IOException` in `DigestComputationException`.
- `ValidationUtil.validateMessageDigest(String, String)`: comparison-only.
- `StatementService.uploadStatement`: gains `contentHash` param; `readBytes` deleted.
- `application.yml`: explicit `file-size-threshold: 0` (digest pass re-reads the disk-spooled part).

## References

- ADR-0020 (rate limiting), ADR-0022 (10MB cap + 413)
