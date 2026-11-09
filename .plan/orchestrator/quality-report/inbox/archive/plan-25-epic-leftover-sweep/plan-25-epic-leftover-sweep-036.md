envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:07Z

component=plan-marshall:phase-5-execute
category=improvement
created=2026-11-09
source_record=qgate 5-execute ae2576 (scope_creep_check, accepted)

# Files created by review-driven fixes and test-class splits trip the scope-creep check

## Observation

Q-Gate finding ae2576: 11 files outside the declared scope after the review loop. They were the new sibling classes of the 400-line test-class split and the CodeRabbit-driven fixes (TokenBuilder, JwsAlgorithm, package-info, two tests). Each had already been reviewed in the functional and code review rounds.

## Candidate rule

Fix tasks created from review findings should register the files they touch with the deliverable they fix. A split that creates new classes should declare them up front (they can be named at outline time).
