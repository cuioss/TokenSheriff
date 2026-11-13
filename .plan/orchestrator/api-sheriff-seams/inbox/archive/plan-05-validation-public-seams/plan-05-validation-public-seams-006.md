envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:32Z

component=token-sheriff-validation-quarkus
category=anti-pattern
created=2026-11-13
source=qgate 6-finalize f38d46 (functional-review false_self_report, resolution fixed by TASK-14 e1246f7a)

# ADR decision text claimed a guarantee for the whole probe that the code gave on one path only

## What happened

ADR-0029 OD-6 stated that with `disclose-issuer=false` the per-issuer `issuer.N.url` entries are omitted for the JWKS
readiness probe as a whole. The code did so only for external validators (see bff004). Functional review classified the
ADR as a false self-report. The ADR became true once the code was fixed.

## Proposed rule

Before a deliverable closes, check each ADR decision sentence against every code path it covers. A decision record that
states a scope wider than the implementation is a defect, not a wording issue.
