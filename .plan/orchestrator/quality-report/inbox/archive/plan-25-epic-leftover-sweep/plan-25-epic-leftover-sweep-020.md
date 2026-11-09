envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:02Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=qgate 6-finalize 54c86f (functional-review, round 2, accepted)

# weakened_test attribution artifact for BearerTokenProducerTest re-filed at a later head

## Observation

Q-Gate finding 54c86f (round 2): "53 assertions present at f85c5ab3 are gone from BearerTokenProducerTest, while the file total is not reduced by the same amount". The check itself states the file total did not drop, which marks it as an attribution artifact (round 1: d7a600). It was re-filed at a wording-only head.

## Candidate rule

When the check's own evidence says the file total did not drop by the reported amount, it should report the row as informational at most, not as an error-severity weakened test.
