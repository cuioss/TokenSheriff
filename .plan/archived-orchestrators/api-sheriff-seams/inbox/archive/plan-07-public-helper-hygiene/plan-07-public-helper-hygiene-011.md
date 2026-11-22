envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:32:27Z

# Candidate lesson: each fix commit after review makes both review steps and the pre-push gate stale again, and a round that ends in loop_back is a full round again

**Source signal**: orchestrator-observed during the run, checked against the plan's work and decision logs.

## What the record shows

After PR #908 was opened, five fix commits moved HEAD: 94b98aec (review dispositions), 323601d9 (bot buffer change), 5e179faa (revert of that change), cd8a95c6 and a243b688 (memory-bound doc wording). Each one restarted the review steps:

- functional-review was dispatched 6 more times after 20:43 and code-review 5 more times (work log 20:43:48Z to 21:23:11Z). Rounds were discarded twice because HEAD moved during the round (work log 18:35:45Z and 20:49:18Z, "HEAD moved under the round ... re-running from Step 1"), and once because the round's own fix moved HEAD (21:01:17Z).
- pre-push-quality-gate ran in full twice (19:24:10Z and 21:51:03Z), and ci-verify twice (19:33:37Z and 22:12:44Z). The second CI run also hit an unrelated e2e browser flake and needed a re-run (decision log 22:04:49Z).
- Full versus delta: the replay after the first loop_back was dispatched as `round_kind=full (last_firing_not_done)` across all 15 deliverables (decision log 18:26:59Z). Later rounds after a `done` record were delta rounds (20:43:47Z onward).

## Suggested rule (for orchestrator classification)

Group review-fix edits into as few commits as possible, and check each fix against the spec before committing (see the BoundedBodyHandler revert candidate), because every new HEAD re-stales functional-review, code-review and the pre-push gate. Make sure a review step's last record is `done` before the next fix lands. A step whose last record is `loop_back` replays as a full round over every deliverable, not as a delta.
