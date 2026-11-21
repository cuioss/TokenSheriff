envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:10Z

# Candidate lesson: an intermittent per-JDK probe passed on rerun in PR CI but ejected the PR from the merge queue

**Signal:** automated review / branch-cleanup (merge-queue outcome that led to the later review rounds)
**Records:** work log d0fd8c (2026-11-21T10:05:46Z): `PR #940 enqueued but not merged after 391s ... merge_group run 44892104059 completed with failure — build (27) BackChannelHttpBodyPhaseTest.shouldObserveWhetherRequestTimeoutBoundsBodyRead, the same JDK 27 probe that failed the first CI attempt at ed4296fd and at 38a6e041 and passed on a rerun each time ... Branch F2 reached; the operator is asked how to proceed`; decision log 10d919 (operator chose "Fix the probe first"); final merge a433e990 (decision 34aeb2, merged after 460s).

## What happened

The JDK 27 leg of the build matrix sometimes failed `BackChannelHttpBodyPhaseTest` with a premature-EOF `IOException` instead of `HttpTimeoutException`. In PR CI it failed on two commits and passed on rerun each time, so ci-verify finished `done` and nothing stopped the flow. The merge queue's `merge_group` run hit the same flake and dequeued the PR. That reached Branch F2 and cost an operator decision. The decision led to three more commits (e959d789, 641b4511, be9a2550), two review rounds, two more gate runs, and a frozen-criterion change (see candidate 18).

## Why it may be worth carrying

A test that failed and then passed on rerun in PR CI is a known flake. It should be raised at ci-verify time, not left for the merge queue, which does not rerun and so is where it costs a decision. Recording "failed then passed on rerun" in ci-verify as a finding would bring the decision forward to a point where it is cheaper.
