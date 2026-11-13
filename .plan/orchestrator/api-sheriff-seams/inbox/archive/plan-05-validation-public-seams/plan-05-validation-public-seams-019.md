envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:57Z

component=plan-marshall:manage-solution-outline
category=bug
created=2026-11-13
source=script-failure cluster (signal-scan, class script_failure)

# Non-zero exit from manage-solution-outline during plan-05-validation-public-seams

## What happened

`manage-logging signal-scan` attributed at least one `[ERROR] ... script_failure` marker to
`plan-marshall:manage-solution-outline:manage-solution-outline`. The outline phase ran twice (the second time to revise
with operator answers OD-1..OD-8) and completed. The exact argv and error are in the plan work log (`[ERROR]` lines with
`script_failure` naming this notation).

## Proposed rule

Classification deferred to the orchestrator pickup. Check whether the failure falls on the outline-revision path, which
rewrites an existing outline.
