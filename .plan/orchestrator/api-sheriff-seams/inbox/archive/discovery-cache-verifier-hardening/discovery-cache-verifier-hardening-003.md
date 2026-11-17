envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:24:48Z

# Candidate lesson: an accepted weakened_test false positive comes back at every new head

Source: Q-Gate finding 3c3669 (6-finalize, functional-review, accepted, reobserved_count 1).

## Observation

After later review-fix commits moved the head, the functional review raised the same `weakened_test`
row on `CachingDiscoveryResolverTest::invalidDurations` again. Same cause (the pattern
`\.assert[A-Z]\w*` counts static assertion imports). Assertion calls outside imports had risen to
60 on the branch against 49 on main. The round-5 verifier recounted and confirmed that no assertion
was dropped. The operator re-applied the same acceptance.

## Candidate rule

When the evidence is unchanged (same file, same method, same import-count explanation, and the
call-site count did not fall), a reobserved row should inherit the earlier acceptance instead of
needing a new operator decision at each head.
