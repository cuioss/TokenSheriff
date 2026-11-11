envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:11Z

component=plan-marshall:phase-3-outline
category=bug
created=2026-11-09
source_record=qgate 3-outline e264d9 (request alignment, fixed)

# Verification criterion pointed at a deliverable that does not run the cited check

## Observation

Q-Gate finding e264d9: deliverable 9's criterion read "Javadoc lint clean under verify -Ppre-commit in D18", but D18 only runs `package -pl ... -am`. The whole-tree `verify -Ppre-commit` runs in D26. Fixed: TASK-30 and TASK-31 now cite D26.

## Candidate rule

When a criterion defers its evidence to another deliverable, check that the other deliverable's verification command actually runs the check named.
