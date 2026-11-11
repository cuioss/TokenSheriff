envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:33Z

# Candidate lesson: Q-Gate description-drift flags intentional ADR pointer on TASK-3 (finding 685ec8, accepted)

Source signal: Q-Gate finding, phase 4-plan, severity warning, resolution accepted.

## What happened

"Description drift: TASK-3 uses structural token '16' not present in deliverable outline."
TASK-3's description cited ADR 16 (validator ownership of JWKS loaders), surfaced by the
phase-4 ADR scan; deliverable 2's outline does not mention it. Accepted as an intentional
pointer; no value was regularized.

## Possible corrective direction (for orchestrator classification)

Second instance in the same run of the same false-positive shape as finding 12845b (ADR
numbers from the phase-4 ADR scan read as structural-token drift). Recurrence within one plan
strengthens the case for exempting ADR-scan pointers from the drift validator.

Components: plan-marshall:phase-4-plan (description-drift Q-Gate, ADR scan).
