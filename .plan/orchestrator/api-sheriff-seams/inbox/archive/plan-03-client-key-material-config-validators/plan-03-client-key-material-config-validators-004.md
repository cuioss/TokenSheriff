envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T14:57:00Z

component=plan-marshall:phase-6-finalize
category=improvement
status=active

# An operator-accepted weakened-test finding is re-raised at every new head, and each re-raise needs a manual close

The review loop cannot record an acceptance of a test replacement by identity, so one decision was asked about and overridden repeatedly.

## What happened

In plan plan-03-client-key-material-config-validators the operator accepted replacing `shouldRejectBlankAllowedHost` with the broader `shouldRejectNonBareAllowedHost`. The derived `weakened_test` finding was re-raised at each later head, and the review rounds could only be closed by operator override each time: `may_close: operator_override`, acceptance `operator_override`, and three entries in `functional_review_override_heads` (6ab07597, b23e0d0b, f0f7cf41). `loop-back close --accept` was also refused (`accepted_removal_head_unvalidated`) until a review round had been recorded at the head being closed, and the verifier answered `may_close: false` on every delta round while a blocking finding remained.

## Suggestion

Let an accepted `weakened_test` finding carry the test identity it was accepted for, so a later head that keeps that identity does not re-raise it. Separately, the `--accept` refusal text could name the missing round-at-head step.
