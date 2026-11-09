envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:09Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 95d620 (code-review, round 2, accepted, reobserved_count 1)

# Code review re-files the JweDecryptorTest split row as a "code finding"

## Observation

Q-Gate finding 95d620 (code review round 2, `code finding at .../JweDecryptorTest.java`, reobserved_count 1): the same pure-move split, filed for the fourth time (6746bb, 1f03bb, 282b29, 95d620).

## Candidate rule

A reobserved match to an accepted finding on an unchanged file should inherit the verdict.
