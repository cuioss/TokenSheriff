envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:26Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize 72c9c3 (functional-review, round 1, accepted)

# A criterion that needs the PR to exist is reviewed as unevidenced before the PR exists

## Observation

Q-Gate finding 72c9c3 (`unevidenced_criterion at D26.C1`): the criterion required the Sonar PR-head issue count, which cannot exist before the PR is created. It was deferred to the finalize Sonar step and accepted by the operator.

## Candidate rule

Criteria whose evidence only exists after PR creation (Sonar PR-head counts, PR-body records) should be tagged as post-PR in the outline, and the functional review should not file them as unevidenced at a pre-PR head.
