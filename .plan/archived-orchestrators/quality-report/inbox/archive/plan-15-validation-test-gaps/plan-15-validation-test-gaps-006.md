envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:15:28Z

# Candidate lesson: commits made during a finalize loop-back carry no change-ledger row, so the 5-execute -> 6-finalize transition is refused

## Source records

- Work log [ERROR] 5fa811 (2026-11-05T21:17:10Z): script_failure notation=plan-marshall:manage-status:manage-status exit_code=1 failure_kind=script_internal_failure — "2 of 11 worktree-branch commit(s) since diverging from main carry no kind=change ledger row".
- Work log [ERROR] f89927: transition 5-execute -> 6-finalize refused, error=execute_commits_unrecorded signature=bookkeeping_gap, unrecorded commits b201659e and 7b8ed2a7, recorded_count=9 of branch_commit_count=11.

## What happened

After the code-review loop-back, phase 5 re-ran with 0 tasks pending and tried to transition back to 6-finalize. The transition guard refused it because two branch commits had no kind=change ledger row. 7b8ed2a7 is the commit that restored the enum tests on the operator's decision during the functional-review round in 6-finalize — a commit made outside a phase-5 task, so the Step 10a settlement bookkeeping never ran for it. The plan recovered, but only after the guard tripped.

## Candidate rule

Any commit landed on the plan branch outside a phase-5 task (an operator-directed fix during functional or code review, a formatter fixup) must get its change-ledger row at the time it is made, by whichever step made it. Otherwise the next 5-execute -> 6-finalize transition fails on a bookkeeping gap. Alternatively the finalize review steps that may commit should own that append themselves.

## Classification left to the orchestrator

plan-marshall workflow gap (finalize-time commits vs. change-ledger settlement), not a TokenSheriff fact.
