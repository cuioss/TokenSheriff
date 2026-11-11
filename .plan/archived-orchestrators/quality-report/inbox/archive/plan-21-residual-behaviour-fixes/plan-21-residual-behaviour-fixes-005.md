envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:27Z

# Candidate lesson: Q-Gate description-drift flags intentional ADR pointers from the phase-4 ADR scan (finding 12845b, accepted)

Source signal: Q-Gate finding, phase 4-plan, severity warning, resolution accepted.

## What happened

"Description drift: TASK-1 uses structural token '17' not present in deliverable outline."
TASK-1's description cited ADR 17 and ADR 18, which the phase-4 ADR scan surfaced for
token-sheriff-validation as lifecycle-contract pointers. Deliverable 1's outline mentioned only
ADR-0017, so the drift validator flagged the numeric token. Accepted as an intentional pointer;
no value was regularized.

## Possible corrective direction (for orchestrator classification)

The description-drift validator treats ADR numbers injected by phase-4's own ADR scan as drift.
Either whitelist ADR references that originate from the ADR scan, or have the scan record its
pointers so the validator can exempt them — avoiding a recurring false positive that must be
accepted by hand.

Components: plan-marshall:phase-4-plan (description-drift Q-Gate, ADR scan).
