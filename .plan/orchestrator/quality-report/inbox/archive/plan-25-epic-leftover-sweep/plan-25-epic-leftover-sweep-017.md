envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:00Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize ed7018 (functional-review, round 2, accepted)

# A wording-only fix commit makes the functional review re-file a criterion the operator already waived

## Observation

Q-Gate finding ed7018 (`unevidenced_criterion at D16.C1`, round 2): the same e2e teardown criterion that round 1 reported as b8bb06 and the operator waived. It was re-filed at a new head that only carried comment and wording changes, and the operator decision had to be applied again.

## Candidate rule

A waiver of an unevidenced criterion should survive a new head when the delta since the waived head does not touch the criterion's evidence files. This needs the review step to declare which inputs its verdict depends on.
