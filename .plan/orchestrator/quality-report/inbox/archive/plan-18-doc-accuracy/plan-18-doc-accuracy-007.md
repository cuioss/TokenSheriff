envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:48Z

# Candidate lesson: remote-merge gate asks the operator about a rebase that the merge queue skips anyway

**Source record**: orchestrator observation from this run's 6-finalize merge gate (not backed by a Q-Gate finding; forwarded by the dispatcher for judgement).
**Suggested component**: plan-marshall:phase-6-finalize
**Suggested category**: improvement

## What happened

With `auto_rebase_threshold=no_overlap_only` and `use_merge_queue=true`, the remote-merge gate blocked and asked for an operator decision because the branch had a clean one-file overlap with main. Under `use_merge_queue=true` the rebase is skipped anyway (the merge queue builds the merge group itself), so the question had no consequence for what actually happened.

## Corrective rule

The rebase-threshold gate should be evaluated only on the path that actually rebases. When `use_merge_queue=true`, either skip the `auto_rebase_threshold` check entirely or downgrade it to an informational log line; reserve the blocking operator prompt for non-merge-queue merges where a rebase would really run. A clean overlap (no conflicts) is also a weak reason to block even without the merge queue.
