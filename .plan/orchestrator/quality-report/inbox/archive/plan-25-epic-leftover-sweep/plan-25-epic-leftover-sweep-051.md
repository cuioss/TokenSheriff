envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:39Z

component=plan-marshall:manage-status
category=bug
created=2026-11-09
source_record=script-failure cluster plan-marshall:manage-status:manage-status (signal-scan, script_failure class)

# Script failure cluster: plan-marshall:manage-status:manage-status

## Observation

`manage-logging signal-scan` attributes at least one `[ERROR] ... script_failure` line in this plan's work log to `plan-marshall:manage-status:manage-status`. The run had 19 script_failure lines across 7 notations; per-notation causes were not extracted by this step. This run re-ran finalize steps at several heads and re-dispatched the merge-queue enqueue, so step-state transitions (mark-step-done without --force, merge authorization bound to an older head) are a likely source.

## For the pickup

Read the plan's work log `[ERROR]` lines for this notation to confirm the cause. File a lesson only if it recurs across plans.
