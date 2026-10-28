# PLAN-01: JWKS loader lifecycle and status truthfulness

epic: quality-report
workstream: WS-01

## Objective

Make `HttpJwksLoader` report its status truthfully and recover on its own, and make
`TokenValidator.close()` release loader resources. An issuer whose first JWKS load fails must
recover once the IdP comes back (V-3); a well-known discovery failure at startup must be retried
rather than fatal (V-4); a key set with zero usable keys must not report `OK` (V-8); concurrent
`initJWKSLoader` calls must not start duplicate refresh tasks (V-16); and closing the validator
must stop background refresh threads (V-5). Closes V-3, V-4, V-5, V-8, V-16.

## Deliverables

1. **V-3** — background refresh that installs a usable key set transitions status to `OK`;
   `IssuerConfigCache` then picks up the recovered issuer. Recovery test with a real
   `HttpJwksLoader` against MockWebServer (first load fails → IdP recovers → token validates).
2. **V-8** — first load sets `OK` only when usable keys were installed (`updateKeys` reports it);
   otherwise status stays `UNDEFINED`/`ERROR`. Test: JWKS with only unsupported keys → not `OK`.
3. **V-4** — when background refresh is enabled, a failed well-known adapter resolution schedules
   retries (status `UNDEFINED`, not terminal `ERROR`), mirroring direct mode. Test: discovery
   endpoint down at init, up later → loader reaches `OK`.
4. **V-16** — win the `initFuture` CAS before starting async work (or cancel any previous refresh
   task via `getAndSet`); test concurrent init starts exactly one refresh task.
5. **V-5** — `TokenValidator.close()` closes every `AutoCloseable` `JwksLoader` of its issuer
   configs; test asserts `isBackgroundRefreshActive()` turns false after close.

Five deliverables on one class family — tightly coupled (one status-ownership refactor); kept unsplit.

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Status transitions must stay monotone-safe for readers: never report `OK` without usable keys.

## Claim Labels

- OBSERVED: `status.set(...)` occurs only in `initJWKSLoader`, `handleAdapterResolutionFailure` and `close`; `performBackgroundRefresh` → `updateKeys` never sets `OK` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `updateKeys`
- OBSERVED: `IssuerConfigCache` only admits issuers with `LoaderStatus.OK` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java` § `getLoaderStatus` check
- OBSERVED: `IssuerConfigCacheRecoveryTest` uses a `StatusControlledLoader` stub, so no test exercises real-loader recovery — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheRecoveryTest.java`
- OBSERVED: `initJWKSLoader` sets `OK` unconditionally after `updateKeys`, whose no-keys guard returns early without changing status — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `initJWKSLoader`
- OBSERVED: `supplyAsync` runs before `initFuture.compareAndSet`; the losing supplier still calls `startBackgroundRefresh` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `initJWKSLoader`
- OBSERVED: `TokenValidator.close()` shuts down only the pipeline and DPoP replay protection; no production caller of `HttpJwksLoader.close()` exists — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § `close`
- HYPOTHESIS: closing loaders from `TokenValidator.close()` is safe because loaders are not shared between validator instances — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java` § `createJwksLoaderIfNeeded` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java` (verify-at-outline — may need no change once status is truthful)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderFailureTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderWellKnownAsyncTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderAsyncInitializationTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheRecoveryTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorTest.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-07 and PLAN-13 (`HttpJwksLoader.java`) — they run after this plan; PLAN-03 (`TokenValidator.java`, single file)
- Adjacent to: `HttpJwksLoaderConfig.java` (V-7 timeouts) — owned by PLAN-07, untouched here

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-01-jwks-loader-lifecycle.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
