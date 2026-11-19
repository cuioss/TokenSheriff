envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:31:01Z

# Candidate lesson: estimated figures were fed to the add-only 6-finalize usage accumulator before the measured ones arrived

- Signal: process observation 8 of the run (decision log WARNING)

## What happened

Twice in this finalize the orchestrator added estimated usage figures to the 6-finalize accumulator before the dispatched
agent's usage report had been read. The accumulator only adds, so a later correction could top up the shortfall but could
not take back an overshoot:

- Second sonar-roundtrip agent: estimated 60000 tokens / 12 tool uses / 60000 ms; measured 87880 / 22 / 55874. Tokens
  (+27880) and tool uses (+10) topped up; duration stays 4126 ms too high.
- Third unified-triage agent: estimated 190000 / 30 / 100000 ms "from a call made in error"; measured 292253 / 17 / 32092.
  Tokens topped up (+102253); tool_uses stay 13 too high, duration 67908 ms too high.

The dispatch-boundary rows carry the measured figures in both cases. A related case in phase 5: envelope 1's boundary row
was recorded from interim usage (251481 tokens) while the final usage was 362100; no second row was written, to avoid
double-counting.

## Evidence

- Decision log 55171f (2026-11-18T21:27:26Z), 8e0489 (2026-11-19T06:03:21Z).
- Work log 5a5903 (15:44:57Z, envelope 1 interim usage).

## Observation for the epic

The accumulator has no subtract or replace path, so any figure entered before the measured report is read leaves a
permanent error in the phase totals.
