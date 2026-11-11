envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:05Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=qgate 6-finalize 82dd75 (functional-review, round 2, accepted)

# NonValidatingJwtParserTest attribution artifact re-filed at a later head

## Observation

Q-Gate finding 82dd75 (round 2): "6 assertions present at f85c5ab3 are gone from BoundsRejectionAccounting, while the file total is not reduced by the same amount". This is the same nested-class attribution artifact accepted in round 1 (c52062). It was re-filed at a wording-only head.

## Candidate rule

A row whose own evidence says the file total did not drop should not be filed at error severity, and an accepted verdict should survive an unchanged file.
