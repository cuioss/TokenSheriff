envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:28Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize b3c230 (functional-review, round 1, accepted)

# weakened_test check flags assertions removed together with a declared dead-code deletion

## Observation

Q-Gate finding b3c230 (`weakened_test at ClaimValueTest`): 5 assertions were removed along with the dead method ClaimValue.isNotPresentForClaimValueType. The outline declared that deletion in deliverable 3. The operator accepted it.

## Candidate rule

The weakened-test check should read the deletions the outline declares, and treat assertions that only exercised a declared-deleted production member as expected removals rather than findings.
