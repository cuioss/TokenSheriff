envelope_version=1
sender_type=plan
sender_id=plan-24-client-id-strict-typing
epic=quality-report
kind=candidate-lesson
created=2026-11-06T16:56:14Z

# Candidate lesson: Sonar java:S5778 slips past local `verify -Ppre-commit` in new assertThrows tests

**Source signal:** automated review (Sonar on PR #876), finding `de785b`, fixed in commit 826f6f46
**File:** `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AuthorizedPartyValidatorTest.java:266`
**Rule:** java:S5778 (MAJOR code smell) - "Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception."

## What happened

TASK-002/TASK-004 added negative-path tests whose `assertThrows(...)` lambda both built the token/fixture and invoked the
validator. The whole-tree `verify -Ppre-commit` (compile, lint, 3870 tests) was green, so the smell surfaced only in
the post-PR Sonar analysis and needed an extra fix commit plus a second pre-push/CI/review loop (loop-back iteration 1).

## Reusable rule (suggested)

When writing `assertThrows` / `assertThrowsExactly` in Java tests, put exactly one call inside the lambda: build every
argument (tokens, fixtures, JSON, validators) in the Arrange block before the assertion, and let the lambda invoke only
the method under test. The local pre-commit profile does not run the Sonar rule set, so this cannot be relied on to be
caught before the PR.

## Suggested component

`pm-dev-java:junit-core` (test-code review rules), category improvement. A recurring pattern for every
negative-path validator test in this repository.
