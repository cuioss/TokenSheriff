envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:36Z

component=plan-marshall:manage-architecture
category=bug
created=2026-11-09
source_record=script-failure cluster plan-marshall:manage-architecture:architecture (signal-scan, script_failure class)

# Script failure cluster: plan-marshall:manage-architecture:architecture

## Observation

`manage-logging signal-scan` attributes at least one `[ERROR] ... script_failure` line in this plan's work log to `plan-marshall:manage-architecture:architecture`. The run had 19 script_failure lines across 7 notations; per-notation causes were not extracted by this step.

## For the pickup

Read the plan's work log `[ERROR]` lines for this notation to see whether the failure was a caller argument error or a script defect. File a lesson only if it recurs across plans.
