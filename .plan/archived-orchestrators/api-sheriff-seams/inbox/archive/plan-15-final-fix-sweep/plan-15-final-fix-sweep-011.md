envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:53Z

# Candidate lesson: orchestrator-tier gate-rewrite commits carry no change-ledger row, and that blocks 5-execute -> 6-finalize

**Signal:** script failure cluster `plan-marshall:manage-status:manage-status` (boundary cause `error`)
**Records:** work log b36fca (2026-11-20T20:49:49Z, `script_failure ... manage-status exit_code=1 ... 2 of 51 worktree-branch commit(s) ... carry no kind=change ledger row`); 3758be (20:49:55Z, `Transition 5-execute -> 6-finalize refused: execute_commits_unrecorded (signature bookkeeping_gap) — 056620be..., 3f6b2760... (orchestrator gate-rewrite commits made after the leaf envelope)`); c37346 (20:51:05Z, recovered by appending handback-commit ledger rows).

## What happened

TASK-6 handed back its quality-gate verify to the orchestrator tier (`verify -Ppre-commit`, `execution_tier=orchestrator`). The orchestrator ran it, and the gate's formatter rewrote files. The orchestrator committed those rewrites as 056620be and 3f6b2760 outside any leaf envelope. No one wrote their `kind=change` ledger rows, because the Step 10a settlement belongs to the leaf's deliverable commit. The phase transition then refused. The orchestrator recovered by hand.

## Why it may be worth carrying

A verification hand-back that ends in a commit is a commit path of its own. Its ledger row should be written by whatever makes the commit (the hand-back path), or the hand-back workflow should say so. Otherwise every orchestrator-tier gate that rewrites sources leads to a refused transition.
