envelope_version=1
sender_type=plan
sender_id=plan-07-jwks-staleness-timeouts
epic=quality-report
kind=candidate-lesson
created=2026-10-30T16:55:39Z

# Candidate lesson: per-deliverable commits carried the wrong Co-Authored-By model trailer

**Source signal**: decision log (operator decision 2026-10-30T10:02:29Z, hash 760099) plus a downstream 5-execute -> 6-finalize transition refusal.

**Component**: plan-marshall commit-trailer resolution for phase-5-execute per-deliverable commits (`run_config` commit-trailer resolver / workflow-integration-git commit path).

## What happened

The 6 unpushed per-deliverable commits of phase 5 (21cd13b9..13ff928a) carried `Co-Authored-By: Claude Opus 5.5` although the operator required `Claude Sonnet 5.5` for this plan's commits. The mismatch was caught only by the operator before push. Remedy was a `filter-branch` rewrite of all 6 commits (new SHAs 32476580..7380db55) and a recaptured 5-execute handshake.

Knock-on cost: the rewrite left the change-ledger `kind=change` rows pointing at the OLD SHAs (the decision-log entry says so explicitly). The later 5-execute -> 6-finalize transition at 12:24:13Z was refused with `execute_commits_unrecorded` / `bookkeeping_gap` ("1 of 9 worktree-branch commits carry no kind=change ledger row ... also flags a recorded row pointing outside HEAD history; remap suggested").

## Candidate rule

1. The commit trailer must be resolved from the model that actually authored the commit (or an explicit plan/project override), and verified BEFORE the first per-deliverable commit, not discovered at push time.
2. Any history rewrite (filter-branch, rebase) of plan commits must be followed by a change-ledger SHA remap in the same step; otherwise the next guarded phase transition fails on a ledger/HEAD mismatch.

## Evidence pointers

- Decision log 10:02:29Z (760099): rewrite-before-push, old/new SHA ranges, "change-ledger kind=change rows still reference the old SHAs".
- Work log 12:24:13Z / 12:24:19Z: transition refused, `execute_commits_unrecorded`, remap suggested.
