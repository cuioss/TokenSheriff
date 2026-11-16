# PLAN-08: Discovery cache and refresh verifier hardening

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-08-discovery-cache-and-verifier-hardening.md` and is queued as one row file, `queue/PLAN-08.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the layer-3 analysis of PLAN-04's landing (#907, `landings/PLAN-04.md`), staged on the operator's word.

## Objective

PLAN-04 shipped `RefreshResultVerifier` and the opt-in `CachingDiscoveryResolver` (#907, merged as `1be3867b`). Both
are new public API that API Sheriff's PLAN-34 adopts once 0.9.10 is released, so what is wrong with them is cheapest to
fix before that release. This plan closes the defects the landing analysis found and read at HEAD: the Quarkus
discovery-cache settings are not validated for value at startup although the producer's startup observer is documented as
validating them and the generated summary on #907 says invalid settings fail there; the cache records an unexpected delegate failure nowhere and turns one caller's interrupt into a cache-wide failure;
four regression-test gaps leave the changed-endpoint criterion and the single-flight join unproven; the discovery cache's
logging and configuration text overstate what the code does; and the verifier's one open spec question (the refreshed ID
token's `sub` against nothing) is neither closed nor documented. Nothing here widens the public API beyond what a fix needs,
and the default `DiscoveryResolver` bean, `RefreshFlow.refresh` and `TokenLifecycleManager`'s observable disposition do not
change.

## Deliverables

1. **Discovery-cache settings are validated for value at startup.** Today
   `TokenSheriffClientProducer.validateDiscoveryCacheOnStartup` calls `ClientConfigMapper.validateDiscoveryCache`, which
   checks only that an enabled cache has a TTL and that no `sheriff.client.discovery-cache.*` key is an orphan; the value
   checks of the `CachingDiscoveryResolver` constructor run only when the `@ApplicationScoped` producer first runs, that is
   on the first call through the client proxy. Make an enabled cache with an invalid value fail application startup with a
   message naming the property and the fix: `ttl` zero or negative or not a parseable `Duration`, `grace-period` or
   `min-refresh-interval` negative or unparseable, `jitter` negative, unparseable or not smaller than `ttl`, and any value
   the outline settles as too large for the arithmetic of deliverable 2. The check reuses the constructor's rules and does
   not copy them into a second list. Verification, one test per invalid value (parameterized), each with a valid
   configuration as its positive control: an enabled cache with the invalid value fails the startup validation, and a
   disabled cache with the same value still fails as an orphan. Whether injecting `CachingDiscoveryResolver` while the cache
   is disabled should fail at deployment instead of at first use is settled at outline: the producer is unconditional today
   and the failure is lazy; the outline either changes that or documents the lazy failure where the docs now say the
   injection fails.
2. **The cache survives what the delegate can throw.** `CachingDiscoveryResolver.refresh` caches only a
   `TransportException` as a failure. Verification, each by a test with a scripted delegate and an injected clock:
   - A runtime exception other than `TransportException` raised by the delegate is recorded as a failed refresh with its
     class as the cause class (never its message, never a response body), so `status()` and `getLoaderStatus()` report the
     failure and the backoff applies; today the slot is released, no state is written, `status()` never shows `FAILED` and
     every call refetches. The exception the winner rethrows and the one the joiners receive are both stated in the outline.
   - A caller thread's interrupt during the winner's HTTP call does not by itself start a cache-wide failure episode that
     fails every joiner closed and holds off retries for `minRefreshInterval`. How is settled at outline: a refresh that
     ended by interrupt is not recorded as a provider failure, or the interrupted winner hands the slot on; the behaviour is
     stated and tested, with the interrupt flag of the interrupted thread preserved.
   - `ttl`, `grace-period`, `min-refresh-interval` and `jitter` cannot overflow the `Instant` or `long` arithmetic
     (`success.plus(ttl)`, `lastSuccess.plus(ttl).plus(gracePeriod)`, `lastFailureAt.plus(minRefreshInterval)`,
     `jitter.toMillis()`): either each has an upper bound refused at construction, or the arithmetic saturates; a value at
     and a value just beyond the bound are both tested.
   - Settled at outline, each either changed with a test or documented as accepted: a joiner waits for the winner's full
     refresh even when a servable stale document exists; the shared stored exception instance is rethrown to every caller
     when nothing was ever resolved; the duplicate fail-closed WARN a lost `withExpiryLogged` compare-and-set can produce.
3. **The regression tests prove what they claim.** Add or strengthen, each in an existing or new class at or under 500
   lines:
   - A refreshed document whose `tokenEndpoint` (and one other endpoint) differs from the cached one is returned by the next
     caller after the refresh and not before it. Today no test serves a different valid document after a refresh, so a
     refresh that updates `lastSuccess` but keeps the old metadata would pass every test. This closes PLAN-04 deliverable 4's
     "a changed endpoint is visible to the next caller" criterion.
   - The stampede test shows that the other callers have joined the in-flight refresh before the held request is released
     (a latch or a counter on the join path), not only that one fetch happened.
   - The jitter test fails for an implementation with zero jitter: it asserts that at least one freshness window is shorter
     than `ttl` over a seeded or enough-samples run, and that none is shorter than `ttl - jitter`.
   - The stale-snapshot regression test (the single-flight race fixed in #907) fails when the `latest != current` guard is
     removed (a negative control kept in the repository, for example a package-private seam or a mutation-style guard
     test), and does not depend on `clock.instant()` being read after `state.get()`.
   - The branches no test reaches today are reached: the aborted-refresh path, the `join` `CompletionException` path, the
     `serveStaleOrFail` branch with no stored failure, and joiners receiving a failed refresh.
4. **The discovery cache's logging and configuration text match the code.** The landing analysis found these statements
   wrong or overstated at HEAD; each is fixed in the doc, the Javadoc or the code, whichever the outline decides is right,
   and the final text matches the shipped behaviour. Verification: a re-read of each location against the code.
   - Logging: the delegate `DiscoveryResolver.resolve()` logs `DISCOVERY_RESOLVED` at INFO on every successful resolve and
     `DISCOVERY_FAILED` at ERROR or WARN on every failed attempt, so a short TTL still logs INFO once per TTL and a failing
     provider is logged once per attempt. `CachingDiscoveryResolver`'s class Javadoc ("A successful refresh logs at DEBUG"),
     `doc/LogMessages.adoc` (INFO `4`: "an ordinary successful refresh logs at DEBUG only"),
     `doc/client/specification/retrieval-flow.adoc` ("A failure episode is logged once, not per attempt") and the
     operator-facing text say or imply otherwise. PLAN-04's constraint was that a discovery refresh must not log at INFO on
     every refresh cycle. The outline decides between a quiet resolve path used only by the cache (any change to
     `DiscoveryResolver` keeps `resolve()` behaviour for existing callers and keeps `DiscoveryResolverTest`,
     `DiscoveryAdversarialTest` and `ProviderMetadataTest` green unedited) and stating the delegate's logging plainly.
   - Defaults: "the grace period defaults to zero" (`doc/client/requirements.adoc` `CLIENT-29`,
     `doc/client/threat-model.adoc`) is true for the Quarkus configuration and not for the Java constructor, which requires
     the value; "a failed refresh is not retried before the minimum refresh interval has elapsed" holds nothing back at the
     default `PT0S`; state both.
   - Injection: `quarkus-config-doc.adoc` and `retrieval-flow.adoc` say injecting the bean while the cache is disabled
     fails at injection; per deliverable 1 it fails at first use unless the outline changes that.
   - Identifier form: `retrieval-flow.adoc` and `test-strategy.adoc` write `TokenSheriffClient-4` where
     `doc/LogMessages.adoc` writes `TokenSheriffClient-004`; align them to the form the runtime emits (verify the emitted
     string, do not assume).
   - ADR-0026: its `affects` metadata and text cover only `token-sheriff-validation-quarkus` resolvers, while `CLIENT-29` and
     `retrieval-flow.adoc` cite it for the client-quarkus check; amend the ADR (metadata and a sentence) or stop citing it
     for the client module.
5. **The verifier's open question is closed and documented.** `RefreshResultVerifier.verify` compares the refreshed ID
   token's `sub` with the access token's `sub` only when the access token carries one, and compares the bound subject with
   the access token's `sub` only. With a bound subject the gap does not exist (an access token without `sub` is refused at the
   binding step; one with `sub` makes the ID token's `sub` equal the bound one through the §12.2 check). With no bound
   subject and an access token without `sub`, the ID token's `sub` is compared with nothing and the session adopts none.
   PLAN-04's spec left this as a HYPOTHESIS to settle at outline; no document records the answer. Verification: the outline
   settles whether the verifier should refuse or whether the rule stays (nothing is bound, so there is no principal to
   protect), the decision and its reason are written in `doc/client/specification/token-handling.adoc` and
   `doc/client/threat-model.adoc` (the 12.2 row), `RefreshResultVerifierTest` keeps its case that pins the behaviour (or
   changes it with the decision), and the duplicate constant-time compare in `RefreshResultVerifier` and
   `StoredToken.refreshed` is either kept with a stated reason or removed. The outline also states whether
   `TokenLifecycleManager` should pass the stored subject to `verify` instead of `null`: today the bound step has no
   production caller and `StoredToken.refreshed` is the only bound check on that path, which is correct.

Done when: all criteria pass, `RefreshPathCoverageGuardTest` and the coverage includes of `token-sheriff-client/pom.xml`
still pass, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar open-issue read at
the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **Fail-fast for a disabled-cache injection** (deliverable 1): deployment-time failure, or the documented lazy failure.
- **Interrupt handling** (deliverable 2): which behaviour the cache has, as it is a public-class contract of 0.9.10.
- **Upper bounds on the four durations** (deliverable 2): values, or saturating arithmetic. The PLAN-04 outline left a
  hard maximum TTL unshipped and the docs are silent about it.
- **Quiet resolve path versus documented delegate logging** (deliverable 4): the first adds an internal seam to
  `DiscoveryResolver`; the second leaves one INFO per TTL.
- **The refreshed ID token's `sub` against an unbound session** (deliverable 5): refuse, or keep and document.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments.
- **Default behaviour stays unchanged and is proven so.** `DiscoveryResolver.resolve()` keeps its behaviour for every
  existing caller; the produced `DiscoveryResolver` bean stays non-caching; `TokenLifecycleManager` keeps the observable
  disposition of PLAN-04 deliverable 3 (quarantine on a rotated refusal, nothing on a non-rotated one, the check before the
  family advance, the WARN `REFRESHED_ID_TOKEN_INCONSISTENT` once); the existing tests named in PLAN-04's deliverable 3 and
  `DiscoveryResolverTest`, `DiscoveryAdversarialTest`, `ProviderMetadataTest` pass without edited assertions.
- **Overridability (AGENTS.md "Preferred overloads", ADR-0006).** `RefreshFlow`, `RevocationClient` and
  `TokenLifecycleManager.applyRefresh` keep their signatures and dispatch; a new seam on `DiscoveryResolver`, if the outline
  adds one, is package-private or an additive overload.
- **Security.** Subject comparison stays constant-time. No token, subject, refresh token or response body is logged
  unsanitised; attacker-controlled text goes through `LogSanitizer`; the status carries a cause class only. A new LogRecord
  takes the next free identifier in its range at write time (INFO `4`, WARN `115` and `116` are taken by PLAN-04) and is
  added to `doc/LogMessages.adoc`.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never
  sleeps), a negative control per regression test, new or grown test classes at or under 500 lines.
- No new dependency without operator approval. Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or
  dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`.
  A dismissal the run cannot transmit is a blocker, never a silent pass.

## Claim Labels

- OBSERVED: the Quarkus startup check validates presence only: `validateDiscoveryCache` rejects an orphan key when the cache is
  disabled and a missing `ttl` when it is enabled, and nothing else; `mapDiscoveryCache` reads the durations with
  `config.getValue(..., Duration.class)` and `getOptionalValue`; the startup observer calls only `validateDiscoveryCache` — read
  at `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`
  § `validateDiscoveryCache` (:178-197), `mapDiscoveryCache` (:209-226) and `DISCOVERY_CACHE_SETTINGS` (:120), and at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`
  § `cachingDiscoveryResolver` (:171-182) and `validateDiscoveryCacheOnStartup` (:193)
- OBSERVED: the constructor rules (`ttl` positive, `gracePeriod`, `minRefreshInterval` and `jitter` not negative, `jitter` smaller
  than `ttl`) are enforced only in `CachingDiscoveryResolver`'s constructor — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java` § constructor
  (:127-150)
- OBSERVED: `refresh` catches only `TransportException`; `refreshAsWinner` releases the slot and completes the claim
  exceptionally on any other exception but writes no state; `serveStaleOrFail` rethrows the stored `lastFailure` instance when
  nothing was ever resolved — read at `CachingDiscoveryResolver.java` § `refresh` (:229-255), `refreshAsWinner` (:198-216),
  `serveStaleOrFail` (:273-297)
- OBSERVED: the duration arithmetic is unguarded: `success.plus(ttl).minus(randomJitter())` (:233), `jitter.toMillis()` (:262),
  `lastFailureAt.plus(minRefreshInterval)` (:375) and `lastSuccess.plus(ttl).plus(gracePeriod)` (:379) — read at
  `CachingDiscoveryResolver.java`. That an extreme value then throws `DateTimeException` or `ArithmeticException` is the
  landing analysis's reasoning from the `java.time` contract, not executed
- OBSERVED: the delegate turns `InterruptedException` into a `TransportException`, restoring the thread's interrupt flag, and
  the cache records every `TransportException` as a failure — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java` § the request send
  (:176-195) and `CachingDiscoveryResolver.java` § `refresh` (:241-254)
- OBSERVED: the delegate logs `DISCOVERY_RESOLVED` at INFO on every successful `resolve()` (`DiscoveryResolver.java`:140), a
  `DISCOVERY_STATUS` WARN on a non-success status (:179) and a `DISCOVERY_FAILED` ERROR on an `IOException` (:189), each per
  attempt; the cache itself logs a success at DEBUG (`CachingDiscoveryResolver.java`:237) — read at both files
- OBSERVED: `retrieval-flow.adoc` states "the delegate's own discovery `INFO` line therefore appears at most once per TTL
  period" and "A failure episode is logged once, not per attempt"; `CachingDiscoveryResolver`'s Javadoc and `doc/LogMessages.adoc`
  INFO `4` say an ordinary successful refresh logs at DEBUG only — read at `doc/client/specification/retrieval-flow.adoc`
  (:290-296), `CachingDiscoveryResolver.java` (:60-66) and `doc/LogMessages.adoc` (:212)
- OBSERVED: the verifier's three steps, the bound-subject step comparing the refreshed access token's subject, and the
  §12.2 check comparing `sub` only when the access token carries one — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshResultVerifier.java` § `verify` (:114-143),
  `consistentWithAccessToken` (:151-159), `subjectMatches` (:165-171)
- OBSERVED: `TokenLifecycleManager` calls `RefreshResultVerifier.verify(rotation, idTokenValidationBridge, null)`, catches the two
  carriers and quarantines a rotated exchange, and passes the access token's subject to `applyRefresh`; `StoredToken.refreshed`
  refuses a bound session whose refreshed subject differs or is absent and adopts a null subject otherwise — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `doRefresh`
  (:878-926) and
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/StoredToken.java` § `refreshed`,
  `subjectsMatch` (:166-195)
- OBSERVED: `RefreshResultVerifierTest` pins "access token without subject, nothing bound: passes" — read at
  `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshResultVerifierTest.java` (:104-111)
- OBSERVED: no test in `CachingDiscoveryResolver{,Adversarial,StalePolicy}Test` serves a valid document that differs from the
  cached one after a refresh (a search of the three classes for changed or different endpoints found only a hostile-document
  case that keeps "token endpoint unchanged"); the existing test names are listed at
  `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverTest.java` (the
  `@DisplayName` rows :59-269)
- OBSERVED: the new and grown test classes are at 292, 316, 239, 309, 215, 119 and 302 lines (`CachingDiscoveryResolverTest`,
  `...AdversarialTest`, `...StalePolicyTest`, `RefreshResultVerifierTest`, `RefreshResultVerifierAdversarialTest`,
  `RedeemedIdentityRefusalExceptionTest`, `TokenSheriffClientDiscoveryCacheTest`) — counted by the landing analysis from
  the merge diff
- HYPOTHESIS: a joiner waits for the winner's whole refresh even when a stale document is servable — read at
  `CachingDiscoveryResolver.java` § `join` (:218-227), `resolve` (:189-192); the cost (a slow provider with the TTL just
  expired blocks every caller for the read timeout) is the landing analysis's reasoning, not measured (verify-at-outline)
- HYPOTHESIS: injecting `CachingDiscoveryResolver` while the cache is disabled passes deployment validation and fails at first
  use, because the producer is `@ApplicationScoped` and unconditional — CDI semantics reasoned by the landing analysis, not
  run under ArC (verify-at-outline)
- HYPOTHESIS: the stale-snapshot regression test passes vacuously if `clock.instant()` is read before `state.get()`, and
  would fail without the `latest != current` guard — reasoned by the landing analysis from
  `CachingDiscoveryResolverAdversarialTest.java`:185-206 and `CachingDiscoveryResolver.java`:164-185; not executed
  (verify-at-outline)
- HYPOTHESIS: the runtime identifier of the new INFO record prints as `TokenSheriffClient-4` or `TokenSheriffClient-004`;
  `ClientLogMessages.java`:85 declares `.identifier(4)` and the docs differ — not checked against `LogRecordModel`'s format
  (verify-at-outline)
- HYPOTHESIS: the 12.2 gap of deliverable 5 is a documentation gap and not a defect (nothing bound means no principal to
  protect) — the landing analysis rated it major; the orchestrator's re-read of `RefreshResultVerifier.java`:137,158 and
  `StoredToken.java`:174-182 supports the narrower reading above (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (the
  specs of PLAN-03 and PLAN-07 may have moved `TokenSheriffClientProducer`, `ClientConfigMapper` and `doc/client/*`), (3)
  whether the interrupt and unexpected-exception behaviours can be changed without altering the documented status
  vocabulary, and (4) that the deliverables stay at about six tasks or fewer; if the outline scopes above that, split at
  outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:127 — constructor (value rules, upper bounds)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:229 — `refresh` (unexpected exception, interrupt)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:198 — `refreshAsWinner`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:273 — `serveStaleOrFail`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryCacheStatus.java`:1 — `DiscoveryCacheStatus` (read; the cause class contract)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`:140 — `resolve` INFO log (changed only if the outline adds a quiet path)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshResultVerifier.java`:114 — `verify`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/StoredToken.java`:166 — `refreshed` (the duplicate constant-time compare)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:880 — the `verify` call site (changed only if the outline passes the stored subject)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java`:85 — INFO `DISCOVERY_CACHE_RECOVERED` (a new record sits beside it, only if the outline adds one)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`:178 — `validateDiscoveryCache`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`:209 — `mapDiscoveryCache`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`:171 — `cachingDiscoveryResolver`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`:193 — `validateDiscoveryCacheOnStartup`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientRuntimeConfig.java`:168 — the `discovery-cache` keys (Javadoc of the value rules)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc`:103 — the discovery cache section
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverTest.java`:59 — `CachingDiscoveryResolverTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverAdversarialTest.java`:1 — `CachingDiscoveryResolverAdversarialTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverStalePolicyTest.java`:1 — `CachingDiscoveryResolverStalePolicyTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshResultVerifierTest.java`:104 — the subjectless-access-token case
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolverTest.java`:1 — `DiscoveryResolverTest` (must stay green unedited)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryAdversarialTest.java`:1 — `DiscoveryAdversarialTest` (must stay green unedited)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientDiscoveryCacheTest.java`:1 — `TokenSheriffClientDiscoveryCacheTest`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/DiscoverySpecIT.java`:1 — `DiscoverySpecIT`
- OBSERVED: `doc/client/requirements.adoc`:448 — `CLIENT-29` (the defaults sentence)
- OBSERVED: `doc/client/requirements.adoc`:568 — `CLIENT-28` (the verifier rule)
- OBSERVED: `doc/client/threat-model.adoc`:245 — the discovery-cache row and the 12.2 row
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:290 — the logging paragraph and the injection sentence
- OBSERVED: `doc/client/specification/token-handling.adoc`:153 — the refresh-result verifier section
- OBSERVED: `doc/client/specification/test-strategy.adoc`:567 — the discovery-cache test rows
- OBSERVED: `doc/LogMessages.adoc`:212 — INFO `4` text (and any new record)
- OBSERVED: `doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc`:1 — `affects` metadata and scope sentence
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/` — new or split test classes for deliverables 2 and 3 (to be created; each at or under 500 lines) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/` — a startup-validation test class for deliverable 1 (to be created) (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff's PLAN-34 adopts the
  hardened verifier and cache from the first release that carries them.
- Depends on: PLAN-04, shipped (#907, `1be3867b`); the plan edits code and docs that landing created.
- `parallelization_scope` is 2. Overlaps are decided by the `next` admission gate from the declared surface, not here. Known
  shared surface with staged or running plans, to be confirmed at emit: PLAN-03 (`TokenSheriffClientProducer`,
  `ClientConfigMapper`, `ClientRuntimeConfig`, `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc`,
  `quarkus-config-doc.adoc`, `doc/LogMessages.adoc`) and PLAN-09 (`doc/LogMessages.adoc`, if either adds a record). PLAN-07 has
  shipped (#908, `80bc2b73`); it edited `doc/LogMessages.adoc` (the `173` row) and `doc/client/architecture.adoc`, so re-read both at
  HEAD. Requirement and log ids are next free at write time.
- Adjacent to: API Sheriff's PLAN-34 (adopts the verifier and the cache; waits for a released 0.9.10; not part of this spec),
  `RefreshFlow` and `DiscoveryResolver` (read; changed only on the outline's decision for the quiet resolve path).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-08-discovery-cache-and-verifier-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
