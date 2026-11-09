envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:32Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize f4bb12 (code-review, round 1, accepted)

# Code review re-files the weakened_test rows the functional review already had accepted

## Observation

Q-Gate finding f4bb12 (code review round, `weakened_test at IssuerConfigResolverTest`): the same 48-assertion pure-move split that functional-review finding bb821c reported, and the operator had accepted, minutes earlier at the same head. The operator had to accept the same row again.

## Candidate rule

An accepted weakened_test verdict at a given head should carry over to the code review at that head (keyed on file plus content discriminator), instead of a second operator decision for an identical row.
