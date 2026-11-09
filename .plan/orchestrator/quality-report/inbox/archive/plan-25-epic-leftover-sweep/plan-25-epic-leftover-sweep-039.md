envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:09Z

component=plan-marshall:phase-3-outline
category=bug
created=2026-11-09
source_record=qgate 3-outline 2bda2a (architecture, fixed)

# module_testing profile declared on a deliverable with no test file

## Observation

Q-Gate finding 2bda2a: deliverable 12 changed only comments and descriptor text (production_only, no behaviour change) and named no test file, yet listed module_testing. Phase 4 would have derived a test task with no target. Fixed: D12 became a single implementation task.

## Candidate rule

Only declare module_testing when the affected files include a test file. For comment-only or descriptor-only changes, the build in Verification is the proof.
