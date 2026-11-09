envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:33Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 672079 (code-review, round 1, accepted)

# Code review re-files the ClaimValueTest declared-deletion row

## Observation

Q-Gate finding 672079 (code review round, `weakened_test at ClaimValueTest`): the same 5 assertions removed with the declared dead method (deliverable 3) that the functional review reported as b3c230 and the operator had accepted.

## Candidate rule

Carry accepted weakened_test verdicts across reviewers at the same head, and let the check read the deletions the outline declares.
