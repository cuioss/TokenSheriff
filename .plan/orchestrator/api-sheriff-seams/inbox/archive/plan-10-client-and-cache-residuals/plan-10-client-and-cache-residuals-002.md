envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:06:48Z

# `ci pr merge-queue` enqueue arms GitHub auto-merge, which must be disarmed before any rebase or force-push re-entry

**Signal source:** branch-cleanup re-entry after queue ejection (decision log 11:40:30Z).
**Component guess:** plan-marshall:tools-integration-ci (pr merge-queue) / plan-marshall:phase-6-finalize (branch-cleanup re-entry).

## What happened

PR #920 was ejected from the queue and then conflicted with main, so the operator authorized a rebase and force-push. At that point the PR still had GitHub auto-merge armed (merge_method MERGE), left over from the earlier `ci pr merge-queue` enqueue. Had it stayed armed, the rebased head could have been enqueued and merged as soon as CI went green, before the head-dependent finalize steps (code-review, functional-review, pre-push gate, automatic-review, merge barrier) ran again on it. The orchestrator disarmed it by hand with `gh pr merge --disable-auto`, a direct gh call outside the CI abstraction.

## Candidate rule

- The enqueue verb's return should state that it armed auto-merge. The ci abstraction should offer a matching disarm verb so callers do not need a raw `gh pr merge --disable-auto`.
- Any branch-cleanup re-entry that will rewrite the head (rebase, force-push, fix loop-back) must first disarm auto-merge. The merge barrier should treat `auto-merge on` at a non-final head as a blocker. The final barrier line at 12:47:22Z already checks for `auto-merge off`, so the check exists at merge time but not at re-entry time.
