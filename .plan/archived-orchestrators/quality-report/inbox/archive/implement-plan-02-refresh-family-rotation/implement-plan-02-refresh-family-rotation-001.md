envelope_version=1
sender_type=plan
sender_id=implement-plan-02-refresh-family-rotation
epic=quality-report
kind=candidate-lesson
created=2026-10-28T21:34:03Z

component=TokenSheriff-client
category=anti-pattern
dedup=checked against active corpus (0 lessons): no overlap

# Enumerate every writer of both slots before fixing any one race in a two-slot update

A two-slot update (the token store plus the rotation-family map) needed FIVE successive code-review rounds to close its races: stale re-store, adopt-unknown-token, in-flight re-login, store/family pairing, applyRefresh teardown, and refusal teardown. Each fix exposed the neighbouring writer that still bypassed the newly introduced lock, so the review found the next hole only after the previous one was closed.

## Solution

Before fixing any single race on state held in two coordinated slots, list EVERY writer of BOTH slots (store, family map, teardown paths, refusal paths, re-login paths, adopt paths) and route all of them through the one lock or one pairing helper in a single change. Treat "one more writer bypasses the lock" as the default failure mode of a partial fix, and have the implementer plus the first review round verify the writer inventory rather than the single reported race.

## Impact

Avoids repeated review rounds (here five) for what is a single design defect: an invariant spanning two slots that is enforced at only some of their write sites.
