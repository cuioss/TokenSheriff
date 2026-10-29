envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:53Z

# Lifecycle close() races surface one narrow window per review round and exhaust the loop-back ceiling

**Source plan:** plan-01-jwks-loader-lifecycle (PR #830)
**Signal:** Q-Gate findings, 6-finalize code-review (rule `code-review`)
**Suggested component:** project-local `token-sheriff-validation` (HttpJwksLoader lifecycle) and plan-marshall:phase-6-finalize (loop-back ceiling)

## What happened

The code review reached its loop-back ceiling of 3 rounds. Each round found one more small window in which `HttpJwksLoader.close()` races with an in-flight initialization or refresh:

- fe43c1: reserving the init future and clearing `closed` were two non-atomic steps, so a concurrent `close()` was silently undone.
- 7ddc2c: the init future of a generation superseded by `close()` during the initial fetch completed ERROR (refresh disabled) instead of the documented UNDEFINED.
- 983533: the same wrong outcome in the window between `adoptResolution` and `publishStatus` in `handleAdapterResolutionFailure`.
- f239e2: the Javadoc promised re-initialization after close, but an owned, shut-down executor makes re-init fail.

TASK-007 eventually tied refresh cycles and endpoint resolution to a lifecycle epoch, which closed the family.

## Corrective rule (candidate)

For a component with close/re-init semantics and asynchronous work (init future, scheduled refresh, endpoint resolution), design generation or epoch binding up front. Every async completion must check that it belongs to the current lifecycle generation before it publishes status or state. Then write a test for each publish point that closes the component inside that window. Fixing races one at a time as reviewers find them costs a full review round per window. The ceiling was hit by successive narrow findings from the same root cause, not by one hard defect.

Process side for the epic: when two consecutive loop-backs cite the same root cause (here: close() vs in-flight async publish), the fix task should cover the whole class, not just the cited line.
