envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:28Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=qgate 6-finalize d7a600 (functional-review, round 1, accepted)

# weakened_test check reports removed assertions in a file that only gained lines

## Observation

Q-Gate finding d7a600 (`weakened_test at BearerTokenProducerTest`): git shows 92 insertions and 0 deletions for the file, yet the check reported assertions as gone. It was an attribution artifact (assertions counted per nested class or method, not per file). The operator accepted it.

## Candidate rule

If the file-level diff has no deletions, the weakened-test check should not report a removal. Attribution of assertions to inner classes or methods needs a cross-check against the raw diff.
