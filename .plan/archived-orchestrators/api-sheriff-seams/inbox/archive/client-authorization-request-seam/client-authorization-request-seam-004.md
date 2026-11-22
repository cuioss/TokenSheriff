envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:25Z

component=plan-marshall:phase-6-finalize
category=improvement

# Conflict gate with use_merge_queue=true forces a costly rebase for overlap_no_content_conflict under the default no_overlap_only threshold

## Observation

With `use_merge_queue=true`, the conflict gate returned `overlap_no_content_conflict`. Under the default threshold `no_overlap_only` this forced `queue_wait=forbidden`, an operator question, and an immediate-merge rebase plus force-push.

The rebase rewrote every commit SHA, invalidating every head-dependent verdict (functional-review, code-review, pre-push gate) and costing a full re-review round, although the overlap had no content conflict and the merge queue itself could have performed the rebase.

## Tradeoff

For queue-enabled projects, a file-level overlap with no content conflict is exactly what the merge queue resolves safely. Forcing a local rebase trades a cheap queue-side rebase for an expensive re-verification round.

## Proposed rule

When `use_merge_queue=true`, either default the conflict-gate threshold to `auto_resolvable` (letting `overlap_no_content_conflict` proceed to the queue), or document the cost explicitly so operators choose the threshold knowingly. Reserve the local rebase path for genuine content conflicts.
