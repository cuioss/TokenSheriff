envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:27Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize bb821c (functional-review, round 1, accepted)

# weakened_test check flags a pure-move test-class split as a weakened test

## Observation

Q-Gate finding bb821c (`weakened_test at IssuerConfigResolverTest`): 48 assertions were moved to the new sibling class IssuerConfigResolverDpopTransportAndLoggingTest by the D20 split (400-line test-class rule). Test-method names and counts were unchanged. The operator accepted it.

## Candidate rule

The weakened-test check should count assertions across the whole plan diff, including sibling test classes created in the same change, before reporting a removal. A split that keeps total assertions should not become a finding.
