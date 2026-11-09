envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:36Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize c57c02 (code-review, round 1, accepted)

# Code review re-files the NonValidatingJwtParserTest attribution artifact

## Observation

Q-Gate finding c57c02 (code review round, `weakened_test at NonValidatingJwtParserTest`): the same attribution artifact (only additions plus two comment edits) that the functional review reported as c52062 and the operator had accepted.

## Candidate rule

Carry accepted weakened_test verdicts across reviewers at the same head.
