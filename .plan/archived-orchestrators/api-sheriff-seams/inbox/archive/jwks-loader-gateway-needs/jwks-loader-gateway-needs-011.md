envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:32Z

# Candidate lesson: manage-status failures, a missing change-ledger row at 5-execute exit and read --field

Source signal: script-failure cluster `plan-marshall:manage-status:manage-status`, 2 distinct failures.

## Records

1. 2026-11-13T14:20:31Z / 14:20:37Z (phase-5-execute exit): `manage-status transition --completed 5-execute` refused with `error=execute_commits_unrecorded signature=bookkeeping_gap`. 1 of 9 worktree-branch commits (`0d6b93bb921c7668dceae59895cdc47b3a54e19e`) had no `kind=change` ledger row (recorded_count=8, branch_commit_count=9), so the phase did not advance. The commit was made between the TASK-11 per-deliverable commit (2d71ac40) and the re-entry, apparently while the orchestrator-tier verification handback was being handled, and no writer settled its ledger row.
2. 2026-11-15T10:34:41Z (6-finalize, after adr-propose): `manage-status read --field ...` was rejected with exit_code=2. `read` declares only `--plan-id` and `--store`. `--field` belongs to the sibling verbs `metadata` and `update-field`.

## Candidate rules

- Every commit made on the worktree branch outside a per-deliverable Step 10a settlement, for example a commit made during the orchestrator verification handback, needs its own change-ledger row when it is made. Otherwise the 5-execute exit gate refuses the transition later.
- To read a single status field, use `manage-status metadata --field` (or read the full record). `read` has no field selector.

Component hint: plan-marshall:manage-status / plan-marshall:manage-change-ledger / plan-marshall:plan-marshall (verification handback commit path).
