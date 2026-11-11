envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:40Z

# Push freshness gate fired twice in finalize: once reconciled after the gate's own commit, once refused after an inline-fix commit

**Source**: decision log 5120d0 and ea5e6d (18:33, freshness-reconcile); work log 351fcc (18:52, "push refused head_not_validated_by_gate"), 2026-11-04.

## What happened

Two separate freshness events, both caused by a commit landing after the last validated build:

1. **18:33 - reconciled.** The `pre-push-quality-gate` step made its own commit 25e892e (the OpenRewrite TODO replacement). That moved the worktree SHA past the last successful build, so the `push` step needed a `freshness-reconcile` decision record attributing the stale SHA to a finalize-internal `mutates_source` commit before it would push. This worked as designed, but it was not automatic.
2. **18:52 - refused.** The wait-region triage committed the two CodeRabbit inline fixes as 310669e. The push was refused with `head_not_validated_by_gate`, and the run had to re-fire security-audit, functional-review, code-review, the pre-push gate (about 8 minutes of Maven), push, ci-verify, automatic-review and sonar-roundtrip at the new HEAD.

## Candidate rule

Any finalize step that commits source should expect the head-dependent chain (reviews, pre-push gate, push, CI, review bots) to re-fire, and should plan for that cost. Where possible, batch inline review-bot fixes into one commit, and avoid source commits from inside the pre-push gate (see the OpenRewrite candidate) so the gate validates the head it pushes.
