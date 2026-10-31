envelope_version=1
sender_type=plan
sender_id=plan-07-jwks-staleness-timeouts
epic=quality-report
kind=candidate-lesson
created=2026-10-30T16:56:05Z

# Candidate lesson: phase skills self-transition and leave the metrics boundary unstamped

**Source signal**: work log orchestrator self-check repairs at two phase boundaries.

**Component**: plan-marshall phase boundary ownership - `plan-marshall:phase-3-outline`, `plan-marshall:phase-4-plan`, `plan-marshall:manage-metrics` boundary stamping, `plan-marshall:plan-marshall` orchestrator boundary call.

## What happened

Twice in one plan a dispatched phase leaf transitioned the plan's phase itself, before the orchestrator's boundary call, so the metrics boundary for that transition was never stamped. The orchestrator detected the gap afterwards and stamped it retroactively:

- 06:01:59Z (e68e75): "stamped missing metrics boundary 3-outline -> 4-plan (phase-3-outline self-transitioned before orchestrator boundary call)".
- 07:58:31Z (890092): "stamped missing metrics boundary 4-plan -> 5-execute (phase-4-plan self-transitioned)".

A retroactive stamp carries the repair time, not the real boundary time, so per-phase duration/token metrics for the affected phases are skewed. Related: at 12:24:23Z a phase-5 leaf logged its completion line under the wrong prefix (`plan-marshall:phase-execute`) and had to re-log it, another sign that leaf-side boundary bookkeeping is improvised.

## Candidate rule

Exactly one owner performs a phase transition and its metrics stamp, atomically. Either the transition verb itself stamps the metrics boundary (so whoever transitions cannot leave it half-done), or phase leaves must not call the transition and must return to the orchestrator, which transitions and stamps in one step. The orchestrator's post-phase repair is a symptom detector, not the fix.

## Evidence pointers

- Work log hash e68e75 and 890092 (both repairs), 18f434 (wrong-prefix completion line).
