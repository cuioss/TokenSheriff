envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:20Z

# Candidate lesson: rebase in finalize orphaned every change-ledger commit SHA, so review attribution degraded to declared_only

- Signal: process observation 4 of the run (decision log WARNING)

## What happened

Branch cleanup found main had moved with an overlapping content conflict, and the operator chose "Rebase and force-push,
resolving the conflicts". The rebase onto main (new head 33e68ac0) rewrote every plan commit SHA. The change ledger still
named the pre-rebase SHAs, so `review_support attribution` answered `declared_only / commit_not_on_branch` for all six
deliverables (attributed_count 0). The review rounds after the rebase had to derive scope by hand from the declared file
lists and the live footprint. Files the ledger had attributed beyond the declarations (sibling malformed-response tests,
test-strategy.adoc, the TASK-016/018 test class) resurfaced as `unrequested_change` candidates and had to be re-judged
against the earlier verdicts.

## Evidence

- Decision log a1c6a7 (classifier overlap_with_content_conflict, operator chose rebase), 5041de (rebase routing),
  8855b7 (05:38:13Z, "change-ledger rows name pre-rebase commit SHAs that are no longer on the branch: attribution answers
  declared_only / commit_not_on_branch for all six deliverables"), adb02c ("attribution would answer declared_only after the
  rebase, so the scope is the live footprint only"), 5157a4 (six unrequested_change entries "because the rebase left the
  change ledger without matching commits").

## Observation for the epic

`manage-change-ledger` has a `remap` verb, but the rebase path in branch cleanup did not run it; the ledger and the branch
disagreed for the rest of the plan.
