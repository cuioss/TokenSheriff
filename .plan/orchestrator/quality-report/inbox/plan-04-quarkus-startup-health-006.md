envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:37Z

component=token-sheriff-quarkus-integration-tests
category=improvement
source_plan=plan-04-quarkus-startup-health
source_records=016c7f, fd901a (pr-comment review bodies, fixed via TASK-14 and TASK-15)

# New tests asserted weaker properties than they were written to protect

## What happened

The tests written in plan-04 passed but would not have caught the regressions they were meant to
guard. CodeRabbit reported four gaps:

- `ProblemDetailMappingIT` checked the problem `type` with `endsWith("token-invalid")`, which also
  accepts a wrong namespace. It should pin the full published URI.
- `ProblemDetailMappingIT` checked for leaks only against the exception-message suffix, which does
  not contain the submitted token. A response that echoed the token would still pass. It should
  assert that the body does not contain `MALFORMED_TOKEN`.
- `TokenSheriffClientProcessorTest` called `registerClientExceptionMapper()` directly, so removing
  `@BuildStep` would not be detected. It should assert that the annotation is present.
- `TokenValidatorHealthCheckTest`'s `FailingResolver` threw from the first resolver call, so the
  failure path of the second call (`outcome()`) was never exercised.

## Rule

- A leak/negative assertion must test the actual sensitive input (the submitted token, the secret),
  not a derived string that happens not to contain it.
- Pin published contract values (problem-type URIs) with `equalTo`, not with suffix or prefix
  matchers.
- When a test calls a Quarkus build-step method directly, also assert that its `@BuildStep`
  annotation is present.
- When code guards several calls with one try/catch, write one failure test per call.

## Impact

These four gaps took two review rounds and two fix tasks. Each round restarted the gate/CI/review
cycle (see the separate review-cycle candidate).
