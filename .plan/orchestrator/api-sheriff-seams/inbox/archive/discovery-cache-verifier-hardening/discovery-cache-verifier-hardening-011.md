envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:26:08Z

# Candidate lesson: failing script cluster plan-marshall:manage-tasks:manage-tasks

Source: script-failure cluster from `manage-logging signal-scan` (marker class `script_failure`, i.e. an `[ERROR] ... script_failure` work-log line for a non-zero exit). It is one of 2 distinct failing notations in this plan; the other is manage-status.

## Observation

At least one call to `plan-marshall:manage-tasks:manage-tasks` exited non-zero during this plan.
The plan created tasks in two waves: TASK-1 to TASK-7 by phase-4 `batch-add`, then the review-fix
tasks TASK-8 to TASK-14 added during finalize for the PR #912 review comments. The exact failing verb
was not extracted for this message (the scan reports notations, not lines). The orchestrator-side
pickup should read the matching `[ERROR]` entry in the plan work log.

## Candidate rule

To be decided at pickup once the line is read. If the failure came from adding fix tasks during
finalize (an invented flag, or a missing required field on `add`), it belongs with the known
argparse-rejection signatures. If it was a guarded refusal, it may not be lesson-worthy.
