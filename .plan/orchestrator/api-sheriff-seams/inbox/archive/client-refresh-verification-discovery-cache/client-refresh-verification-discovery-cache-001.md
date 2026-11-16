envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:07:09Z

component=plan-marshall:phase-5-execute
category=improvement
source_plan=client-refresh-verification-discovery-cache
source_record=qgate:6-finalize:cdc31c

# Concurrency race in a new caching component was caught only at finalize code-review, not during execute

## What happened

TASK-005/006 added `CachingDiscoveryResolver` (token-sheriff-client, discovery package), a cache with a
single-flight refresh and a stale-grace policy. Execute-phase verification (compile, `verify -Ppre-commit`,
adversarial and stale-policy tests) was green. The finalize `default:code-review` step then raised a
blocking Q-Gate finding (cdc31c, severity error, lines 164-177): a refresh that failed could overwrite a
newer successful cache entry with a failed state derived from a stale snapshot. That forced a code-review
loop-back (iteration 1/5) and an inline fix commit in 6-finalize.

## Why it slipped

The execute-phase tests exercised the single-thread success / failure / stale-grace paths. Nothing
modelled two refreshes interleaving (refresh A starts on snapshot S0, refresh B publishes S1, A fails and
publishes "failed" over S1). The task's verification profile carried no concurrency-review obligation for
a component whose whole point is shared mutable state.

## Candidate rule

When a task introduces or changes a component holding shared mutable state across threads (cache,
single-flight refresh, compare-and-set publication), the execute task should include a short explicit
interleaving review: for each publish, confirm it is conditional on the snapshot it was derived from
(CAS or version check), and add at least one test that forces the stale-publisher interleaving.
Otherwise the defect surfaces only at finalize code-review, where every fix advances HEAD and re-runs
the review rounds (see the companion candidate on review re-run cost).
