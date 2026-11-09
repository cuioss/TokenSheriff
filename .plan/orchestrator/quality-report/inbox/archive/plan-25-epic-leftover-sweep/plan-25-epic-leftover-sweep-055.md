envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:41Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=run event (dispatcher-reported): merge-queue ejection on e2e flake, loop-back re-dispatch cancelled the queued run

# Re-dispatching the enqueue while the PR is queued cancels the in-flight merge-queue run

## Observation

The merge queue ejected PR #883 on a flaky e2e Playwright screenshot comparison. Later, a loop-back re-dispatch of the enqueue step while the PR was still queued cancelled the merge-queue run that was in progress.

## Candidate rule

The enqueue step must check whether the PR is already in the merge queue and, if so, wait on the existing entry instead of enqueueing again. Separately, the e2e screenshot tests need a flake policy (retry or tolerance) so a one-pixel diff does not eject a PR from the queue.
