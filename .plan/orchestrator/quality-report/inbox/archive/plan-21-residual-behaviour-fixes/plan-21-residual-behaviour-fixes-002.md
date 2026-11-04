envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:03Z

# Candidate lesson: sync-baseline record forwarded --head-at-completion and broke the pre-merge HEAD binding

Source signal: finalize run fact (operator-observed during plan-21-residual-behaviour-fixes).

## What happened

The first sync-baseline record in finalize forwarded `--head-at-completion`, although that
step's workflow doc does not record a HEAD-at-completion value. The extra binding was later
compared by the pre-merge barrier against the actual merge candidate and tripped
`merge_candidate_not_gated`. The record had to be re-recorded without the flag before the
merge could proceed.

## Possible corrective direction (for orchestrator classification)

- Agent discipline: forward only the flags a step's own doc lists for its recording call;
  do not carry over a flag seen on a sibling step's record (anti-pattern of flag
  extrapolation across steps).
- Script side: the sync-baseline recording verb could reject or ignore
  `--head-at-completion` when the step does not own a HEAD binding, so the barrier is not
  fed a stale HEAD.

Components: plan-marshall:phase-6-finalize (sync-baseline step), plan-marshall:manage-status (merge-authorization record).
