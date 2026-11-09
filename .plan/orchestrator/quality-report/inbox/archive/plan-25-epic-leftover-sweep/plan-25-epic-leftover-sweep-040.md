envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:10Z

component=plan-marshall:phase-3-outline
category=bug
created=2026-11-09
source_record=qgate 3-outline b20343 (request alignment, fixed)

# Verification command did not run the profile that contains the code being fixed

## Observation

Q-Gate finding b20343: deliverable 16 fixed the e2e teardown (e2e-tests profile, stop-dev-environment.sh), but its Verification command was `verify -pl token-sheriff-quarkus-parent/e-2-e-playwright -am` without `-Pe2e-tests`, so the teardown path was never run. It also listed module_testing with no test file. Fixed: TASK-39 verifies with -Pe2e-tests.

## Candidate rule

A deliverable's verification command must run the profile or job that contains the changed code path. Copy the CI job's own invocation (here `verify -Pe2e-tests -pl ...`) instead of writing a generic module build.
