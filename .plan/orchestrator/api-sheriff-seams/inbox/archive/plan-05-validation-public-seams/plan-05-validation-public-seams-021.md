envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:59Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-13
source=script-failure cluster (signal-scan, class script_failure, notation plan-marshall:phase-6-finalize:verdict_currency)

# Non-zero exit from the verdict_currency classifier during finalize

## What happened

`manage-logging signal-scan` attributed at least one `[ERROR] ... script_failure` marker to
`plan-marshall:phase-6-finalize:verdict_currency`. In the same run the classifier re-fired functional-review and code-review
on every HEAD advance (see the separate candidate on undeclared verdict_inputs). The exact argv and error are in the plan
work log.

## Proposed rule

Classification deferred to the orchestrator pickup. Pick it up together with the verdict-currency re-fire candidate,
because both concern the same script.
