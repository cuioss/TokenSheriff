envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:06:38Z

# Merge-queue landing gate waited out its full budget on a hung merge-group run, then main moved under the PR

**Signal source:** branch-cleanup queue-landing gate (work log 11:15:46Z, decision log 11:21:59Z and 11:40:30Z).
**Component guess:** plan-marshall:phase-6-finalize (branch-cleanup queue-landing gate) / plan-marshall:tools-integration-ci (pr merge-queue).

## What happened

- PR #920 was enqueued at 10:44Z. Merge-group run 44288298902 had its `e2e-tests / test` job stuck in post-checkout cleanup from 10:45Z, after the Maven step was cancelled. No failure was reported.
- The queue-landing gate kept polling until it timed out at 1803s of its 1800s budget (`settle: timeout`). GitHub then cancelled the run at 11:15:37Z and took PR #920 out of the queue without merging it.
- While the PR sat in the queue, PR #921 from another plan in the same epic (plan-11, d8aba512) landed on main. It conflicted with #920 in `doc/client/specification/test-strategy.adoc` and `ClientConfigurationTest.java`. The re-enqueue at 11:37Z found PR #920 CONFLICTING/DIRTY.
- Recovery cost about 1h50m: an operator question, a rebase, a force-push, and the head-dependent finalize steps (code-review, functional-review, pre-push gate, ci-verify, sonar, automatic-review) run again on the rebased head.

## Candidate rule

- The queue-landing gate should look at the merge-group run's job states while it waits. A job that stays in_progress past its normal duration, or sits in a post-step with no progress, means a dead runner. The gate should report that early instead of waiting out a fixed budget.
- When a queued PR is ejected, check mergeability against the current main before re-enqueuing. Sibling plans in the same epic can land in the meantime, especially when their footprints overlap (here `test-strategy.adoc` and the client configuration tests).
- Epic-level: two plans in one epic touching the same doc and test files (plan-10 and plan-11) should either be sequenced or have their overlap flagged when the plans are staged.
