envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:20:05Z

# Finalize-step commits leave the change ledger unrecorded

**Defect**: The execute->finalize transition refused twice with `execute_commits_unrecorded`, because commits made by finalize settle steps (security-audit hardening, CodeRabbit fix rounds) had no change-ledger rows. Rows had to be appended by hand via `manage-change-ledger append --kind change`.

**Corrective action**: Commits produced by finalize steps (fix rounds, hardening loop-backs) should record their change-ledger rows automatically at commit time, so the transition invariant never needs manual repair.

**Evidence**: plan-13-log-transport-hygiene work log (`execute_commits_unrecorded` refusals and the manual `manage-change-ledger append` calls).

**Suggested component**: plan-marshall:manage-change-ledger / plan-marshall:phase-6-finalize
