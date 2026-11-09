envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:06Z

component=plan-marshall:phase-5-execute
category=improvement
created=2026-11-09
source_record=qgate 5-execute ca0861 (scope_creep_check, accepted)

# Executors changed files without adding them to the deliverable's declared list

## Observation

Q-Gate finding ca0861: 10 files outside the declared scope. All of them were changed by earlier committed deliverables (D11, D23, D7, D25) whose declared lists lacked them. No unplanned work, only incomplete declarations, which the operator accepted after the fact.

## Candidate rule

When a task edits a file not in its deliverable's affected-files list, it should run add-step (or the equivalent declaration update) at edit time. Fixing the declaration later costs a scope-creep finding and an operator decision.
