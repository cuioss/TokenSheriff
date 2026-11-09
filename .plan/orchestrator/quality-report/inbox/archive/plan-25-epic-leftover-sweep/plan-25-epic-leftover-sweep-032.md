envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:10Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 22b706 (code-review, round 2, accepted)

# Code review re-files the NonValidatingJwtParserTest attribution artifact as a "code finding"

## Observation

Q-Gate finding 22b706 (code review round 2, `code finding at .../NonValidatingJwtParserTest.java`): the same nested-class attribution artifact, filed for the fourth time (c52062, c57c02, 82dd75, 22b706).

## Candidate rule

Apply accepted verdicts automatically to unchanged files at later heads, across both reviewers.
