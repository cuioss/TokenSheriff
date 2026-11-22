# PLAN-06: JWKS Loader Gateway Needs

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-06-jwks-loader-gateway-needs.md` and is queued as one row file, `queue/PLAN-06.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Give `HttpJwksLoader` two opt-in behaviours that API Sheriff currently implements in a 700-line wrapper
because the library has no seam for them: a bounded, off-to-the-side JWKS refresh when a token names a
`kid` the loaded key set does not hold, which swaps the key set only when the triggering `kid` is present;
and a fast, backed-off retry of a failed initial load. Both are configured on `HttpJwksLoaderConfig`, both
default to off so that default behaviour is provably unchanged, and the unknown-`kid` path sits on an
unauthenticated request path, so it carries an explicit flood bound per issuer and an adversarial test
suite. Target release 0.9.10. API Sheriff's matching deletion of its wrapper is a separate API Sheriff plan
and is not part of this plan.

## Deliverables

Consumer context: API Sheriff installs its own `RetryingJwksLoader` through `IssuerConfigBuilder.jwksLoader`
and builds a fresh library loader for every retry and every refresh, because the library's re-fetch hook is
package-private. Its observable contract, which the TokenSheriff seam must be able to meet, is: a lookup of
an unknown `kid` never fetches on the request thread and never waits; it claims a per-issuer window with one
compare-and-set on a monotonic clock, at most one refresh is scheduled per window whatever the request rate
or the number of distinct `kid` values; the fetch runs off to the side while the current key set keeps
answering; the fetched key set is installed only if it holds the triggering `kid`; the replaced key set
stays valid for the key-rotation grace period; a failed or non-matching fetch changes nothing and does not
start the failed-load retry; a failed first load is retried after 1 second, doubling to a 30 second cap and
never above the refresh interval, one attempt in flight at a time.

1. **Opt-in configuration on `HttpJwksLoaderConfig`.** Add `unknownKidRefreshWindow` (a `Duration`, unset by
   default) and the initial-retry settings (see deliverable 3) to `HttpJwksLoaderConfig` and its builder.
   Verification criteria:
   - Unset by default: a config built without the new settings reports them as absent, and every existing
     `HttpJwksLoaderConfigTest` and `HttpJwksLoaderConfigWellKnownTest` case passes unmodified.
   - Validation at `build()`: a zero or negative window is refused with `IllegalArgumentException`; the
     window is bounded to a stated sane range (the consumer uses 5 to 3600 seconds; the bounds are chosen at
     outline, OD-3); combining the setting with `refreshIntervalSeconds(0)` and no caller-supplied executor
     is refused in the same fail-closed style as `maxStaleness` (a refresh the loader cannot schedule would
     leave the setting silently inert); `null` is refused.
   - `withIssuerIdentifier` copies the new fields (it re-invokes the private constructor field by field, so
     a forgotten field would silently drop the setting): a test builds with the setting, calls
     `withIssuerIdentifier`, and asserts the copy carries it.
   - Getter shape follows the existing `getMaxStaleness()` convention (`Optional`).
   - Javadoc states the availability and security trade-offs: the bound is per loader instance, one loader
     serves one issuer, and an attacker who presents an invented `kid` consumes the window, so a legitimate
     rotation is picked up at the latest one window later or at the next periodic refresh, whichever is
     first.

2. **Unknown-`kid` refresh in `HttpJwksLoader`.** When the setting is on and `getKeyInfo(kid)` cannot answer
   from the current key set nor from a still-valid retired set, the loader tries to claim the refresh window;
   a successful claim schedules one fetch on the loader's executor. Verification criteria, one per element:
   - Contract: `getKeyInfo` performs no I/O and never blocks on the caller thread; the triggering lookup is
     answered from the key sets held at that moment (the triggering token is refused, the next token naming
     the new `kid` succeeds once the swap happened). Whether a bounded blocking wait is offered instead is an
     operator decision (OD-1); this spec's default is non-blocking.
   - Claim: one compare-and-set on the next-allowed reading of a monotonic clock (`System.nanoTime`
     difference compared overflow-safely, not wall clock), made when the lookup arrives, so at most one
     fetch per window whatever the outcome, the request rate and the number of distinct `kid` values; the
     claim is made only while a key set is loaded (before a first successful load the retry of deliverable 3
     is the only recovery path).
   - Bounded per issuer: two loaders have independent windows; one issuer's flood does not consume
     another's window; a second in-flight guard ensures at most one unknown-`kid` fetch is in flight per
     loader, including against the periodic refresh cycle and the retry of deliverable 3 when the executor is
     caller-supplied and multi-threaded.
   - Off-to-the-side: the fetch uses the loader's current endpoint adapter, re-runs the per-fetch egress
     check (`egressCheckPassed`, the C1 DNS-rebinding re-check) exactly as a periodic cycle does, and builds a
     candidate key set without publishing it.
   - Swap only if the triggering `kid` is present: the candidate replaces the installed set only if
     `getKeyInfo(kid)` on the candidate is present; otherwise the candidate is discarded and the installed
     set, retired sets, `currentJwksContent`, `lastSuccessfulLoad`, status and endpoint are untouched.
   - A failed, timed-out, oversized, malformed or no-usable-key fetch changes nothing, and in particular is
     NOT counted as a failed refresh cycle: it must not increment `consecutiveRefreshFailures`, must not
     trigger re-discovery, must not feed the opt-in `maxStaleness` degradation, and must not downgrade
     `OK`. Otherwise a flood of invented `kid` values could degrade an issuer.
   - Grace and retention: a swap retires the replaced set with the configured grace period and the
     `maxRetiredKeySets` bound exactly as the periodic path does (the install path is shared, not copied),
     and repeated legitimate rotations never exceed the retired-set bound.
   - Lifecycle (ADR-0018): the claimed fetch captures the epoch and installs under the lifecycle lock only
     while live; a `close()` during the fetch installs nothing, publishes no status and leaves no task;
     a rejected executor (shut down) never surfaces an exception on the calling thread and releases the
     in-flight guard.
   - `getKeyIds()` contract points a to h (pinned by `JwksLoaderKeyIdsContractTest`, TokenSheriff #888) still
     hold: a returned snapshot is immutable and unchanged by a later swap, retired ids are excluded while
     `getKeyInfo` still resolves them within the grace period, status is `OK` only while a usable key set is
     installed, and a refresh yielding no usable key leaves the installed set in place.
   - Logging: a successful claim logs one record bounded by the window (the `kid` is not logged raw; if it
     is logged it passes `ErrorTextSanitizer`); a suppressed claim logs at most DEBUG; no log record is
     produced per lookup.
   - Default behaviour unchanged (see deliverable 4 for the proof tests): with the setting unset,
     `getKeyInfo` of an unknown `kid` issues zero HTTP requests and schedules nothing, and the documented
     contract "getKeyInfo only reads the installed key set; it never triggers loading" in
     `jwks/http/package-info.java` and `JwksLoader` is amended to state the opt-in exception.

3. **Initial-load retry backoff knob.** An opt-in setting (OD-2 fixes the shape: initial delay, multiplier
   and cap, or a single enable flag with documented constants) makes a loader whose initial load failed retry
   after a short, doubling delay instead of waiting for the fixed refresh interval (default 600 seconds,
   `DEFAULT_REFRESH_INTERVAL_IN_SECONDS`). Verification criteria:
   - Unset: a failed first load keeps today's cadence (no extra request inside the refresh interval); the
     0.9.9 behaviour "refresh is started also after a failed first load, at the fixed interval" is unchanged.
   - Set: the first retry occurs after the initial delay, each next one after the doubled delay up to the cap,
     never above the refresh interval; retries stop at the first installed key set, at a terminal `ERROR`
     (security rejection while no endpoint is installed, as in ADR-0017) and at `close()`.
   - Strictly sequential: the next attempt is scheduled only after the previous one ended, at most one fetch
     in flight per loader across retry, unknown-`kid` and periodic paths; an attempt that throws still
     schedules the next one (no silent end of the sequence on the executor thread).
   - Does not downgrade or flap status: status stays `UNDEFINED` while no key set is installed, never
     `LOADING`, `OK` only when keys are installed (ADR-0017 status contract).
   - Epoch-safe (ADR-0018): a retry of a closed generation installs nothing and schedules nothing.
   - Works in well-known mode (re-resolving a failed discovery, honouring the terminal rejections) and in
     direct mode.

4. **Adversarial and regression tests.** New test classes against a counting mock JWKS server
   (`JwksResolveDispatcher` and `DiscoveryGenerationDispatcher` precedent), each negative test paired with a
   positive control. Required cases:
   - Flood: N threads times M distinct invented `kid` values within one window produce at most one fetch
     (assert on the server-side request count, not on a loader flag); after the window elapses exactly one
     more fetch is possible.
   - Kid spray with a legitimate rotation interleaved: the spray cannot displace the installed key set, cannot
     make the loader leave `OK`, cannot start the failed-load retry; the legitimate `kid` is served after at
     most one window.
   - Concurrent triggers: a latch releases many threads at once; exactly one claim wins; no second fetch is
     started while one is in flight.
   - Swap only if present: server returns a key set without the triggering `kid` (installed state untouched,
     asserted field by field through the public accessors), with it (swapped, old set still resolvable within
     grace), with it but also changed material for existing `kid` values (documented behaviour asserted).
   - Failure injection: HTTP 5xx, timeout, body over the response-size bound, malformed JSON, empty key set,
     egress block after a DNS-rebind between the claim and the fetch; each leaves state unchanged and does not
     count as a failed cycle.
   - Lifecycle: `close()` during an in-flight unknown-`kid` fetch; re-initialisation after `close()`;
     executor shut down; caller-supplied single-thread executor that is busy (the request thread never
     blocks, measured by elapsed time of `getKeyInfo` under a blocked executor).
   - Clock: a test seam (package-private constructor taking a `LongSupplier` monotonic clock, the pattern API
     Sheriff's wrapper already uses) proves window expiry and overflow-safe comparison without sleeping.
   - Retry: backoff sequence asserted through the injected scheduler, no overlap with the periodic cycle,
     stop at first success.
   - Default-off proof: with neither setting configured, a counting server sees zero requests from any number
     of unknown-`kid` lookups, and the full set of existing tests in `HttpJwksLoader*Test`,
     `JwksLoaderKeyIdsContractTest`, `IssuerConfigCache*Test` passes without modification.
   - Existing `TokenSignatureValidator` behaviour on `KEY_NOT_FOUND` is unchanged (the triggering token is
     still refused with the same event).

5. **Quarkus property wiring (conditional on OD-4).** If the operator decides to follow the `max-staleness`
   precedent, expose both settings as per-issuer properties under `sheriff.token.issuers.<name>.jwks.http.`
   with the orphaned-configuration rule (ADR-0026: a key for an issuer without HTTP JWKS loading fails
   startup instead of being ignored). Verification criteria: key constants in `JwtPropertyKeys`; resolution
   in `IssuerConfigResolver` next to the `max-staleness-seconds` handling; unset leaves the produced loader
   configuration identical to today's; an orphaned key fails startup with the same message style as the
   existing one; the key table in `quarkus-config-doc.adoc` and the README are updated; a unit test per
   rule modelled on `IssuerConfigResolverMaxStalenessTest` and `JwtPropertyKeysTest`. If the operator decides
   Java-API only, this deliverable is dropped and the decision is recorded in the ADR of deliverable 6.

6. **Documentation, traceability and decision record.** Requirement entries in
   `doc/validation/requirements.adoc` under key management (ids next free at write time), the key-rotation
   description in `doc/validation/architecture.adoc` (the JWKS integration feature list), the
   `HttpJwksLoaderConfig` block in `token-sheriff-validation/doc/api-reference.adoc`, the configuration text
   in `token-sheriff-validation/doc/configuration/openid-discovery.adoc`, the threat-model rows in
   `doc/validation/threat-model.adoc` (row D1 "JWKS endpoint flooding" gains the unknown-`kid` mitigation and
   its tests; row E6 "pre-loaded keys only" is re-read and reworded so it stays true: keys still come only
   from the configured JWKS endpoint, now also on demand), log records in `doc/LogMessages.adoc` (identifiers
   next free at write time), `doc/developer/test-impl-mapping.adoc` rows for every new test class, and an ADR
   in `doc/adr/` (number next free at write time) recording the design, the flood bound, the
   non-counting of off-to-the-side failures and OD-1 to OD-5. Verification: Javadoc on every new public member
   with `@since`, thread-safety note, example; `check-javadocs-pre-commit` passes; doc links resolve.

**Operator decisions at outline** (recorded in the ADR of deliverable 6 before scoping on them):

- OD-1: unknown-`kid` lookup semantics: non-blocking schedule-only, as API Sheriff's wrapper does and this
  spec's default, versus a bounded blocking wait on the request thread (rejected by default because it puts
  I/O on an unauthenticated path).
- OD-2: retry knob shape: explicit initial delay, multiplier and cap, or one enable flag with fixed documented
  constants (the consumer uses 1 s doubling to 30 s).
- OD-3: bounds of `unknownKidRefreshWindow` (consumer range 5 to 3600 seconds; default window if a flag form
  is chosen).
- OD-4: Quarkus property wiring (follow the `max-staleness` precedent) or Java-API only.
- OD-5: whether a fetch that succeeds but yields no triggering `kid` refreshes `lastSuccessfulLoad` (it
  confirms the installed set is current) or leaves it untouched (this spec's conservative default).

## Standing Constraints

- Build and gate: Maven through the wrapper only (`./mvnw`). Before any commit that touches build inputs:
  `./mvnw -Ppre-commit clean verify` (it auto-fixes files in place; read `git status` afterwards and commit
  what it changed), then `./mvnw clean install`, then
  `./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am`.
  Never gate on `test-compile` or `test`: the `generators` classifier jar exists only from `package`.
- Pre-1.0 rules (AGENTS.md): no `@Deprecated`, no transitional comments, break APIs freely, design for the
  final post-1.0 shape. This plan is nonetheless additive: new members only.
- Everything new is opt-in and off by default. Default behaviour is proven unchanged by (a) a counting-server
  test showing zero extra requests with the settings unset and (b) the existing loader and cache test classes
  passing without edits. A change to default behaviour is a defect of this plan.
- Security first: the unknown-`kid` path is reachable by any unauthenticated caller who can present a token.
  The flood bound is per loader (per issuer), independent of request rate and of the number of distinct
  `kid` values; the bound does not depend on what is asked for. No I/O and no blocking on the calling thread
  by default. An unknown-`kid` fetch re-applies the egress guard and the response-size bound of the periodic
  path. Failures of this path never degrade status and never feed re-discovery or staleness counters.
- Concurrency: lifecycle and epoch rules of ADR-0018 apply to every new code path (install under the
  lifecycle lock, capture the epoch when claiming, skip once closed). No new lock is introduced without
  stating its order against `lifecycleLock`.
- Logging: `de.cuioss.tools.logging.CuiLogger`, `%s` only, `LogRecord` for INFO/WARN/ERROR with ranges INFO
  001-099, WARN 100-199, ERROR 200-299, every record documented in `doc/LogMessages.adoc`. New identifiers
  are the next free value at write time in `JWTValidationLogMessages`, checked against
  `LogMessageIdentifierUniquenessTest`; none is assumed here. Exception parameter first. Remote text (a
  `kid`, an issuer, an exception message) passes `ErrorTextSanitizer` before it is logged (ADR-0022,
  `LogRecordSanitizationGuardTest`).
- OpenRewrite markers are actual defects: never commit one. Narrow catches against declared throwers; the
  existing catch-all in the refresh cycle is a documented exception and is not copied to new code.
- Javadoc: every public and protected type and method, `@since`, thread-safety note, example, package-info
  for any new package.
- Tests: JUnit 5, AAA, CUI Test Generator, parameterised tests for three or more similar variants, test
  classes at or under 500 lines (guideline). Signal integrity per `doc/client/specification/test-strategy.adoc`
  § Signal integrity: assert on observable server-side effects (request counts), pair every negative test
  with a positive control, no sleeps where an injected clock or scheduler suffices.
  Coverage: 80 percent instruction and branch on new code under the opt-in `coverage` profile.
- Documentation: AsciiDoc, `xref:` syntax, requirement ids and ADR number are next free at write time; every
  new test class is added to `doc/developer/test-impl-mapping.adoc`.
- Native image: `HttpJwksLoader` is initialised at run time (`native-image.properties`); any new static
  holding a thread pool, scheduler or random source is initialised at run time too, and
  `NativeImageMetadataTest` keeps the shipped reflect-config pinned.
- Dependencies: none added without explicit approval.
- Sonar: AGENTS.md names the SonarCloud new-code gate in CI as the coverage authority. An open Sonar issue on
  this plan's code is fixed in a pushed commit or suppressed in code with a stated rationale; none is
  dismissed silently. The plan template's Sonar transition procedure is not documented in this repository and
  is not restated.
- Git: feature branch, PR against `main` via `gh`, merge through the queue only after CI is green and every
  reviewer comment is answered and resolved (CLAUDE.md § Git Workflow).

## Claim Labels

- OBSERVED: `HttpJwksLoader.getKeyInfo` consults only the current key set and the retired key sets inside the grace period, performs no I/O and has no unknown-`kid` trigger (finding `~:725-745`, actual `:725-745`) — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `getKeyInfo`
- OBSERVED: asserted absence, TokenSheriff has no public or package-private unknown-`kid` refresh entry; the only re-fetch entry is `performBackgroundRefresh()` at `:1024`, package-private and documented as a "test entry point"; the scheduled path is `performBackgroundRefresh(long)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `performBackgroundRefresh`
- OBSERVED: the documented contract says key retrieval never triggers loading, in `jwks/http/package-info.java:47-49` and in the `JwksLoader` type documentation ("`getKeyInfo` performs retrieval only"); this opt-in feature changes that sentence for opted-in loaders — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/package-info.java` § Key retrieval, and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoader.java` § type documentation
- OBSERVED: the retired-key deque exists at `:198` (`ConcurrentLinkedDeque<RetiredKeySet>`) and is filled and trimmed in `updateKeys` (`:907-970`, the finding said `~:937-946`); the install-and-publish-OK step is `installKeysAndPublishOk` (`:798`) which installs unconditionally and cannot express "swap only if a given `kid` is present" — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `updateKeys`, `installKeysAndPublishOk`
- OBSERVED: since 0.9.9 the refresh task is started after the initial load attempt also when it failed (`initialize`, `startBackgroundRefresh(startEpoch)` before the result check), at `scheduleAtFixedRate` with the fixed `refreshIntervalSeconds` as both initial delay and period; the default is `60 * 10` seconds (finding `HttpJwksLoaderConfig ~:65`, actual `:65`) — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `initialize`, `startBackgroundRefresh`, and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` § `DEFAULT_REFRESH_INTERVAL_IN_SECONDS`
- OBSERVED: `HttpJwksLoader` guards every key installation, status publication and task scheduling with one `lifecycleLock` and an `epoch` counter (ADR-0018), re-checks egress on each refresh cycle via `egressCheckPassed`, and counts a cycle that installs no keys through `recordFailedCycle`, which feeds `consecutiveRefreshFailures`, re-discovery and `degradeIfStale` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `performBackgroundRefresh`, `recordFailedCycle`, `degradeIfStale`
- OBSERVED: `HttpJwksLoaderConfig` is a hand-written builder (not Lombok); `withIssuerIdentifier` re-invokes the private constructor field by field, so a new field must be added there; the default refresh executor is a single-thread daemon pool per loader created in `build()` only when `refreshIntervalSeconds > 0` and no executor was supplied; `build()` refuses `maxStaleness` together with `refreshIntervalSeconds(0)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` § `withIssuerIdentifier`, `build`, `maxStaleness`
- OBSERVED: `JwksLoaderKeyIdsContractTest` pins the `getKeyIds()` contract points a to h through `JwksLoaderFactory.createHttpLoader` with a 1 second refresh interval and a 10 second grace period (commit `7520e165`, "pin JWKS key-id contract (#888)"); points f to h need a refresh — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java` § class documentation
- OBSERVED: API Sheriff's `RetryingJwksLoader` (738 lines, finding said about 700) implements the claim by `AtomicLong` compare-and-set on a monotonic reading (`onUnknownKeyId`), schedules the fetch on one shared single-thread scheduler (`runRefresh`), keeps `refreshInFlight`, builds a fresh delegate per refresh and installs it only if it holds the triggering `kid` (`settleRefresh`), retires the replaced delegate for the grace period, and retries a failed load with 1 second doubling to 30 seconds capped by the refresh interval — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/RetryingJwksLoader.java` § `onUnknownKeyId`, `runRefresh`, `backoffDelay`
- OBSERVED: API Sheriff's window is the per-issuer key `unknown_kid_refresh_window_seconds`, range 5 to 3600 seconds, default 30 seconds when unset (finding `TokenValidationRules ~:214`, actual `:215`) — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/config/validation/rule/TokenValidationRules.java` § `UNKNOWN_KID_REFRESH_WINDOW`, and `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/TokenValidatorProducer.java` § `toJwksLoader`
- OBSERVED: `HttpJwksLoader.getLoaderStatus` is a pure atomic read and `IssuerConfigCache.resolveConfig` re-admits a recovered issuer on a cache miss by re-checking the loader status, so a loader that recovers by background refresh becomes usable without a restart — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `getLoaderStatus`, and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java` § `resolveConfig`
- OBSERVED: precedent for per-issuer Quarkus wiring of an opt-in loader knob: `JwtPropertyKeys.ISSUERS.MAX_STALENESS_SECONDS` (`:460`), orphan check `validateNoOrphanedMaxStaleness` and builder application `builder.maxStaleness(...)` in `IssuerConfigResolver` (`:509`, `:529`, `:569`), documentation in `quarkus-config-doc.adoc:66` and the module README — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java` § `validateNoOrphanedMaxStaleness`
- OBSERVED: the threat model already has a row "JWKS endpoint flooding" (D1) citing the refresh interval, caching and `ResilientHttpAdapter` retry, and a row E6 stating "pre-loaded keys only — no dynamic file/DB lookup" — read at `doc/validation/threat-model.adoc` § rows D1 and E6
- OBSERVED: `TokenSignatureValidator.validateSignature` refuses an unknown `kid` with `KEY_NOT_FOUND` after a single `getKeyInfo` call — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenSignatureValidator.java` § `validateSignature` (line 137)
- HYPOTHESIS: the install path can be split into "build a candidate `JWKSKeyLoader` from a fetched `Jwks`" and "commit under the lifecycle lock", so the periodic path and the unknown-`kid` path share the commit step (retired-set bookkeeping, grace period, `maxRetiredKeySets`) — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `updateKeys` (verify-at-outline)
- HYPOTHESIS: the cui-http `ETagAwareHttpAdapter` returns the cached body on an HTTP 304, so an off-to-the-side fetch of an unchanged JWKS yields content equal to the installed set and therefore no new `kid`; and the adapter is safe to call from a second thread while a periodic cycle runs — confirm/refute at the adapter used by `HttpJwksLoader.resolveJWKSAdapter` and its tests `HttpJwksLoaderSchedulerTest` (verify-at-outline)
- HYPOTHESIS: a single-thread default executor serialises unknown-`kid` fetches with periodic cycles, while a caller-supplied multi-thread executor does not, so a shared in-flight guard is needed — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` § `build` (default executor) and `HttpJwksLoader.initJWKSLoader` (verify-at-outline)
- HYPOTHESIS: the retry knob must interlock with the existing fixed-rate task (two schedulers of the same loader), and the rejected-executor handling of `ensureBackgroundRefresh` is the model for a retry that cannot be scheduled — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `ensureBackgroundRefresh`, `handleUnexpectedInitializationFailure` (verify-at-outline)
- HYPOTHESIS: API Sheriff's wrapper can drop its refresh and retry for `http` sources once both knobs exist, and keeps only the retry for `file` sources; that is an API Sheriff matter and is not verified here — refute/confirm in the API Sheriff plan that adopts 0.9.10 (verify-at-outline)
- Verify-first clause: before scoping, the consuming phase reads `HttpJwksLoader` (initialisation, refresh cycle, install path, lifecycle lock and epoch), `HttpJwksLoaderConfig` (builder, `withIssuerIdentifier`, `build`), the cui-http adapter classes it composes, and the tests named in Expected Surface at HEAD of the 0.9.10 line, and confirms or refutes each HYPOTHESIS above; in particular it settles whether the install step can be shared, whether an off-to-the-side fetch can safely use the existing adapter concurrently with a periodic cycle, and whether recording a non-matching fetch as "not a failed cycle" is implementable without a second counter. A refutation loops back to re-scope this spec. OD-1 to OD-5 are asked at outline and recorded before scoping on them. If PLAN-03 changes `HttpJwksLoaderConfig` before this plan runs, the builder and `withIssuerIdentifier` are re-read at that state.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:725 — `getKeyInfo`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:798 — `installKeysAndPublishOk`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:907 — `updateKeys`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:971 — `startBackgroundRefresh`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:1024 — `performBackgroundRefresh`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:1152 — `recordFailedCycle`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:1390 — `close`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/package-info.java`:47 — `key-retrieval contract text`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoader.java`:51 — `JwksLoader` contract text
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:65 — `DEFAULT_REFRESH_INTERVAL_IN_SECONDS`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:338 — `withIssuerIdentifier`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:811 — `maxStaleness` builder method (precedent)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:850 — `build`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java`:517 — `JWKS_KEYS_STALE` (log record precedent; new records take the next free identifier)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/events/SecurityEventCounter.java`:66 — `EventType` (only if a new event is chosen)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenSignatureValidator.java`:137 — `getKeyInfo` call site (behaviour unchanged)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderSchedulerTest.java` — `scheduler precedent, must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderKeyRotationTest.java` — `must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderGracePeriodTest.java` — `must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderEgressRefreshTest.java` — `egress re-check precedent`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderLockFreeStatusTest.java` — `status contract precedent`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java` — `contract points a to h, must stay green`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/DiscoveryGenerationDispatcher.java` — `dispatcher precedent`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigTest.java` — `config tests extended`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheRecoveryTest.java` — `recovery behaviour, must stay unchanged`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeys.java`:460 — `MAX_STALENESS_SECONDS` (conditional, OD-4)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`:529 — `orphan check and builder application (conditional, OD-4)`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc`:66 — `key table (conditional, OD-4)`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`:200 — `max-staleness documentation precedent (conditional, OD-4)`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolverMaxStalenessTest.java` — `test precedent (conditional, OD-4)`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeysTest.java` — `key constants test (conditional, OD-4)`
- OBSERVED: `doc/validation/requirements.adoc`:226 — `VALIDATION-4 key management (new entries, ids next free at write time)`
- OBSERVED: `doc/validation/architecture.adoc`:95 — `JWKS integration feature list`
- OBSERVED: `doc/validation/threat-model.adoc`:211 — `row D1 JWKS endpoint flooding (row E6 at :282 re-read)`
- OBSERVED: `token-sheriff-validation/doc/api-reference.adoc`:112 — `HttpJwksLoaderConfig` block
- OBSERVED: `token-sheriff-validation/doc/configuration/openid-discovery.adoc`:1 — `refresh and staleness configuration text`
- OBSERVED: `doc/LogMessages.adoc`:1 — `log reference`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:1 — `test-to-implementation mapping`
- OBSERVED: `doc/adr/0017-A_JWKS_loader_reports_OK_only_with_usable_keys_and_retries_only_transient_discovery_failures.adoc`:1 — `status contract the new paths must respect`
- OBSERVED: `doc/adr/0018-A_JWKS_loader_fences_stale_work_with_one_lifecycle_lock_and_a_generation_epoch.adoc`:1 — `lifecycle contract the new paths must respect`
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderUnknownKidRefreshTest.java` — `new functional test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderUnknownKidAttackTest.java` — `new adversarial test class (flood, kid spray, concurrent triggers), to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderInitialRetryTest.java` — `new retry test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderDefaultBehaviourUnchangedTest.java` — `new default-off proof class, to be created` (verify-at-outline)
- HYPOTHESIS: `doc/adr/` — `one new ADR file, number next free at write time` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none. Within the plan: deliverable 1 precedes 2 and 3; deliverable 4 is written alongside 2 and 3 and gates them; deliverable 5 (if kept) follows 1; deliverable 6 closes the plan.
- Overlaps with: PLAN-05 (both touch `token-sheriff-validation`; shared documents `doc/validation/architecture.adoc`, `doc/validation/requirements.adoc`, `doc/validation/threat-model.adoc`, `token-sheriff-validation/doc/api-reference.adoc`, `doc/developer/test-impl-mapping.adoc`, `doc/LogMessages.adoc`, `JWTValidationLogMessages` and `SecurityEventCounter`: the second plan to run takes the next free identifiers at its own write time). PLAN-03 (touches `token-sheriff-validation` `commons/transport/EgressPolicy` and, per the findings, `HttpJwksLoaderConfigBuilder.allowedEgressHost`, so `HttpJwksLoaderConfig.java` is a shared file: whichever plan runs second re-reads the builder and `withIssuerIdentifier` before editing). PLAN-01 to PLAN-04 are in `token-sheriff-client` and share no file with this plan. PLAN-07 shares `token-sheriff-validation-quarkus` only if deliverable 5 is kept, and then no file (PLAN-07 touches the `producer` package; deliverable 5 touches `config`).
- Adjacent to: `IssuerConfigCache` recovery (unchanged; a loader that recovers by retry or refresh is re-admitted by the existing mechanism); `HttpWellKnownResolver` discovery (re-discovery counters are deliberately not fed by the new path); the SSRF `EgressPolicy` (re-used, not changed here). API Sheriff's `RetryingJwksLoader` and its `unknown_kid_refresh_window_seconds` key are deleted or reduced in a separate API Sheriff plan (AS PLAN-34) that waits for a released 0.9.10 and is not part of this spec.
- Release and sequencing: target release 0.9.10 (repository at 0.9.10-SNAPSHOT). `parallelization_scope` is 1: plans in this epic run sequentially. Recommended order within WS-02 is PLAN-05, PLAN-06, PLAN-07; no hard ordering constraint exists between them. This is the highest-risk plan of the three (an unauthenticated trigger path): it is reviewed with the security persona and its adversarial suite is a merge precondition.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-06-jwks-loader-gateway-needs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
