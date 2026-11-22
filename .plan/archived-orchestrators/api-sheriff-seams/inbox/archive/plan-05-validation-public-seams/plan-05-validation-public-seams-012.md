envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:36Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=pr-comment d9b039 (CodeRabbit, PR #899, resolution fixed by TASK-16 19b3b40a); same defect as qgate 63639f

# Review bot re-raised the replay-guard clock pairing after code review had closed it

## What happened

CodeRabbit (Major, Security) raised that `LogoutTokenValidator` takes `replayGuard` and `clock` as separate builder
arguments, so mismatched clocks can purge a live `jti` and allow a replay. Internal code review had found the same defect
(63639f) but closed it as taken into account, protected only by Javadoc. The operator split it into TASK-16, where the
builder constructs the replay guard from its own clock and the public guard constructor no longer takes a clock.

## Proposed rule

A security-relevant code-review finding should not close as "taken into account" when the only protection is
documentation. Treat a Javadoc-only invariant on a public builder as an open defect.
