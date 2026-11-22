envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:36Z

component=token-sheriff-validation
category=anti-pattern
created=2026-11-13
source=pr-comment e9b6e2 (CodeRabbit, PR #899, resolution fixed 2b3bfdcc)

# Security reference cited the wrong mechanism for the logout-token / ID-token separation

## What happened

`doc/validation/security-reference.adoc:107` said the reverse direction is closed because the logout-token validator never
produces a `TokenContent`. That does not stop a raw logout JWT from reaching `IdTokenValidationPipeline`, which takes the
raw token string and checks neither `events` nor `nonce`. Whether such a token is refused depends on the ID-token claim
checks and on the issuer's `expectedTokenType`. The paragraph was corrected.

## Proposed rule

A security-reference claim that one path cannot be confused with another must name the check that actually refuses the
input. A result-type separation does not imply input-side separation.
