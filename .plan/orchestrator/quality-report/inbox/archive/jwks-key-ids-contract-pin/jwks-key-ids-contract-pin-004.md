envelope_version=1
sender_type=plan
sender_id=jwks-key-ids-contract-pin
epic=quality-report
kind=candidate-lesson
created=2026-11-10T12:55:15Z

# Candidate lesson: `architecture --plan-id` is a router flag and must come before the verb

**Source signal:** script-failure cluster `plan-marshall:manage-architecture:architecture` (1 occurrence, work-log hash `ad988d`, script-log hash `76e529`, 2026-11-10T08:48:01Z, phase 5-execute, finalize loop-back 1 while working on TASK-7).

**Component (suggested):** `plan-marshall:manage-architecture` (caller: `plan-marshall:execute-task` / `plan-marshall:phase-5-execute`)

## What happened

The executing agent ran a content search with the plan id appended after the verb:

```text
architecture search --content --pattern 'getRealm|realm\(\)|sanitiz' --plan-id jwks-key-ids-contract-pin
```

`architecture.py` declares `--plan-id` (and `--project-dir`) only at the top level. The call was rejected with `exit_code=2`, `error: misplaced_router_flag`, `flags: [--plan-id]`, and the script printed the corrected form: `architecture.py --plan-id jwks-key-ids-contract-pin search --content --pattern ...`. Two minutes earlier in the same envelope (08:45:03, 08:45:05) the agent had issued two `architecture --plan-id ...` calls in the correct order, so the position was known but not applied consistently.

## Corrective rule (candidate)

For `manage-architecture:architecture`, place `--plan-id` / `--project-dir` before the verb (`architecture --plan-id P search --content --pattern X`), never after it. This is the "router-scoped flag placed after the verb" argparse-rejection signature. The script already gives a precise `misplaced_router_flag` hint, so the remaining fix is on the caller side: the canonical invocation the agent copies from should show the flag before the verb.

## Classification notes for the orchestrator

ACTIONABLE, plan-marshall bundle scope. Very likely a recurrence of an existing argparse-rejection / misplaced-router-flag lesson — check for a `merge_into` before filing a new one. Side observation (not a separate candidate): after the worktree was removed, the same correctly-ordered call returns `status: success` with `files_scanned: 0`, so a post-merge search through `--plan-id` silently scans nothing.
