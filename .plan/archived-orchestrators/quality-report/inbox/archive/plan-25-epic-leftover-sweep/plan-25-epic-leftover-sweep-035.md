envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:06Z

component=plan-marshall:phase-5-execute
category=bug
created=2026-11-09
source_record=qgate 5-execute a16419 (scope_creep_check, accepted)

# Scope-creep check re-reports files an earlier accepted finding already covered

## Observation

Q-Gate finding a16419: 17 files outside the declared scope. 16 of them were the same earlier-deliverable files already accepted in ca0861, plus the D20 splits; one was doc/client/architecture.adoc, added to D17 via add-step on the orchestrator's instruction. The accepted residual set was not subtracted, so the operator reviewed the same files again.

## Candidate rule

The scope-creep check should subtract files covered by an earlier accepted or taken-into-account scope finding from its residual set. A file added through add-step should count as declared from then on.
