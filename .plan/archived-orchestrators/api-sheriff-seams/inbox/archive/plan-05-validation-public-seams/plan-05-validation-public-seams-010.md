envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:35Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=qgate 6-finalize fe394e (code-review, resolution fixed inline fa6c27f4)

# Replay guard purged on a concurrent validation's later instant

## What happened

Even with a single decision instant per call, a concurrent validation running at a later instant could purge the entry,
so a replay judged fresh at an earlier instant could still be admitted at the `iat + window` edge. The fix made the
validator read the clock, run the claim checks and admit the `jti` as one critical section (fa6c27f4), with a two-thread
regression test that fails without the lock.

## Proposed rule

The time read, the freshness check and the replay-set admission form one atomic decision. Test it with a concurrent
interleaving, not only with straddling clocks. This was the fourth review round on the same replay-window family
(63639f, d9b039, 3c8e51, fe394e). Designing the atomic decision up front would have avoided three of them.
