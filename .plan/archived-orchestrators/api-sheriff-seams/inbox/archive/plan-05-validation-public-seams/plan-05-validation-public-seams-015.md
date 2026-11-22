envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:55Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=pr-comment 3e7c3c (CodeRabbit, PR #899, resolution fixed by TASK-15)

# Malformed iat/exp were refused as SIGNATURE instead of their named checks

## What happened

A signed logout token with a non-numeric `iat` or `exp` failed in `OffsetDateTimeMapper` during claim mapping inside
`verifySignature`. `TokenBuilder` wrapped that as a `MISSING_CLAIM` `TokenValidationException`, and
`LogoutTokenValidator.validate` caught it and returned `SIGNATURE`. The security reference documented `ISSUED_AT` /
`EXPIRATION` for "not a number". The test suite removed `iat` but never sent a present non-numeric value. TASK-15 keeps
the check identity and adds tests for both claims.

## Proposed rule

When a refusal enum is documented per claim, test each documented refusal reason with an input that reaches it
(absent, wrong type, out of range). Do not let a broad catch in an upstream step take over a downstream check's identity.
