envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:33Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 48bb71 (code-review, round 1, accepted)

# Code review re-files the BearerTokenProducerTest attribution artifact

## Observation

Q-Gate finding 48bb71 (code review round, `weakened_test at BearerTokenProducerTest`): the same attribution artifact (92 insertions, 0 deletions) the functional review reported as d7a600 and the operator had accepted at the same head.

## Candidate rule

Carry an accepted weakened_test verdict from the functional review to the code review at the same head.
