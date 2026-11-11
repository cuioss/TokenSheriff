envelope_version=1
sender_type=plan
sender_id=implement-plan-23-sonar-backlog-cleanup
epic=quality-report
kind=candidate-lesson
created=2026-11-05T07:53:17Z

component=plan-marshall:phase-6-finalize
category=bug
source_plan=implement-plan-23-sonar-backlog-cleanup
signal=review-integrity false positive (review_support test-integrity)

# review_support test-integrity grades against the origin/main tip instead of the merge-base, producing false weakened_test findings on a lagging branch

## Symptom

During finalize, the functional/code review's deterministic `review_support test-integrity` check reported a `weakened_test` finding on a test file the plan had not weakened. Upstream `main` had moved since the branch was last rebased, and upstream had itself edited that same test file. After rebasing the branch onto the current `origin/main`, the finding cleared with no change to the plan's own edits.

## Root cause

`test-integrity` resolves its default `--base-ref` to the current `origin/main` tip and compares assertion counts between that tip and the branch head. When the branch lags a moved `main`, the two-dot comparison attributes upstream's assertion changes to the branch: assertions upstream added since the fork point look as if the branch removed them. The comparison should be anchored on the merge-base (`git merge-base origin/main HEAD`), i.e. three-dot semantics, so only the branch's own changes are graded.

## Corrective action

- Fix: resolve the default base as the merge-base of the branch and `origin/main`, not the `origin/main` tip, so a rebase lag can never manufacture a `weakened_test`.
- Until fixed: when `test-integrity` flags a file that upstream also touched since the fork point, rebase (or pass an explicit `--base-ref` set to the merge-base) and re-run before treating the finding as real. Do not "fix" the test to satisfy the finding.

## Evidence

Plan `implement-plan-23-sonar-backlog-cleanup` (PR #855, TokenSheriff, merged as 0c56583e): the false finding appeared after main moved, and cleared after the sync-baseline rebase onto `origin/main`.

## Routing note

The component above is the finalize pipeline that surfaced the finding; the owning script is the `review_support` `test-integrity` subcommand, so re-target the component to that script's skill on pickup. This candidate was first filed into the global lessons store by an earlier run that missed the orchestration verdict (2026-11-05-07-001, now tombstoned); it is re-routed here and is not a duplicate of any live lesson.
