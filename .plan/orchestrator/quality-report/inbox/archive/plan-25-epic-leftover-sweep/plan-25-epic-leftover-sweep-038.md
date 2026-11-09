envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:09Z

component=plan-marshall:phase-3-outline
category=bug
created=2026-11-09
source_record=qgate 3-outline 40fa45 (architecture, fixed)

# A verification deliverable was given the implementation profile

## Observation

Q-Gate finding 40fa45: deliverable 26 was change_type verification and wrote no file, yet its Profiles block listed implementation (bucket documentation_only). Phase 4 would have derived an implementation task with no write target. Fixed: TASK-40 carries the verification profile.

## Candidate rule

A deliverable with change_type verification takes the verification profile and no write bucket. The outline author should check the profile against change_type before the Q-Gate does.
