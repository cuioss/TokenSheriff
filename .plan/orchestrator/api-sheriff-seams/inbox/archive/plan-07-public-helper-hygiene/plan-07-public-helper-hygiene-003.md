envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:23Z

# Candidate lesson: adopting a shared helper dropped a guard the call site used to apply

**Source signal**: automated review (PR #908, CodeRabbit finding bd25ca on BearerTokenProducer.java:226, resolved `fixed`).

## What happened

When `BearerTokenProducer` adopted the new public `BearerCredentials` helper, the old post-selection check `bearerToken.trim().isEmpty()` was removed. With `token.header=Cookie` and `Cookie: Bearer="   "`, `findCookieValue` strips the quotes and returns three spaces. Its `isEmpty()` check does not reject that, so the blank token reached the validator instead of returning INVALID_REQUEST. The fix changed the check to `isBlank`. After the fix, both reviews kept an advisory that no test reaches the new `isBlank` branch (decision log 21:13:18Z, 21:15:04Z).

## Suggested rule (for orchestrator classification)

When a refactor replaces inline logic with a shared helper, list every guard the old call site applied and confirm that the helper or the new call site still applies each one. Add a test for the input the removed guard existed for, here a quoted all-blank cookie value.

## Evidence

- manage-findings pr-comment bd25ca resolution_detail.
- Decision log advisories at BearerTokenProducer.java:340 (no test reaches isBlank).
