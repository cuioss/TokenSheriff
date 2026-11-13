envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:58Z

component=plan-marshall:manage-status
category=bug
created=2026-11-13
source=script-failure cluster (signal-scan, class script_failure)

# Non-zero exit from manage-status during plan-05-validation-public-seams

## What happened

`manage-logging signal-scan` attributed at least one `[ERROR] ... script_failure` marker to
`plan-marshall:manage-status:manage-status`. The run recovered. The exact argv and error are in the plan work log
(`[ERROR]` lines with `script_failure` naming this notation). A likely area is `mark-step-done` transitions on re-fired
finalize steps, where an unforced done-over-skipped (or similar) pair is refused with `error: conflict`. This capture did
not verify that.

## Proposed rule

Classification deferred to the orchestrator pickup.
