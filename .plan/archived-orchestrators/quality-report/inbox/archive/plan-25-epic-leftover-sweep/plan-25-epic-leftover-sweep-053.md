envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:40Z

component=plan-marshall:plan-orchestrator
category=bug
created=2026-11-09
source_record=script-failure cluster plan-marshall:plan-orchestrator:orchestrator (signal-scan, script_failure class)

# Script failure cluster: plan-marshall:plan-orchestrator:orchestrator

## Observation

`manage-logging signal-scan` attributes at least one `[ERROR] ... script_failure` line in this plan's work log to `plan-marshall:plan-orchestrator:orchestrator`. The run had 19 script_failure lines across 7 notations; per-notation causes were not extracted by this step. The plan was orchestrated under epic quality-report, and its plan directory moved between the worktree and the main checkout.

## For the pickup

Read the plan's work log `[ERROR]` lines for this notation to see whether the failure was a caller argument error or a script defect. File a lesson only if it recurs across plans.
