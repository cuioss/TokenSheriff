envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:36Z

component=plan-marshall:lsp-client
category=bug
created=2026-11-09
source_record=script-failure cluster plan-marshall:lsp-client:lsp_client (signal-scan, script_failure class)

# Script failure cluster: plan-marshall:lsp-client:lsp_client

## Observation

`manage-logging signal-scan` attributes at least one `[ERROR] ... script_failure` line in this plan's work log to `plan-marshall:lsp-client:lsp_client`. The run had 19 script_failure lines across 7 notations; per-notation causes were not extracted by this step.

## For the pickup

Read the plan's work log `[ERROR]` lines for this notation to see whether the failure was a caller argument error (invented flag or verb) or a defect in the script. File a lesson only if it recurs across plans.
