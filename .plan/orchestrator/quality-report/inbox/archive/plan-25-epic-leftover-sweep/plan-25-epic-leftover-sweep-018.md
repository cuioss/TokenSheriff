envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:00Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize a46d0c (functional-review, round 2, accepted)

# The finalize gates cannot evidence their own results before they run

## Observation

Q-Gate finding a46d0c (`unevidenced_criterion at D26.C1`, round 2): the criterion "all three gates green and recorded in the PR body" was unevidenced because verify had not been re-run after two fix commits and the Sonar PR-head count is produced by the finalize gates themselves. The operator accepted it.

## Candidate rule

A deliverable whose criterion is "the finalize gates pass" is circular when the functional review runs inside those gates. Such criteria should be marked as evidenced by the finalize step results, not reviewed as part of the deliverable.
