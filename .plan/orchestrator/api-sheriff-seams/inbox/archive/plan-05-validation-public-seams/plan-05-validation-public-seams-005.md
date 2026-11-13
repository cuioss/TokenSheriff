envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:32Z

component=token-sheriff-validation-quarkus
category=bug
created=2026-11-13
source=qgate 6-finalize bff004 (functional-review, resolution fixed by TASK-14 e1246f7a)

# Disclosure opt-out honoured on only one of two probe paths, and a test pinned the leak

## What happened

`disclose-issuer=false` was honoured only on the EXTERNAL_VALIDATOR path of the JWKS readiness probe. The
PROPERTY_CONFIGURED path hard-coded `includeUrl = true` in `JwksEndpointHealthCheck.performHealthCheck`, so the payload
still carried `issuer.N.url`. The test `nonDisclosingPayload` asserted this leaking behaviour. The frozen criterion, the
outline test plan and ADR-0029 OD-6 all required no issuer identifier in the payload. Functional review caught it.

## Proposed rule

When an opt-out flag governs output on several code paths, route every path through one helper that reads the flag, and
write the non-disclosure test once per path against the criterion text, not against observed output.
