envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:41Z

# Candidate lesson: 5-execute -> 6-finalize transition refused twice for commits with no change-ledger row

**Signal source:** script-failure cluster, two work-log `[ERROR] ... script_failure` lines on the same notation:
- 2026-11-02T07:54:57Z: "1 of 10 worktree-branch commit(s) since diverging from main carry no kind=change ledger row" (commit c5a90323...)
- 2026-11-02T10:08:16Z: "3 of 15 worktree-branch commit(s) ... carry no kind=change ledger row" (first missing commit 59de8916...)

**Notation:** plan-marshall:manage-status:manage-status (phase transition)
**Failure kind:** script_internal_failure, exit_code=1

## What happened

Both times, commits had landed on the worktree branch outside the phase-5-execute Step 10a settlement bookkeeping, so they had no `kind=change` ledger row. The transition guard refused the move until the rows were appended through `manage-change-ledger append --kind change`. The log records only the first missing SHA of each refusal (c5a90323 and 59de8916). 59de8916 is the finalize code-review loop-back fix. The second refusal came after the code-review loop-backs (59de8916, 46ab79a5) and the CodeRabbit doc fix committed inline in the triage step (58e6e12c), all of which were committed outside the phase-5 per-deliverable commit path. The orchestrator should check which three commits were missing rows; this attribution comes from commit timing and is not confirmed.

## Candidate rule

Every commit path that can advance the worktree branch outside the per-deliverable commit (finalize code-review loop-back fixes, triage inline fixes, operator-directed fix tasks) must append its own `kind=change` ledger row when it commits. Otherwise the next 5 -> 6 transition fails. The guard works correctly. The gap is that the inline-commit paths in phase-6-finalize (code-review loop-back, wait-region triage inline fix) do not do the settlement bookkeeping themselves.
