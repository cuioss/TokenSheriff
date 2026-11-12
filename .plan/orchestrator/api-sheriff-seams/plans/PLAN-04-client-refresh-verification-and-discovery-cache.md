# PLAN-04: Client refresh verification and discovery cache

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-04-client-refresh-verification-and-discovery-cache.md` and is queued as one row file, `queue/PLAN-04.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Close two gaps that make a gateway re-implement, or skip, client-engine behaviour. First, the refreshed ID
token check of `TokenLifecycleManager` (pipeline validation, OIDC Core 12.2 `iss`/`sub` consistency with the
refreshed access token, bound-subject match) is private to the lifecycle manager, so a caller that drives
`RefreshFlow` itself stores the refreshed ID token unverified. Publish that check as a verifier usable
without the lifecycle manager, and keep the lifecycle manager on top of it. Second, `DiscoveryResolver`
fetches on every call and keeps no status, so a caller that caches its result for the process lifetime never
sees a changed endpoint. Add an opt-in caching, refreshing resolver with a TTL and a status. Both are
additive: `RefreshFlow`, `TokenLifecycleManager`'s observable behaviour and `DiscoveryResolver.resolve()` do
not change for existing callers.

## Deliverables

1. **Public refresh-result verifier.** A public type in `token-sheriff-client` verifies a refresh outcome
   without the lifecycle manager. It takes the `RotationResult` (whose access token the flow already
   validated), the `IdTokenValidationBridge`, and the bound subject (nullable), and returns the verified
   raw ID token to carry forward, or `null` when the authorization server returned none. It enforces, in
   this order: pipeline validation of the refreshed ID token; OIDC Core 12.2 `iss` equality with the
   refreshed access token and `sub` equality when the access token carries a subject; and, when a subject is
   bound, equality of the refreshed principal with the bound subject using a constant-time comparison. The
   method signature and exception types are chosen at outline; the behaviours below are fixed. Because a
   caller of this verifier must fail closed on a verifier refusal, each criterion is stated for the edge set a
   caller sees:
   - ID token absent (`null`) and blank: result `null`, no pipeline call, no refusal.
   - ID token present and valid, consistent: result is the raw token, unchanged.
   - ID token fails the pipeline (bad signature, wrong `iss`, wrong `aud`, expired, `alg=none`): the
     `TokenValidationException` reaches the caller (not swallowed, not wrapped without its cause).
   - ID token valid but `iss` differs from the access token's: refused, with the existing WARN record
     emitted once.
   - ID token valid, access token carries a subject and the ID token's differs: refused.
   - ID token valid, access token carries no subject: not refused on 12.2 `sub` (the existing rule).
   - Bound subject present and refreshed subject differs or is absent: refused.
   - Bound subject absent (`null`): the bound-subject step is skipped and the refreshed subject is adopted
     by the caller (the existing `StoredToken` rule).
   - Rotated and not-rotated results are both accepted as input; the verifier never touches the refresh
     token or any store.
2. **Verifier refusals carry the redemption state.** A refusal raised by the verifier is classified by
   `RefreshFlow.classify` as redeemed (with the rotated successor when the exchange rotated), not as
   pre-redemption. Today `classify` answers `PRE_REDEMPTION` for any throwable the caller's own ID-token
   checks raise, which for a gateway that verifies itself would keep a session holding a refresh token the
   authorization server has already burned. Verification: for every refusal row of deliverable 1, a test
   feeds the thrown exception to `RefreshFlow.classify` and asserts the `REDEEMED` kind with
   `RefreshRedemption.rotated(successor)` when the result was rotated and `RefreshRedemption.notRotated()`
   when it was not; the existing `RedeemedValidationRefusalException` and `RedeemedRefreshFailure` seam is
   the carrier unless the outline shows a gap.
3. **`TokenLifecycleManager` delegates and behaves identically.** The lifecycle manager's private
   `verifiedRefreshedIdToken` / `consistentWithAccessToken` are replaced by the published verifier, and
   nothing the manager's callers observe changes. Enumerated consumer-visible set, one criterion each, all
   proven by the existing tests passing without edited assertions: a refused ID token on a rotated exchange
   quarantines (revokes the successor when a revocation endpoint is advertised, clears store and family) and
   rethrows the original exception; on a non-rotated exchange it revokes nothing and leaves the store and
   family untouched; the check runs before the family advances; the verified ID token reaches
   `applyRefresh` as the stored ID token; a missing ID token stores none; the WARN record
   `REFRESHED_ID_TOKEN_INCONSISTENT` is emitted exactly as before. Relevant existing tests:
   `RefreshAdversarialTest`, `RefreshIdTokenValidationQuarantineTest`, `RefreshPostRedemptionQuarantineTest`,
   `RefreshFamilyReseedTest`, `TokenLifecycleManagerApplyRefreshTest`, `RefreshProductionPathSpecIT`.
4. **Caching, refreshing discovery resolver (opt-in).** A new public class in the discovery package wraps a
   `DiscoveryResolver` (or the same configuration) and returns provider metadata from a cache with a
   time-to-live, refreshes after expiry, and reports a status. `DiscoveryResolver.resolve()` itself is not
   changed. Verification:
   - A call inside the TTL performs no network request; the first call after the TTL performs one; the time
     source is injectable (a `Clock` or supplier) so no test sleeps.
   - Concurrent callers on an expired entry cause exactly one refresh (single-flight), and the others
     receive the refreshed value or the stale one per the stale policy, never a partially built value.
   - A refresh that fails (transport error, issuer mismatch, a non-TLS endpoint, an oversize document, a
     non-JSON content type) never replaces the cached value with an unvalidated one: every check
     `DiscoveryResolver.resolve()` performs still runs on a refresh and a refused document is dropped.
   - The stale policy is stated and tested (see Operator decisions): what the resolver returns while a
     refresh keeps failing, and for how long, with the failure visible in the status.
   - The status reports at least: not attempted, resolved, failed, with the time of the last success and
     the cause class of the last failure (never the response body or any attacker-controlled text); the
     status is readable without triggering a fetch. It aligns with the existing `LoaderStatus` /
     `LoadingStatusProvider` of the validation module's transport package, or states why not.
   - A changed endpoint in the refreshed document is visible to the next caller after the refresh, which is
     the behaviour the consumer's process-lifetime cache lacks.
   - The cached `ProviderMetadata` is a mutable object with public fields; the resolver either hands out an
     immutable view or a defensive copy, or documents and tests that the returned instance must not be
     mutated, as settled at outline.
5. **Documentation.** The verifier and the cache are documented. Verification: one requirement or an
   amendment per area with the next free `CLIENT-N` at write time (CLIENT-16 and CLIENT-17 own the ID-token
   and lifecycle text today); `doc/client/specification/token-handling.adoc` (the refreshed-ID-token
   paragraph and the §12.2 note), `doc/client/threat-model.adoc` (the 12.2 row) and
   `doc/client/specification/retrieval-flow.adoc` (discovery) match the code;
   `doc/client/specification/test-strategy.adoc` names the new tests; a new LogRecord, if any, is the next
   free identifier in its range at write time and is added to `doc/LogMessages.adoc`.

Done when: all criteria pass, the refresh-path coverage gate (`RefreshPathCoverageGuardTest`, per-class
floors on `RefreshFlow`, `RotationResult`, `RefreshTokenFamily`, `RefreshScheduler`, `InMemoryTokenStore`)
still passes, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar
open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason
(`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **Discovery cache TTL default.** The consumer has no TTL (process lifetime). Candidate defaults and the
  presence of a minimum refresh interval, jitter and a hard maximum are the operator's call; the spec's
  proposal is that the resolver has no default of its own beyond what the operator names, and that the
  existing non-caching `DiscoveryResolver` stays the default bean.
- **Stale policy on a failing refresh.** Serve the last good metadata for a bounded grace period (and how
  long), serve it indefinitely with the failure visible in the status, or fail closed after the TTL. Serving
  stale metadata keeps a gateway up through an IdP outage but also keeps a revoked or rotated endpoint in
  use; the operator chooses.
- **Is the verifier public API from 0.9.10, and in which package.** The finding wants it public. The
  operator confirms, and chooses between `token` (next to `IdTokenValidationBridge` and `RotationResult`)
  and `lifecycle` (next to `TokenLifecycleManager`).
- **Whether the caching resolver is wired into `TokenSheriffClientProducer` as configuration**
  (`sheriff.client.*`), or published as a class only. The consumer builds its own resolver; wiring adds
  configuration surface that must follow the orphan-configuration rule (ADR-0026).
- **The unverified related lead (see Claim Labels, last HYPOTHESIS)** is not a deliverable. The operator
  decides whether the outline may open it as a follow-up.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments.
- **Default behaviour stays unchanged and is proven so.** `DiscoveryResolver.resolve()` keeps fetching on
  every call (`DiscoveryResolverTest`, `DiscoveryAdversarialTest` and `ProviderMetadataTest` pass without
  edits); the produced `DiscoveryResolver` bean stays non-caching; `TokenLifecycleManager` keeps its
  observable disposition (deliverable 3's enumerated set); `RefreshFlow.refresh` gains no behaviour.
- **Overridability (AGENTS.md "Preferred overloads", ADR-0006).** `RefreshFlow` and `RevocationClient` are
  non-final and subclassed by test doubles; `TokenLifecycleManager` routes the rotation through a public,
  overridable `applyRefresh` (ADR-0006). The refactor in deliverable 3 changes only the private helper
  `TokenLifecycleManager` calls, never the overridable methods it dispatches through.
- **Post-redemption safety (ADR-0005).** Every verifier refusal happens after the authorization server has
  answered `2xx`; the session disposition on refusal is the rotated-vs-not rule of the existing text in
  `doc/client/specification/token-handling.adoc` and the classification of deliverable 2. No refactor may
  reorder the check relative to the family advance (the check runs first, so a refusal leaves no
  half-applied rotation).
- **Security.** Subject comparison is constant-time (the `SubBindingValidator` / `StoredToken.subjectsMatch`
  idiom). No token, subject, refresh token or response body is logged unsanitised; attacker-controlled text
  goes through `LogSanitizer`. Logging follows `AGENTS.md` (`CuiLogger`, `%s` only, `LogRecord` for
  INFO/WARN/ERROR, new identifiers the next free in range at write time). A discovery refresh must not log
  at INFO on every refresh cycle (the existing `DISCOVERY_RESOLVED` is INFO per resolve); the refresh log
  level is decided at outline so a short TTL does not flood the log. Adversarial tests are required:
  subject-substitution (the refreshed ID token names another user), issuer-substitution, `alg=none`,
  expired, unsigned and wrong-audience ID tokens, an access token with no subject while a subject is bound;
  for discovery, a refresh that changes `issuer`, downgrades an endpoint to `http`, exceeds the size bound
  and answers a wrong media type, and a refresh stampede.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants,
  `@GeneratorsSource` and the existing `RefreshTestSupport` / `TokenDispatcher` fixtures, injected time
  (never sleeps), new or grown test classes at or under 500 lines.
- No new dependency without operator approval. Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`, `AGENTS.md` names the SonarCloud
  new-code gate the coverage authority): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.

## Claim Labels

- OBSERVED: the refreshed-ID-token check is private to the lifecycle manager: `verifiedRefreshedIdToken`
  (`private static`, :1272) validates through `IdTokenValidationBridge.validateRefreshedIdToken`, then
  `consistentWithAccessToken` (:1287) compares `iss` always and `sub` only when the access token carries a
  subject, and a failure raises `TokenValidationException` or `IllegalStateException` plus the WARN
  `REFRESHED_ID_TOKEN_INCONSISTENT`; the findings' "~:1272-1294" is exact — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` §
  `verifiedRefreshedIdToken`, `consistentWithAccessToken`
- OBSERVED: the manager runs the check before the family advances and quarantines a rotated exchange on a
  refusal (call site at :873-881); the bound-subject rule is applied later, in the store transform
  (`applyRefresh(..., refreshedSubject, refreshedIdToken)` :903-906 → `StoredToken.refreshed`, which refuses
  a bound subject that the refreshed subject does not match and refuses an absent refreshed subject, using a
  constant-time comparison) — read at `TokenLifecycleManager.java` § `doRefresh` (:836-935) and
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/StoredToken.java` §
  `refreshed`, `subjectsMatch` (:168-194). The findings cite "~:901" for the bound-sub rule; the rule itself
  is at `StoredToken`, :901-903 only computes the refreshed subject
- OBSERVED: `RefreshFlow.refresh` validates only the access token (`validationBridge.validateAccessToken`)
  and returns the ID token raw in `RotationResult`; its constructors take one `TokenValidationBridge` and no
  ID-token bridge — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java` § `refresh`
  (:196-250) and the constructors (:142-170). The findings' "~:196" is the method
- OBSERVED: `IdTokenValidationBridge.validateRefreshedIdToken(String)` is public and runs the pipeline for
  an ID token with no `nonce` check; its Javadoc states the caller enforces the 12.2 consistency — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/IdTokenValidationBridge.java` §
  `validateRefreshedIdToken` (:183)
- OBSERVED: `RefreshFlow.classify` answers `PRE_REDEMPTION` for any throwable that is not one of the named
  types, and its Javadoc states a caller's own "binding or ID-token checks" are outside its domain and
  classify as pre-redemption; `RedeemedValidationRefusalException(TokenValidationException, RefreshRedemption)`
  and the interface `RedeemedRefreshFailure` carry redemption state — read at `RefreshFlow.java` § `classify`
  (:265-317) and
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RedeemedValidationRefusalException.java`
  § constructor (:67)
- OBSERVED: the consumer stores the refreshed ID token unverified: `TokenRefreshCoordinator.rotate` takes
  `rotation.idToken()` and keeps it when non-blank (`rotatedIdToken` at :817, stored at :823), and keeps
  `sub` from the previous record (:824) with no comparison against the rotation; the refresh seam is
  `scopedFlows.refresh(...)`, which builds a `RefreshFlow` from the access-token bridge only — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/refresh/TokenRefreshCoordinator.java` § `rotate`
  and `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/ScopedEngineFlows.java` § `refresh`.
  Asserted absence verified by search: no `validateRefreshedIdToken` call exists under `bff/refresh/` or
  `bff/login/` of the consumer (the only calls are `BffRuntimeProducer.java`:728 for the user-info projection
  and its comment at :748)
- OBSERVED: the consumer validates the stored ID token only lazily, on a user-info read:
  `idBridge.validateRefreshedIdToken(sessionRecord.idToken())` at `BffRuntimeProducer.java`:728 — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/quarkus/BffRuntimeProducer.java` § the
  `UserInfoEndpoint` construction. The findings' "~:728" is exact
- OBSERVED: `DiscoveryResolver.resolve()` (:127) fetches, parses and validates on every call, holds no cache
  and no status, and logs `DISCOVERY_RESOLVED` at INFO per resolve; no class extends it — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java` §
  `resolve`; subclass search of the repository and the consumer found none
- OBSERVED: `LoaderStatus` (`OK`, `ERROR`, `LOADING`, `UNDEFINED`) and `LoadingStatusProvider` live in the
  validation module's `commons.transport` package and serve the JWKS and well-known loaders; the client has
  no equivalent for discovery — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/LoaderStatus.java` and
  `.../LoadingStatusProvider.java`. Asserted absence verified by search of `token-sheriff-client/src/main` for
  `LoaderStatus` / `LoadingStatusProvider` (none)
- OBSERVED: the consumer caches the discovery result for the process lifetime without a TTL:
  `memoize(discoveryStatus.recording(() -> new DiscoveryResolver(clientConfiguration).resolve()))` caches a
  resolved value forever and retries only after a failure; `DiscoveryStatus` (156 lines) is a three-state
  tracker (`NOT_ATTEMPTED`, `RESOLVED`, `FAILED`) — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/quarkus/BffRuntimeProducer.java` § `memoize` (the
  construction at ~:541 and the helper at ~:1528) and
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/runtime/DiscoveryStatus.java` § `State`,
  `recording`
- OBSERVED: `ProviderMetadata` is a `@CompiledJson` class with public non-final fields (`issuer`,
  `authorizationEndpoint`, `tokenEndpoint`, ...), so a cached instance handed to several callers is mutable
  — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/ProviderMetadata.java`
  § field declarations (:54-82)
- OBSERVED: `TokenSheriffClientProducer.discoveryResolver` produces a plain `new DiscoveryResolver(...)` bean
  — read at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`
  § `discoveryResolver` (:140)
- HYPOTHESIS: a caller holding no bound subject yet (`null`) and a refreshed access token without a subject
  adopts no principal at all, so the ID token's `sub` is then compared with nothing — confirm/refute at
  `StoredToken.refreshed` (:168-182: `refreshedSubject != null ? refreshedSubject : subject`) and
  `consistentWithAccessToken` (verify-at-outline); if true, the verifier should compare the ID token `sub`
  with the bound subject directly, which is what OIDC Core 12.2 states
- HYPOTHESIS: a verifier refusal for the not-rotated case must leave the presented refresh token usable
  (existing behaviour) — confirm at `RefreshIdTokenValidationQuarantineTest` and
  `RefreshAdversarialTest` § the non-rotated cases (verify-at-outline)
- HYPOTHESIS: the unverified lead of the findings — TokenSheriff 0.9.9 applies a refresh as a compare-and-set
  on the base refresh token, while the consumer's persist is presence-only (`ServerSessionBinding.persist` →
  `SessionStore.replaceIfPresent`), so a stale grant could overwrite a re-login that happened mid-refresh —
  is NOT a deliverable and is unverified; confirm/refute at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/session/SessionStore.java` § `replaceIfPresent`
  and `.../bff/session/InMemorySessionStore.java` § `replaceIfPresent`, and at
  `TokenLifecycleManager.applyRefresh` (verify-at-outline). `replaceIfPresent` exists in both consumer files
  (confirmed by search); whether a re-login reuses the session id was not read
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every caller of the
  private helpers being replaced and every test that reaches them through `TokenLifecycleManager`, (3) whether
  the verifier can carry redemption state through `RedeemedValidationRefusalException` without a new
  exception type, (4) where each new public type sits relative to `ClientArchitectureTest`, and (5) every
  line number above against HEAD. A refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:1272 — `verifiedRefreshedIdToken`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:1287 — `consistentWithAccessToken`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:873 — the `verifiedRefreshedIdToken` call site in `doRefresh`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/StoredToken.java`:168 — `refreshed`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/IdTokenValidationBridge.java`:183 — `validateRefreshedIdToken`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/RotationResult.java`:74 — `RotationResult`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`:302 — `classify` (documentation and, if the outline finds a gap, classification of verifier refusals)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RedeemedValidationRefusalException.java`:67 — constructor (the redemption-carrying refusal)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RedeemedRefreshFailure.java`:1 — `RedeemedRefreshFailure`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`:127 — `resolve` (unchanged; wrapped by the new class)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/ProviderMetadata.java`:48 — `ProviderMetadata`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java`:44 — `DISCOVERY_RESOLVED` (a refresh log record would sit beside it)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/LoaderStatus.java`:1 — `LoaderStatus` (read; status vocabulary to align with)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`:140 — `discoveryResolver` (changed only if the operator wires the cache as configuration)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshAdversarialTest.java`:1 — `RefreshAdversarialTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshIdTokenValidationQuarantineTest.java`:1 — `RefreshIdTokenValidationQuarantineTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshPostRedemptionQuarantineTest.java`:1 — `RefreshPostRedemptionQuarantineTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshFamilyReseedTest.java`:1 — `RefreshFamilyReseedTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshTestSupport.java`:1 — `RefreshTestSupport` (fixtures)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerApplyRefreshTest.java`:1 — `TokenLifecycleManagerApplyRefreshTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowTest.java`:1 — `RefreshFlowTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RedeemedRefreshFailureTest.java`:1 — `RedeemedRefreshFailureTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolverTest.java`:1 — `DiscoveryResolverTest` (must stay green unedited)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryAdversarialTest.java`:1 — `DiscoveryAdversarialTest` (must stay green unedited)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/RefreshPathCoverageGuardTest.java`:1 — `RefreshPathCoverageGuardTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java`:1 — `ClientArchitectureTest`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/RefreshProductionPathSpecIT.java`:1 — `RefreshProductionPathSpecIT`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/DiscoverySpecIT.java`:1 — `DiscoverySpecIT`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/` or `.../lifecycle/` — new public verifier class (new file; package decided at outline) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/` — new caching resolver class and its status type (new files) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/` — new test classes for the verifier edge set and the classification of verifier refusals (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/` — new test class for the caching resolver (single-flight, TTL boundary, stale policy, failed refresh) (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientRuntimeConfig.java`:124 — a discovery TTL property would be added beside the existing `sheriff.client.*` keys, only if the operator wires the cache as configuration (verify-at-outline)
- OBSERVED: `doc/client/specification/token-handling.adoc`:139 — the refreshed-ID-token paragraph and the 12.2 note
- OBSERVED: `doc/client/threat-model.adoc`:325 — the OIDC Core 12.2 row
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:43 — the discovery and request parameter text
- OBSERVED: `doc/client/requirements.adoc`:415 — `CLIENT-16` (ID-token validation; the verifier amends or extends it)
- OBSERVED: `doc/client/specification/test-strategy.adoc`:1 — `Client Specification — Test Strategy`
- OBSERVED: `doc/LogMessages.adoc`:1 — only if a new LogRecord is added

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`).
- `parallelization_scope` is 1: plans of this epic run sequentially, one at a time, in ordinal order. This
  plan runs last of the four client plans.
- Depends on: none by code (PLAN-01 to PLAN-03 are order only).
- Overlaps with: PLAN-02 and PLAN-03, all three in `token-sheriff-client`. PLAN-04 and PLAN-02 both touch
  `RefreshFlow` (PLAN-02 adds the per-call scope overload and moves the scope-reconciliation baseline;
  this plan touches `classify` and its documentation and may add nothing else there), and both share
  `RefreshFlowTest`, `TokenLifecycleManagerApplyRefreshTest` and the refresh-path coverage gate, so the plan
  that runs second rebases on the first's `RefreshFlow`. PLAN-03 shares `doc/client/requirements.adoc`,
  `doc/client/specification/test-strategy.adoc`, and — only if the revoke-if-supported method is adopted
  inside `TokenLifecycleManager` — that class. Requirement and log ids are next free at write time, never
  pre-assigned.
- Adjacent to: `UserInfoClient` and the user-info path (read; not changed), `TokenValidator.createIdToken`
  (consumed unchanged; the validation-side plans of the epic leave it untouched), `TokenSheriffClientProducer`
  (changed only on the operator's wiring decision), `ClientCredentialsFlow` (no ID token).
- API Sheriff's matching changes (calling the verifier in `TokenRefreshCoordinator.rotate`, replacing its
  process-lifetime discovery memoization and `DiscoveryStatus`) are its own plan, AS PLAN-34, which waits for
  a released 0.9.10. They are NOT part of this spec.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-04-client-refresh-verification-and-discovery-cache.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
