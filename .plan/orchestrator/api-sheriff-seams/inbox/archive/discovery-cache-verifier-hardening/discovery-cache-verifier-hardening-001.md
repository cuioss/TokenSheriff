envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:24:31Z

# Candidate lesson: test-integrity checker counts `import static ...Assertions.assertAll` lines as assertions

Source: Q-Gate finding 422520 (6-finalize, functional-review, accepted by operator as a false positive).

## Observation

The `weakened_test` check reported that 1 assertion present at origin/main is gone from
`CachingDiscoveryResolverTest::invalidDurations` (fix_kind correction). It was a measurement artifact:
the checker pattern `\.assert[A-Z]\w*` also matches `Assertions.assertAll` inside `import static` lines.
origin/main had 7 explicit static assertion imports, which counted as 7 assertions. The branch had one
wildcard import, produced by an OpenRewrite rewrite, which counted as 0. Assertion calls actually rose
from 49 to 55.

## Candidate rule

The assertion counter should skip `import` lines, or count only call sites, so that an import-style
rewrite (explicit to wildcard static imports) is not reported as a weakened test.

## Cost in this run

The operator had to accept the same false positive by hand at every review round and every new head.
This is the first of 5 accepted rows with the same cause.
