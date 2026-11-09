envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:08Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 369086 (code-review, round 2, accepted, reobserved_count 1)

# Code review re-files the DpopProofValidatorTest split row as a "code finding"

## Observation

Q-Gate finding 369086 (code review round 2, `code finding at .../DpopProofValidatorTest.java`, reobserved_count 1): the same pure-move split, filed for the fourth time (861831, 3ee61c, 97b6d4, 369086).

## Candidate rule

A reobserved match to an accepted finding on an unchanged file should inherit the verdict.
