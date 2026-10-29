envelope_version=1
sender_type=plan
sender_id=implement-plan-02-refresh-family-rotation
epic=quality-report
kind=candidate-lesson
created=2026-10-28T21:34:12Z

component=plan-marshall:phase-6-finalize
category=bug
dedup=checked against active corpus (0 lessons): no overlap

# finalize-step-sync-baseline skip rule and the pre-merge gate disagree on head-dependence

The finalize pre-merge gate refused with `merge_candidate_not_gated`. `finalize-step-sync-baseline` had stamped an old `head_at_completion`, and on re-entry the step was skipped as non-head-dependent. The gate, however, derives head-dependence FROM THE STAMP, so the stale stamp made the step head-dependent in the gate's eyes while the step's own skip rule treated it as not head-dependent. Main advanced mid-run, the only sanctioned fix was a rebase, and the rebase invalidated every verdict: audit, two reviews and the builds all re-ran (about one hour lost).

## Solution

Make the step's skip rule and the gate's head-dependence derivation read the same source: a step that stamps `head_at_completion` must not be skipped on re-entry while that stamp differs from the current head, or the gate must not derive head-dependence from a stamp the step may leave stale. Until reconciled, re-run sync-baseline explicitly when main has advanced before the gate fires.

## Impact

Any long-running finalize during which the base branch advances can hit a late, expensive gate refusal that forces a rebase plus a full verdict re-run.
