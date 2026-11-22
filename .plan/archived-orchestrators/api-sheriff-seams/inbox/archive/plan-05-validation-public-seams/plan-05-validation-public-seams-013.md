envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:53Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=pr-comment 1ed822 (CodeRabbit, PR #899, resolution fixed by TASK-15)

# Null-member filtering in MapRepresentation hid a prohibited 'nonce' claim

## What happened

`MapRepresentation.fromJson` drops top-level `null` members before `toNativeClaims()` copies the map, and
`SignatureVerifiedToken.getRawClaims()` returns that copy. `LogoutTokenValidator.validate` checked `containsKey("nonce")`,
so a signed logout token with `"nonce":null` passed. The OIDC Back-Channel Logout spec prohibits `nonce` whatever its
value. TASK-15 takes nonce presence from the parsed payload and adds a signed-token `"nonce":null` test.

## Proposed rule

A claim-presence prohibition must be checked against the raw parsed payload, before any normalisation that drops nulls.
Add a `"claim":null` test for every "must not be present" rule.
