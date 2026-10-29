envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=candidate-lesson
created=2026-10-29T13:11:01Z

## Candidate lesson: wall-clock boundary tests need wide margins and a cache-hit assertion before the boundary

**Source**: review-bot findings 82b682 (inline) and e94755 (review body), CodeRabbit on PR #833 at head 9648063c; both resolved `fixed` by TASK-4, commit 467a857b.

**What happened**: the two new cache-hit regression tests in `AccessTokenValidationPipelineTest` (`shouldRejectCachedTokenExceedingIssuerMaxTokenAge`, `shouldRejectExpiredCachedTokenUnderOwnIssuerSkew`) had two weaknesses:

1. The time margins were 1-3 s (`maxTokenAgeSeconds = 4` with `iat` about 1-2 s ago; `exp` about 2-3 s ahead). A stall of about 2 s before the cache-priming call could expire the token early, so the test would fail on its first assertion instead of testing the cache-hit rejection.
2. Both tests also passed if every cache lookup missed. The uncached path rejects the token with the same security event and counts it once, so a regression that stopped caching would not fail either test, even though the tests were described as cache-hit regression tests.

**Corrective rule (proposed)**:
- For tests that cross a wall-clock boundary, leave several seconds of margin before the first (priming) call, and keep the wait below the shared boundary-wait bound.
- When a test claims to cover the cached path, assert a valid cache hit (for example a hit counter of 1) before the boundary. Then a regression that turns every hit into a miss fails the test, instead of passing through the uncached path with the same outcome.

**Scope**: Java test authoring for cache and time-policy code (token-sheriff-validation); generic across any "cached vs uncached path produces the same observable" test.
