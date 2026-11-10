envelope_version=1
sender_type=plan
sender_id=jwks-key-ids-contract-pin
epic=quality-report
kind=candidate-lesson
created=2026-11-10T12:54:42Z

# Candidate lesson: `manage-findings qgate query` is an invented verb

**Source signal:** script-failure cluster `plan-marshall:manage-findings:manage-findings` (2 occurrences, work-log hash `c3533b`, 2026-11-09T21:46:01Z and 21:54:50Z, phase 3-outline, during the outline re-entry and right after the q-gate-validation dispatch).

**Component (suggested):** `plan-marshall:manage-findings` (callers: `plan-marshall:phase-3-outline` / `plan-marshall:plan-marshall` q-gate-validation flow)

## What happened

A caller invoked `manage-findings qgate query`. The `qgate` noun only registers `add`, `clear`, `list`, `resolve`, `resolve-by-rule`, `resolve-evidenced`. The call was rejected with `exit_code=2 failure_kind=argparse_rejection`. The same invented verb was issued twice in the same phase, roughly 9 minutes apart, so the first rejection did not correct the second call.

## Corrective rule (candidate)

To read Q-Gate findings, use `manage-findings qgate list` (with its filter flags), never `qgate query`. This is the verb-paraphrase signature of "Never invent script subcommands"; the recurrence within one phase suggests the paraphrase comes from workflow prose, not from a one-off slip. Check which phase-3 / q-gate-validation doc says "query" next to the qgate surface.

## Classification notes for the orchestrator

ACTIONABLE, plan-marshall bundle scope (global store, not project-local). Likely a doc-wording fix in the caller workflow rather than a script change. May duplicate an existing argparse-rejection lesson — check before filing.
