envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:44Z

# Candidate lesson: a deferred one-shot retry ran without re-checking that it was still wanted

Source signal: review-bot finding e00ddf (coderabbit, PR #906, inline on HttpJwksLoader.java:1308), resolution fixed by TASK-12 with tests in TASK-13 (commit 6f3e8e2a).

## What happened

`scheduleInitialLoadRetry` schedules a one-shot retry that is not stored. `reResolveEndpoint` cancels only `refreshTask`, so a terminal ERROR does not cancel a pending retry. `runInitialLoadRetry` then called `performBackgroundRefresh` without checking status or installed keys. A pending retry could therefore repeat endpoint discovery after a terminal rejection, or fetch the JWKS again after a periodic cycle had already installed keys. The method's Javadoc claimed the cycle honours terminal rejections. The fix re-checks eligibility (live generation, no installed keys, status not ERROR) under the lifecycle lock before the cycle starts.

## Candidate rule

Any scheduled task that is not tracked for cancellation must re-check its preconditions under the owning lock when it fires: lifecycle generation, terminal state, and whether its goal was already reached. Test each case where the precondition changes between scheduling and firing.

Component hint: token-sheriff-validation HttpJwksLoader (scheduler / lifecycle).
