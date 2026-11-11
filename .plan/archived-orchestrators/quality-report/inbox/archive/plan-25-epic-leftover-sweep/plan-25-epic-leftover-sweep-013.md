envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:34Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 3ee61c (code-review, round 1, accepted)

# Code review re-files the DpopProofValidatorTest split row

## Observation

Q-Gate finding 3ee61c (code review round, `weakened_test at DpopProofValidatorTest`): the same 43-assertion pure-move split that the functional review reported as 861831 and the operator had accepted.

## Candidate rule

Carry accepted weakened_test verdicts across reviewers at the same head.
