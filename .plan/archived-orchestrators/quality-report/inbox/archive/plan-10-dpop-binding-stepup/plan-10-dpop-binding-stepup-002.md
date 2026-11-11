envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:51:57Z

# Negative step-up max_age plus positive clock skew let StepUpResultVerifier accept a stale auth_time

**Source**: review-bot finding 060f53 (CodeRabbit, PR #856, inline at StepUpResultVerifierTest.java:57), resolution fixed in commit 310669e. Slipped-then-caught security defect.

## What happened

`StepUpChallengeParser.parseMaxAge` accepted a negative `max_age`, the challenge record did not validate it, and `StepUpResultVerifier.verifyAuthTime` applied the freshness window as `max_age + skew`. With `max_age=-10` and a 60-second skew, an `auth_time` 30 seconds old was accepted although the challenge asked for something stricter than "now". The fix rejects a negative `max_age` fail-closed before the freshness check, with a test that fails without the guard.

The plan introduced the injectable step-up clock and had several functional-review and code-review rounds plus a security-audit step; none of them flagged the sign of a server-supplied integer that is combined with a tolerance.

## Candidate rule

Any server-supplied numeric bound that is combined with a local tolerance (clock skew, grace window) must have its range validated (non-negative, upper cap) before the arithmetic, and the test suite must include the out-of-range value. Reviews of freshness/expiry code should explicitly ask "what does a negative or overflowing input do once the tolerance is added".

## Follow-up noted in-run

Decision log 3bf237: the step-up freshness spec (doc/client/specification/step-up-and-logout.adoc:59-68) and the `StepUpHandler.verifyResult` @throws still omit the negative-max_age rejection; deferred to a follow-up to avoid another full re-verification round.
