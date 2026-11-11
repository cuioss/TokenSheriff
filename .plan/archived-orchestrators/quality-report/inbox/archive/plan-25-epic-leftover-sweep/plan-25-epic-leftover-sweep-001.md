envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:02Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize b8bb06 (functional-review, round 1, accepted)

# Functional review flags a criterion as unevidenced when local verification cannot run at all

## Observation

Q-Gate finding b8bb06 (`unevidenced_criterion at D16.C1`): the e2e teardown hang cause could not be named from logs and the local e2e run could not start, so the criterion "teardown completes locally" stayed unevidenced. The operator decided CI verifies it, with two green e2e runs on main owed after merge.

## Candidate rule

When a deliverable's success criterion names a local run the environment cannot execute (e2e profile, container stack), the outline should say up front that CI verifies it and record the post-merge check that is owed. Otherwise every review round re-files the same unevidenced finding and the operator has to waive it again.
