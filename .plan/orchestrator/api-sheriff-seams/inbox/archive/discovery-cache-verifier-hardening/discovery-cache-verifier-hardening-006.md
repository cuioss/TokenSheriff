envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:14Z

# Candidate lesson: a test-only hook placed before the claim's try/finally can leave a single-flight claim stuck

Source: pr-comment finding 25dd20 (PR #912, coderabbitai, CachingDiscoveryResolver.java:288), resolution fixed (TASK-8, regression test TASK-10).

## Observation

In `CachingDiscoveryResolver`, the `afterClaim` test hook and the post-claim state re-read ran after
the refresh claim was put into `inFlight` but before `refreshAsWinner` entered its try/finally. If the
hook threw (an `AssertionError` or a runtime exception), the claim stayed in `inFlight` and every
later caller waited in `join()` with no end.

## Fix

The hook and the state re-read moved inside the claimed region, so every exit completes the claim
with the abort marker (`REFRESH_ABORTED`) and clears `inFlight`.

## Candidate rule

In single-flight or claim code, the cleanup scope must start at the moment the claim is published.
Any code between publishing the claim and entering try/finally, including test seams, is a path that
can strand joiners.
