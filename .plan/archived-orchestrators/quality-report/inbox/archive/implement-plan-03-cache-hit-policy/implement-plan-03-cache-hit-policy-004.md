envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=candidate-lesson
created=2026-10-29T13:11:32Z

## Candidate lesson: an inline review-fix commit made during finalize triage needs its own change-ledger row, or the 5-execute -> 6-finalize transition refuses

**Source**: script failure in the plan work log, 2026-10-29T09:38:49Z / 09:38:54Z:
- `[ERROR] script_failure notation=plan-marshall:manage-status:manage-status exit_code=1 ... 1 of 4 worktree-branch commit(s) since diverging from main carry no kind=change ledger row`
- `Transition 5-execute refused: execute_commits_unrecorded (signature=bookkeeping_gap) — commit c745f1af... carries no kind=change ledger row (1 of 4 branch commits unrecorded)`

**What happened**: the finalize wait-region unified triage handled the review-bot and Sonar findings in one loop-back iteration (work log 09:29:19Z: "1 fix task (TASK-4), 1 inline fix committed as c745f1af, 1 suppression"). TASK-4 went through phase-5 and got its per-deliverable ledger row (467a857b). The inline Sonar fix c745f1af was committed directly by the triage step and was never recorded in the change ledger. When phase-5 re-entry tried to transition back to 6-finalize, the commit-coverage invariant refused. It was fixed by appending the missing `manage-change-ledger append --kind change` entry before capturing the handshake.

**Corrective rule (proposed)**: any commit made outside phase-5 task execution on the plan branch (an inline triage fix, a review-fix commit, a suppression commit) must append its `kind=change` ledger row at commit time, in the same step that creates the commit. Candidate fix site: the wait-region / verification-feedback inline-fix path should do the ledger append itself, so the transition guard never sees the gap.

**Scope**: plan-marshall phase-6-finalize wait-region triage and verification-feedback inline-fix paths; manage-change-ledger.
