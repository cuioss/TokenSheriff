envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:04Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 282b29 (functional-review, round 2, accepted)

# JweDecryptorTest split row re-filed at a later head

## Observation

Q-Gate finding 282b29 (round 2): "63 assertions present at f85c5ab3 are gone from the file". This is the same D20 pure-move split accepted in round 1 (6746bb). It was re-filed at a wording-only head.

## Candidate rule

Keep accepted weakened_test verdicts across heads when the file is unchanged since the accepted head.
