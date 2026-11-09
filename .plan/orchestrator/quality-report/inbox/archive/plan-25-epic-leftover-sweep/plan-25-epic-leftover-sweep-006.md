envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:29Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 861831 (functional-review, round 1, accepted)

# weakened_test check flags a pure-move split into a sibling class and a support class

## Observation

Q-Gate finding 861831 (`weakened_test at DpopProofValidatorTest`): 43 assertions were moved to DpopProofValidatorAlgorithmAndClaimTest and the shared DpopProofValidatorSupport by the D20 pure-move split. The operator accepted it.

## Candidate rule

Same as the other split rows: count assertions across the files created by the same change, including non-Test support classes, before reporting a weakened test.
