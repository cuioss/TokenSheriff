envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:37Z

# Candidate lesson: a close() Javadoc promised idempotence the fixture did not implement

**Signal:** automated review (CodeRabbit, remediated in-run)
**Record:** pr-comment finding 1900d6 (PR #940, RawHttpServer.java:162, resolved `fixed` in 38a6e041).

## What happened

The new `RawHttpServer.close()` Javadoc said a repeated call has no effect. CodeRabbit showed that with the injected `FailingServerSocket` a second call runs the failing close again and throws again, which breaks the stated contract. The fix changed the Javadoc, not the code. It now says a repeated call repeats the release steps, which is a no-op once everything is released, but a close that throws on every call throws again. No closed-flag guard was added, because a guard would also have to keep retries possible after a partial failure.

This was the first review loop-back of the run (work log 09:20:40, loop-back iteration 1/5). It needed a new head, so the gate, CI and both reviews all ran again.

## Why it may be worth carrying

On a fixture with fault injection, contract words like "no effect", "every step runs" and "idempotent" get checked against the injected failure paths. Checking the Javadoc against the failing-socket test before pushing avoids a review round.
