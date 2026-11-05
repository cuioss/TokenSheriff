envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:20:11Z

# Merge-queue landing wait cannot tell a slow queue from a dequeued PR; post-merge wait blocks on benchmarks

**Defect**:
- The first enqueue of PR #861 was ejected when the merge-group `e2e-tests / test` job hung until its 30-minute job timeout (the same job took 185 s on the PR head and passed on the second enqueue).
- The landing wait budget (1800 s) ran out while the queue was still healthy, forcing an F1 loop-back; the landing wait cannot distinguish a slow queue from a dequeued PR until the entry disappears.
- The post-merge `checks wait --adaptive` blocked for more than 600 s because the only pending check was the post-merge Performance Benchmark workflow, which is not a gate.

**Corrective action**:
- The landing gate should re-check queue membership before declaring budget exhaustion (still queued -> extend/keep waiting; dequeued -> loop back).
- The post-merge CI wait should exclude post-merge benchmark workflows (non-gating checks).

**Evidence**: plan-13-log-transport-hygiene work log (merge-queue ejection, F1 loop-back, post-merge checks wait), PR #861.

**Suggested component**: plan-marshall:tools-integration-ci / plan-marshall:phase-6-finalize
