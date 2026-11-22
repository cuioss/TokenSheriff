envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:50Z

# Candidate lesson: a relaxed probe assertion accepted the fixture's own connection cutoff as the outcome under test

**Signal:** automated review (CodeRabbit, remediated in-run)
**Record:** pr-comment finding a9db38 (PR #940, BackChannelHttpBodyPhaseTest.java:129, resolved `fixed` in be9a2550).

## What happened

To deal with the intermittent JDK 27 failure (see the merge-queue flake candidate), the probe's JDK 27 branch was relaxed to accept a premature-EOF `IOException` as well as `HttpTimeoutException`. CodeRabbit pointed out that the stalling fixture server bounded its own wait with a 30 second cutoff and then closed the connection. If the test thread paused for more than 30 s, the fixture's close would produce the same `IOException`. The relaxed assertion would accept it and report that the request timeout bounded the body read. The fix made the fixture hold the stalled body until the test releases it or teardown stops the thread, so that outcome can only come from the client.

## Why it may be worth carrying

Widening an assertion to accept a second outcome is only sound when nothing else in the test can produce that outcome. Self-terminating fixture timeouts are such a source. Check them every time a probe's accepted-outcome set grows.
