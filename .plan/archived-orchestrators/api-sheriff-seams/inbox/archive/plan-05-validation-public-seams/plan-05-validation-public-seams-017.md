envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:56Z

component=plan-marshall:manage-execution-manifest
category=bug
created=2026-11-13
source=script-failure cluster (signal-scan, class script_failure)

# Non-zero exit from manage-execution-manifest during plan-05-validation-public-seams

## What happened

`manage-logging signal-scan` attributed at least one `[ERROR] ... script_failure` marker to
`plan-marshall:manage-execution-manifest:manage-execution-manifest`. The run recovered, and the step that made the call
went on to complete. The exact argv and error are in the plan work log (`[ERROR]` lines with `script_failure` naming this
notation). This capture did not determine whether the cause was a caller-side argument error or a script defect.

## Proposed rule

Classification deferred to the orchestrator pickup. If this notation also appears in other plans' script-failure
clusters, check the workflow text that builds the call against the script's canonical-invocation block.
