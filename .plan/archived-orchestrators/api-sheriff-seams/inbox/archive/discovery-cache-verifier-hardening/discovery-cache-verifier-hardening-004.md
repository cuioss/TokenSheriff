envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:24:56Z

# Candidate lesson: the code review repeats the reobserved weakened_test row at the new head

Source: Q-Gate finding 5ad18c (6-finalize, code-review, accepted).

## Observation

At the same new head as finding 3c3669, the code review produced its own copy of the
`weakened_test` row on `CachingDiscoveryResolverTest::invalidDurations` (49 assertion calls on main,
60 on the branch). This was the fourth manual acceptance of one artifact: two reviews times two heads.

## Candidate rule

The number of operator decisions for one measurement artifact currently grows with reviews times
heads. Fixing the counter (skip import lines) removes the whole set. Carrying an acceptance across
reviews and unchanged heads is the fallback if the counter cannot change.
