envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:31Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=qgate 6-finalize c52062 (functional-review, round 1, accepted)

# weakened_test check reports removals in a file that only gained additions and comment edits

## Observation

Q-Gate finding c52062 (`weakened_test at NonValidatingJwtParserTest`): the file only gained additions plus two comment edits, but the check attributed 6 assertions as gone from the nested BoundsRejectionAccounting class. It was an attribution artifact. The operator accepted it.

## Candidate rule

Cross-check nested-class assertion attribution against the raw file diff. Do not report a removal when no assertion line was deleted.
