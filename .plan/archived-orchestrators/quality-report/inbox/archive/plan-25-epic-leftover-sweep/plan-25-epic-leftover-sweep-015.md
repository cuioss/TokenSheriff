envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:35Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 182d71 (code-review, round 1, accepted)

# Code review re-files the HttpJwksLoaderFailureTest split row

## Observation

Q-Gate finding 182d71 (code review round, `weakened_test at HttpJwksLoaderFailureTest`): the same 53-assertion pure-move split that the functional review reported as d049eb and the operator had accepted.

## Candidate rule

Carry accepted weakened_test verdicts across reviewers at the same head.
