envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:52Z

# Candidate lesson: phase-5 envelope stalled on a review-disposition task beside a valid build hand-back (B7 reclassification)

- Signal: script-failure cluster, notation `plan-marshall:phase-5-execute`
- Marker classes: one B7 no-progress reclassification (decision log) plus `[ERROR]` / `[BLOCKED]` lines emitted under the
  phase-5-execute component

## What happened

Envelope 3 returned after TASK-7/8/9 with their builds owed to the orchestrator tier, but TASK-10 (disposition every
undisposed review-body finding on eight older PRs) had finished 1 of 8 PRs when the context budget ran out. The B7
no-progress predicate reclassified the `voluntary_checkpoint` as `error` (in_progress=1, completed_delta=0,
353441 tokens). The orchestrator ran the owed build first, then re-dispatched TASK-10, which completed in the recovery
envelope.

Separately, envelope 7 ended with `[BLOCKED] Worktree state not verified: worktree_mutated` and refused the 5-execute to
6-finalize transition until the orchestrator ran the final quality sweep.

## Evidence

- Decision log b7c7e9 (17:12:43Z, reclassification), 83b6b7 (build consumed first, recovery re-dispatch), 44ce83 (an
  earlier escalate_ask boundary deliberately not counted as a stall).
- Work log 660379 (envelope 3 return: "context budget exhausted before PRs 899-920"), c84129 (recovery dispatch),
  e74561 (19:09:11Z, BLOCKED worktree_mutated).
- The `[ERROR] (plan-marshall:phase-5-execute) Script ... manage-change-ledger ...` lines are attributed to this notation
  as well; their content is the change-ledger candidate.

## Observation for the epic

A single task that walks eight PRs does not fit one envelope; splitting it per PR would have avoided the stall and the
recovery dispatch.
