envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:05Z

# Candidate lesson: weakened_test false positive accepted a fifth time after a further head change

Source: Q-Gate finding 4d017e (6-finalize, code-review, accepted).

## Observation

A later head (21:15Z) produced the code-review `weakened_test` row on
`CachingDiscoveryResolverTest::invalidDurations` once more (49 assertion calls on main, 60 on the
branch). The finding detail itself names "import-line counting" as the cause. The functional
review row at this head is not among the accepted rows, so only the code-review copy came back.

## Candidate rule

Five accepted rows in one plan with one root cause (`\.assert[A-Z]\w*` matching
`import static ...Assertions.assertAll`) is evidence for a checker fix, not for more acceptances.
When a later plan makes an OpenRewrite import change in a test file, expect this row until the
counter excludes import lines.
