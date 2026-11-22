envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:52Z

# Candidate lesson: parameterized test hard-listed the failure members of an enum

Source signal: review-bot finding a76cf9 (coderabbit, PR #906, inline on HttpJwksLoaderUnknownKidAttackTest.java:175), resolution fixed by TASK-13 (commit 6f3e8e2a).

## What happened

`shouldIgnoreFailingResponse` used `@EnumSource(names = {"SERVER_ERROR", "SLOW", "OVERSIZED", "MALFORMED", "EMPTY_KEY_SET"})`, which repeats every failure member of `UnknownKidJwksDispatcher.Mode`. A new failure mode added to the dispatcher would have been left out of the attack test without any warning. The fix uses `mode = EnumSource.Mode.EXCLUDE` with only the two successful modes (`DEFAULT_KEY`, `ROTATED_KEY`).

## Candidate rule

When a parameterized test is meant to cover "every failure case" of an enum, exclude the known-good members instead of listing the bad ones. New members are then covered by default.

Component hint: token-sheriff-validation tests (JUnit 5 @EnumSource).
