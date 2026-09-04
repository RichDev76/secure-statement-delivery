# 0023 — Compute the upload content hash in one streaming pass; defer upload streaming and bulkhead

**Addendum (2026-08):** the deferred ciphertext streaming has since landed. `StatementFileStore`
is now a pull-based port (`StreamSupplier`), `FileCipher` exposes `encryptingStream()` and
`ciphertextLength()`, and the S3 adapter PUTs with a precomputed Content-Length, so no ciphertext
buffer remains anywhere in the path. The one-pass digest decision below is unaffected; the
concurrency bulkhead is still deferred.

## Context

Uploads were holding up to three full copies of the file in heap at once: two `getBytes()` reads
for digest checking and persistence, plus the buffered ciphertext in the S3 adapter. The 10MB cap
we added as an ADR-0022 follow-up bounds that at roughly 30MB per request, which is survivable but
still wasteful.

## Problem

Given that the cap already bounds the risk, how much of the full streaming redesign — streamed
ciphertext to S3, an upload concurrency bulkhead — is worth doing right now versus later?

## Decision

Hash each upload exactly once, streaming, via `ContentDigest.hexOf(InputStream)` (adapter:
`Sha256ContentDigest`). `StatementUploadService` computes the hash, validates it against
`X-Message-Digest` (now a plain string compare), and hands it to `uploadStatement` for
persistence. Streaming ciphertext to S3 and the concurrency bulkhead stay deferred for now.

## Alternatives

Doing the full streaming redesign immediately was on the table, but a wrongly computed
Content-Length would silently corrupt uploads, and a misordered bulkhead filter opens an
unauthenticated DoS vector — too much risk to take on at a 10MB cap. Doing nothing wasn't right
either: the duplicate reads were pure waste and safe to remove on their own. We also considered
hashing inside `UploadRequestValidator`, but validators doing file I/O felt like the wrong place
to put it.

## Consequences

Per-upload heap drops from roughly 3x to 1x file size — the S3 adapter's ciphertext buffer, which
the addendum above has since removed entirely. We're accepting that upload concurrency stays
unbounded under virtual threads for now (the ciphertext-buffering piece of that risk was closed by
the addendum's streaming work). Externally observable behavior — validation order, error codes,
audit reasons — is unchanged.

## Implementation Notes

`Sha256ContentDigest` gets a streaming overload; the caller owns the stream.
`StatementUploadService.computeContentHash` wraps `IOException` in `DigestComputationException`.
`UploadRequestValidator.validateMessageDigest(String, String)` is comparison-only now.
`StatementService.uploadStatement` gains a `contentHash` parameter and `readBytes` is gone.
`application.yml` sets `file-size-threshold: 0` explicitly, since the digest pass re-reads the
disk-spooled part otherwise.

## References

- ADR-0020 (rate limiting), ADR-0022 (10MB cap + 413)
