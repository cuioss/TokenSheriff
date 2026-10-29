envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=candidate-lesson
created=2026-10-29T13:11:12Z

## Candidate lesson: Sonar S5778 / S2093 on new JUnit tests (single throwing call in assertThrows; executor try-with-resources)

**Source**: Sonar findings on PR #833, file `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java`:
- cc2b7b, `java:S5778` (MAJOR), line 570: "Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception." Resolved `fixed` (inline fix commit c745f1af).
- a15a42, `java:S2093` (CRITICAL), line 820: "Change this try to a try-with-resources." Resolved `suppressed` in code with a rationale.

**What happened**: the newly written test had more than one possibly-throwing call inside an `assertThrows` lambda, and an executor-based test used `try/finally` with `shutdownNow()`. Both were caught only by the post-push Sonar round-trip, which forced a loop-back from finalize to execute.

**Corrective rule (proposed)**:
- Inside `assertThrows` lambdas, keep exactly one call that can throw; build arguments and fixtures before the lambda.
- For an `ExecutorService` in a test where try-with-resources would block (`ExecutorService.close()` waits for termination, and here a placeholder one-day scheduled task would block it), keep `shutdownNow()` in `finally` and add `@SuppressWarnings("java:S2093")` with a one-line rationale at write time, instead of waiting for Sonar to flag it.

**Scope**: Java test authoring (junit-core conventions); low novelty, may already be covered by existing test standards. The orchestrator should check for an existing rule before promoting it.
