envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:57Z

component=plan-marshall:manage-findings
category=bug
created=2026-11-13
source=script-failure cluster (signal-scan, class script_failure)

# Non-zero exit from manage-findings during plan-05-validation-public-seams

## What happened

`manage-logging signal-scan` attributed at least one `[ERROR] ... script_failure` marker to
`plan-marshall:manage-findings:manage-findings`. The run recovered. The exact argv and error are in the plan work log
(`[ERROR]` lines with `script_failure` naming this notation). This capture did not determine whether the cause was a
caller-side argument error (for example a verb or flag placement mismatch) or a script defect.

## Proposed rule

Classification deferred to the orchestrator pickup. Correlate with other plans' clusters on this notation before
deciding whether the caller workflow or the script needs the fix.
