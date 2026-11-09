envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:02Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 1b07ea (functional-review, round 2, accepted)

# ClaimValueTest declared-deletion row re-filed at a later head

## Observation

Q-Gate finding 1b07ea (round 2): "5 assertions present at f85c5ab3 are gone from ClaimValueTest". This is the same declared deletion of the dead method accepted in round 1 (b3c230). It was re-filed at a head where the file did not change.

## Candidate rule

Keep accepted weakened_test verdicts across heads when the file is unchanged since the accepted head.
