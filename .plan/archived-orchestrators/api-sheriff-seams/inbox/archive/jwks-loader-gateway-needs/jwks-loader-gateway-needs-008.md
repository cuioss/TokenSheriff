envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:01Z

# Candidate lesson: moving a mirrored list did not stop it drifting; the review loop needed a second round

Source signal: review-bot finding b1550e (coderabbit, PR #906, inline on JwtPropertyKeys.java:357, second review round at 6f3e8e2a), resolution fixed by TASK-17 (commit 611b96e2).

## What happened

After the first round (finding 29c7fa) the HTTP-only list was derived and guarded. The DPoP sub-property list (`DPOP_SUB_PROPERTIES`) had only been moved next to the key constants and still copied the `ISSUERS` keys under `DPOP_BASE` by hand, minus `DPOP_ENABLED`. A DPoP key missing from that list would let `validateNoOrphanedDpopProperties` accept it without warning while DPoP is disabled. The bot raised it in round two. TASK-17 added a reflection-based completeness guard in `JwtPropertyKeysTest`. That cost a second loop-back from 6-finalize to 5-execute and another CI and review cycle.

## Candidate rule

When a review finding names a defect class (here: hand-kept lists that copy a set defined elsewhere), fix every instance of that class in the touched module in the same fix task, not only the instance on the commented line. Moving a list next to its source does not count as deriving it.

Component hint: plan-marshall:phase-6-finalize triage (fix-task scoping) / token-sheriff-validation-quarkus config.
