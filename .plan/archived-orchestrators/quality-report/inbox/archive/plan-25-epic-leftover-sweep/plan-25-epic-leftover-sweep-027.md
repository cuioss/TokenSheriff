envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:06Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize e4b775 (code-review, round 2, accepted)

# Code review re-files the BearerTokenProducerTest attribution artifact as a "code finding"

## Observation

Q-Gate finding e4b775 (code review round 2, `code finding at .../BearerTokenProducerTest.java`): the same attribution artifact, filed for the fourth time (d7a600, 48bb71, 54c86f, e4b775) at a head where the file was unchanged.

## Candidate rule

Apply accepted verdicts automatically to unchanged files at later heads, across both reviewers.
