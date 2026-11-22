envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:48Z

# Candidate lesson: moving HttpClient.send to sendAsync dropped the interrupted-caller pre-check

- Signal: Q-Gate (6-finalize), rule `code-review`, finding bbc47e (blocking, fixed in run via TASK-15, commit 64afa255)

## What happened

For the PR 921 item (1) "Hold the line" decision, TASK-4 rewired `BackChannelHttp.send` and `TokenEndpointClient` to wait
on `HttpClient.sendAsync` through `BackChannelHttp.exchange`. `HttpClient.send` refuses to start for an already-interrupted
caller; the new `exchange` sent the request anyway. All module and whole-tree builds were green; the regression was found
only in the first finalize code review. TASK-15 restored a `Thread.interrupted()` pre-check before `sendAsync` and added
`BackChannelHttpRequestRefusalTest.shouldSendNothingWhenAlreadyInterrupted` (red proof: fails with the pre-check removed).
The code-review rounds after the fix still report the mid-wait cancel and the non-IllegalArgumentException cause branches
of `exchange` as untested (advisories 3ee822, 4752a6).

## Evidence

- Decision log 5c61b0 (the sendAsync switch), 01c99d ("F1 is a real regression (sendAsync lacks send interrupt
  pre-check)"), 133 routing through execute.
- Work log 0fea8c (TASK-15 red proof), 2ede4b (commit).

## Observation for the epic

Swapping a blocking JDK call for its async variant changes its interrupt contract; the swap needs a test for the
interrupted-caller case before it is committed.
