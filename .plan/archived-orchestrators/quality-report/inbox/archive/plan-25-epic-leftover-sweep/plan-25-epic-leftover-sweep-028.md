envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:07Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 998488 (code-review, round 2, accepted)

# Code review re-files the ClaimValueTest declared-deletion row as a "code finding"

## Observation

Q-Gate finding 998488 (code review round 2, `code finding at .../ClaimValueTest.java`): the same declared deletion, filed for the fourth time (b3c230, 672079, 1b07ea, 998488).

## Candidate rule

Apply accepted verdicts automatically to unchanged files at later heads, across both reviewers.
