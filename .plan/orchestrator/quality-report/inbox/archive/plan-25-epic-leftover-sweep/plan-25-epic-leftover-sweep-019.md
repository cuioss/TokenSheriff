envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:01Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize a6b0d6 (functional-review, round 2, accepted)

# weakened_test row for IssuerConfigResolverTest re-filed at a later head

## Observation

Q-Gate finding a6b0d6 (round 2, `weakened_test at .../IssuerConfigResolverTest.java`): "48 assertions present at f85c5ab3 are gone from the file". This is the same D20 pure-move split accepted in round 1 (bb821c). The new head only carried wording changes, none in this file, yet the row was filed again under a new hash because the title now carries the full path.

## Candidate rule

A finding identity that changes with the title format (short class name vs full path) defeats dedup across rounds. Key weakened_test findings on file path plus the baseline commit, and keep an accepted verdict when the file did not change since.
