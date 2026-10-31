envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:40:47Z

component=token-sheriff-quarkus-integration-tests
category=bug
source_plan=plan-04-quarkus-startup-health
source_records=254bb6, script-failure cluster "stale ~/.m2 native build"

# Native integration test runs against stale ~/.m2 extension jars and reports false failures

## What happened

The native integration-test path (`build-native-if-needed.sh`) builds the integration-test runner
without `-am` and reuses an already existing runner. Neither path rebuilds the Quarkus extension
modules changed on the branch, so the test silently ran against extension jars that were stale in
`~/.m2`. During plan-04 this produced a false failure in
`ProblemDetailMappingIT.shouldMapUnhandledTokenValidationExceptionToProblemJson` (finding 254bb6),
which was first read as evidence for the "exception mappers are inactive at runtime" hypothesis.
After the local repository was refreshed, the same test passed with the registration in place. The
hypothesis was refuted as a cause.

## Rule

- Before you read a native or integration-test failure in `token-sheriff-quarkus-integration-tests`
  as a code defect, make sure the extension modules changed on the branch were installed first. Use
  a reactor build with `-am`, or `install` the changed extension modules.
- `build-native-if-needed.sh` should either build with `-am` or invalidate the cached runner when an
  upstream extension module changed. It must not reuse a runner built against older jars.

## Impact

A stale-artifact failure looks exactly like a real regression. It can send a run chasing a wrong
hypothesis and spend a full fix-task cycle (TASK-13 in plan-04) on a problem the code does not have.
