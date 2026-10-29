envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:41:01Z

# PR body goes stale after loop-back fix tasks (false self-report)

**Source plan:** plan-01-jwks-loader-lifecycle (PR #830)
**Signal:** Q-Gate findings, 6-finalize functional-review (e7c80c, fa9ad2, both `false_self_report`)
**Suggested component:** plan-marshall:phase-6-finalize (create-pr / loop-back re-entry)

## What happened

The PR body was written at create-pr time. Later, loop-back fix tasks TASK-004 to TASK-009 changed the scope a lot. The functional review then flagged the body as a false self-report:

- e7c80c: "Known, deliberately unfixed" points (a) and (c) had been fixed by TASK-5, TASK-7 and TASK-9, but the body still said they were open.
- fa9ad2: the Changes and Summary sections left out the Quarkus `TokenValidatorProducer` disposer, the public `FailureReason` / `getFailureReason()`, the converter change, the terminal absent-issuer behaviour, and the retry-probe compose/README/IT changes. The squash merge uses this body as the commit message.

## Corrective rule (candidate)

After every loop-back that adds fix tasks to an open PR, regenerate the PR body (through the ci body verbs) before the reviews run again. Do not wait for the functional review to catch it. A loop-back re-entry step that re-derives Summary, Changes and Known-limitations from the current task set and diff would prevent this class of finding.
