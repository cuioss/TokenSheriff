envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:55Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=pr-comment d1c4a8 (CodeRabbit review body, outside-diff, PR #899, resolution fixed by TASK-18)

# numericDate floored fractional NumericDate values

## What happened

`LogoutTokenValidator.numericDate` floored `exp`/`iat` to whole seconds. With `exp = N + 0.9` and clock `N + 0.1`, the
token was refused although it had not expired, and the `iat` freshness edges moved. RFC 7519 permits non-integer
NumericDate values. TASK-18 keeps the fraction as an `Instant` and adds fractional boundary tests. The finding arrived as an
outside-diff comment in a review body, not as an inline thread.

## Proposed rule

Convert JWT NumericDate values to `Instant` with sub-second precision. Include fractional-second cases in boundary tests.
Pipeline note: findings inside a review body's outside-diff section are as actionable as inline ones and must be ingested.
