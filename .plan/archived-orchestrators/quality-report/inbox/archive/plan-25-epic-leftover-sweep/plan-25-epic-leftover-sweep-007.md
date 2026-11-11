envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:30Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 6746bb (functional-review, round 1, accepted)

# weakened_test check flags the JweDecryptorTest pure-move split

## Observation

Q-Gate finding 6746bb (`weakened_test at JweDecryptorTest`): 63 assertions were moved to JweDecryptorEcdhEsAndCompressionTest by the D20 pure-move split. The operator accepted it.

## Candidate rule

Follow assertions moved to sibling classes in the same diff before reporting a weakened test.
