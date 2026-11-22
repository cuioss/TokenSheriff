# Landing Analysis: PLAN-04 — Client refresh verification and discovery cache

epic: api-sheriff-seams
workstream: WS-01
pr: #907 (https://github.com/cuioss/TokenSheriff/pull/907), merged as 1be3867b395e231afa8d27ce44daa308cbab1ad3

> Landing record for one shipped plan. Lives at `landings/PLAN-04.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `client-refresh-verification-discovery-cache-010.md` (lane `plan-marshall`, plan id
`client-refresh-verification-discovery-cache`), plus nine candidate-lesson messages (`-001` to `-009`). PR state read through
`ci pr view` (`state: merged`, merge commit as above, base `main`). `inbox landing-check`: `complete: false` (missing
`unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and `sonar_open_new_issues`, all
`unknown` in the landing); `surface_delta` is `unmeasured` (the landing supplied neither the declared nor the realized surface),
so the declared-vs-realized comparison below was made by hand from the 29-file merge diff (+3248/-78).

## Deliverable Fidelity vs Spec

The spec lists five deliverables; the landing reports `deliverables_total=6`, `deliverables_done=6`, which the spec does not bear
out (the plan's own numbering; the outline was not read). Every one of the spec's five traces to code and a test, with the gaps
below. The operator decisions the spec left to outline all shipped and the code agrees with the docs on each, except where a row
says otherwise.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Public refresh-result verifier | shipped-modified (a third package choice) | `RefreshResultVerifier` is a `public final` utility in package `flow`, with `static String verify(RotationResult, IdTokenValidationBridge, @Nullable String)` (`flow/RefreshResultVerifier.java:114-143`, re-read). The three steps run in the spec's order (pipeline `:126`, §12.2 `:130`, bound subject `:137`). Every edge row of the spec is met: absent or blank ID token gives `null` with no pipeline call (`:121-122,142`); a pipeline failure reaches the caller as `RedeemedValidationRefusalException` with its cause; `iss` mismatch logs WARN once and refuses; `sub` mismatch refuses only when the access token carries a `sub` (`:157-158`); a bound subject that differs or is absent refuses; a `null` bound subject skips the step. The spec offered `token` or `lifecycle`; `flow` was chosen. |
| 2. Verifier refusals carry the redemption state | shipped-as-specified, with one new public type | `RefreshFlow.classify` logic is unchanged (Javadoc only); `RedeemedRefreshFailure` is tested first, so an `IllegalStateException` carrier is not misread. `RedeemedValidationRefusalException` accepts only a `TokenValidationException`, so the consistency and binding refusals needed a new `RedeemedIdentityRefusalException` (the spec said "unless the outline shows a gap"). Its `redemption` field is `transient` and degrades to `rotationUnknown()` on a deserialized instance (re-read, `:50-75`), which classifies `REDEEMED` with no successor, fail closed. Every refusal row is tested rotated and not rotated. |
| 3. `TokenLifecycleManager` delegates and behaves identically | shipped-as-specified | `doRefresh:880` calls `RefreshResultVerifier.verify(rotation, idTokenValidationBridge, null)` before `family.rotate` (`:891`); the catch is on the two carriers (`:881`), quarantines only when `rotation.rotated()` (`:882-885`) and rethrows the original through `carriedRefusal`. None of the eight named existing tests is in the 29-file diff (re-read from `git show --stat`), so they pass unedited. Small deviations: the catch narrowed from `IllegalStateException \| TokenValidationException` to the two carriers (L3-8), and the WARN is now attributed to the `RefreshResultVerifier` logger. |
| 4. Caching, refreshing discovery resolver (opt-in) | shipped-as-specified, with two criteria missed or unproven (L1-4, L1-5) | `CachingDiscoveryResolver` and `DiscoveryCacheStatus`: TTL, grace period, minimum refresh interval and jitter, single-flight refresh, fail closed after grace, an injected `InstantSource`, a defensive `copy()` of the cached metadata, a status readable without I/O that carries a cause class only. It also implements `LoadingStatusProvider` (`NOT_ATTEMPTED` to `UNDEFINED`, `RESOLVED` to `OK`, `FAILED` within grace to `UNDEFINED`, beyond grace to `ERROR`; `LOADING` is never reported). `DiscoveryResolver.resolve()` is untouched. Wired as an opt-in Quarkus bean behind `sheriff.client.discovery-cache.*`, `@Typed` to its own class so it does not satisfy a `LoadingStatusProvider` injection point. |
| 5. Documentation | shipped, with several doc/code mismatches (L1-6, L1-7) | `CLIENT-28` (verifier) and `CLIENT-29` (cache) are new rather than amendments of `CLIENT-16` and `CLIENT-17`; the README range is now `CLIENT-1`–`CLIENT-29`; `token-handling`, `threat-model`, `retrieval-flow`, `test-strategy` and `doc/LogMessages.adoc` (INFO `4`, WARN `115` and `116`) match the code except as listed. `CLIENT-29` sits at `requirements.adoc:448`, before `CLIENT-28` at `:568` (cosmetic ordering). |

Decisions the spec left to outline, as shipped:

| Decision | Shipped |
|----------|---------|
| Verifier package | `flow` (a third option; `token-handling.adoc` explains only why `token` would cycle, not `lifecycle`) |
| TTL default | none; required (Java constructor and Quarkus) |
| Stale policy | bounded grace, then fail closed; Quarkus defaults the grace period to `PT0S`, the Java constructor requires it |
| Minimum refresh interval, jitter | both default to zero; no hard maximum TTL shipped |
| `ProviderMetadata` mutability | a defensive copy through the existing `ProviderMetadata.copy()`; the class is untouched |
| Status vs `LoaderStatus` | its own three-state record plus the `LoadingStatusProvider` mapping above; no stated reason for not reusing `LoaderStatus` directly |
| Quarkus wiring | wired as an opt-in bean |

Added unplanned or outside the Expected Surface (in the 29-file diff, not declared): `RedeemedIdentityRefusalException` and its
test, `discovery/package-info.java`, `ClientConfigMapper.java` (+119) and `quarkus-config-doc.adoc`, the new Quarkus test
`TokenSheriffClientDiscoveryCacheTest`, and `token-sheriff-client/pom.xml` (the coverage includes grew from 12 to 14). Declared
and not touched, correctly: `StoredToken`, `IdTokenValidationBridge`, `RotationResult`, `RedeemedValidationRefusalException`,
`DiscoveryResolver`, `ProviderMetadata`, `LoaderStatus`, `ClientArchitectureTest`, `RefreshPathCoverageGuardTest`,
`RefreshFlowTest`, `RedeemedRefreshFailureTest`.

## Three-Layer Analysis

Layer 1 and 2 findings are the spec's own and the plan's quality; layer 3 is what the spec did not name. A level-3 read-only
leaf produced the first pass from the spec and the narrative as scratchpad copies (it opened no `.plan/` path); the orchestrator
re-read every point below at HEAD `1be3867b` before recording it, and re-rated one finding (L1-2). Not verified by anyone: the
build and test outcomes, the runtime behaviour under ArC, and whether the stale-read regression test fails without its fix (the
leaf reasoned it through; nothing was executed).

### Layer 1 — fidelity

- **L1-1 (note) Verifier package.** `flow` was chosen over `token` and `lifecycle`. `lifecycle` to `flow` already exists
  (`TokenLifecycleManager` imports `flow`), so it would not have cycled; the docs do not say why it was not taken.
- **L1-2 (minor, re-rated from the leaf's "major") The spec's HYPOTHESIS about the refreshed ID token's `sub` is true only for an
  unbound session, and no document records the answer.** With a bound subject the gap does not exist: an access token without a
  `sub` is refused at the bound step (`RefreshResultVerifier.java:137`), and one with a `sub` makes the ID token's `sub` equal it
  through §12.2 (`:157-158`). With no bound subject and an access token without a `sub`, the ID token's `sub` is compared with
  nothing and `StoredToken.refreshed` adopts a null subject (`StoredToken.java:179-182`). That is the spec's own rule for an
  unbound session ("the refreshed subject is adopted by the caller"), and `RefreshResultVerifierTest.java:104-111` pins it. What
  is missing is the written decision. Routed to PLAN-08 deliverable 5.
- **L1-3 (minor) The bound-subject step has no production caller, and the constant-time compare exists twice.**
  `TokenLifecycleManager.java:880` passes `null`, so `StoredToken.refreshed:174-178` is the only bound check on that path (correct,
  not a double check). The same compare is in `RefreshResultVerifier:165-171` and `StoredToken:191-195`. The spec's bound-subject
  criterion is exercised only by direct verifier tests.
- **L1-4 (minor) INFO per refresh remains.** The spec said a discovery refresh must not log at INFO on every refresh cycle. The
  delegate `DiscoveryResolver.resolve()` still logs `DISCOVERY_RESOLVED` at INFO on every success (`:140`, re-read), so a short
  TTL logs INFO once per TTL. `retrieval-flow.adoc:290-291` admits "at most once per TTL period"; the class Javadoc
  (`CachingDiscoveryResolver.java:60-66`) and `LogMessages.adoc:212` read as if no INFO occurs.
- **L1-5 (major) No test for the changed-endpoint criterion.** The spec: "A changed endpoint in the refreshed document is visible
  to the next caller after the refresh". The `@DisplayName` rows of `CachingDiscoveryResolverTest` (re-read) and a search of the
  three cache test classes show no valid, different document served after a refresh; the only hit is a hostile-document case that
  keeps "token endpoint unchanged". A refresh that updates `lastSuccess` but keeps the old metadata would pass every test. Routed to
  PLAN-08 deliverable 3.
- **L1-6 (minor) Doc/code mismatches in the cache's text** (each re-read): `retrieval-flow.adoc:291` says a failure episode is
  logged once, while the delegate logs ERROR or WARN per failed attempt (`DiscoveryResolver.java:179,189`);
  `requirements.adoc:467` and `threat-model.adoc:245` say the grace period "defaults to zero", which is true only for Quarkus;
  `threat-model.adoc:246` says a failed refresh is not retried before the minimum refresh interval, which holds nothing back at the
  default `PT0S`; `quarkus-config-doc.adoc:119` and `retrieval-flow.adoc:328` say injecting the bean while the cache is disabled
  fails at injection (L3-6); `retrieval-flow.adoc:296` writes `TokenSheriffClient-4` while `LogMessages.adoc:212` writes
  `-004` (the emitted string was not checked; `ClientLogMessages.java:85` declares `.identifier(4)`).
- **L1-7 (minor) ADR-0026 scope.** Its `affects` metadata and text cover the validator resolvers of `token-sheriff-validation-quarkus`
  only, while `CLIENT-29` and `retrieval-flow.adoc` cite it for the client-quarkus check without an amendment.
- **L1-8 (minor) No hard maximum on `ttl`, `grace-period`, `min-refresh-interval` or `jitter`.** The spec's outline questions named a
  hard maximum; none shipped and the docs are silent. See L3-5.

### Layer 2 — quality

- **L2-1 (note) Test sizes and conformance.** All new or grown classes are at or under 500 lines: 292, 316 and 239 (the three cache
  classes), 309 and 215 (verifier, verifier adversarial), 119 (`RedeemedIdentityRefusalExceptionTest`), 302 (the Quarkus test) and
  162 (`DiscoverySpecIT`); the 29-file diff stat agrees with the first seven. A grep of the new tests found no `Thread.sleep`,
  Mockito or Hamcrest (the leaf's grep; not repeated). Time is injected through `InstantSource`.
- **L2-2 (minor) The stale-snapshot regression test is weaker than it looks.** It does force the interleaving (a hook on the first
  `clock.instant()` runs a nested successful refresh), but it depends on `clock.instant()` being read after `state.get()`
  (`CachingDiscoveryResolver.java:164-165`); a refactor that reorders those passes it vacuously, and no negative control lives in
  the repository. Unexecuted.
- **L2-3 (minor) Concurrency test gaps.** The stampede test releases the held request after the first fetch is seen and does not
  prove the other callers had joined. Not reached by any test: the aborted-refresh branch (`:209-213`), the `join`
  `CompletionException` branch (`:222-225`), the `failure == null` branch of `serveStaleOrFail` (`:293-294`) and joiners
  receiving a failed refresh. `shouldBoundJitter` can never fail for a zero-jitter implementation.
- **L2-4 (minor) Quarkus tests stop short of the container.** They call the producer methods directly; nothing proves the startup
  observer fires under ArC or that a client proxy can be built for `CachingDiscoveryResolver`, and `DiscoverySpecIT` builds the
  cache by hand because the integration module does not wire the extension. No test covers an invalid value at startup (L3-1).
  `ClientConfigMapper.java:116-120` keeps a hand-mirrored production list, `DISCOVERY_CACHE_SETTINGS`, guarded by a test that
  derives its cases from `ClientRuntimeConfig` by reflection, which is the fix CodeRabbit asked for.
- **L2-5 (note) Verifier adversarial coverage is complete against the spec's list** (subject substitution, issuer substitution,
  `alg=none`, expired, unsigned, wrong audience, an access token without a subject while a subject is bound), each with a matched
  positive control; the two signature-tampering cases are refused by the pipeline and the validly signed `sub` and `iss` mismatch
  cases are in `RefreshResultVerifierTest`.
- **L2-6 (note) Log hygiene.** Ids are the next free in range (INFO `4`, WARN `115` and `116`, `ClientLogMessages.java:85,246,258`
  re-read). Attacker text goes through `LogSanitizer`; the status carries a class name only; the verifier's WARN and DEBUG carry no
  token and no subject.

### Layer 3 — defects the spec did not name

- **L3-1 (major) Invalid Quarkus discovery-cache values do not fail at startup.** `validateDiscoveryCacheOnStartup` calls only
  `ClientConfigMapper.validateDiscoveryCache` (`TokenSheriffClientProducer.java:193`), which checks an orphan key when disabled and a
  missing `ttl` when enabled (`ClientConfigMapper.java:178-197`, re-read). A zero or negative `ttl`, an unparseable duration or a
  `jitter` not smaller than `ttl` reaches `mapDiscoveryCache` and the constructor only when the `@ApplicationScoped` producer first
  runs, which is the first call through the client proxy. Failing scenario: `enabled=true`, `ttl=PT0S`; the application starts and
  the first resolve fails with `IllegalArgumentException`. CodeRabbit's generated summary in the #907 description ("invalid
  settings fail at startup") and ADR-0026's intent say otherwise; the author-written part of the description does not make the claim.
  Routed to PLAN-08 deliverable 1.
- **L3-2 (minor) An unexpected delegate exception is invisible to the status.** `refresh` catches only `TransportException`
  (`:241`); any other exception leaves `refreshAsWinner`'s `finally` (`:208-215`), which releases the slot and fails the joiners with
  a cause-less `TransportException("Discovery refresh was aborted")` but writes no state. `status()` never shows `FAILED`, and with
  no backoff every call refetches. The delegate declares its refusals as `TransportException`, so the path is theoretical. PLAN-08
  deliverable 2.
- **L3-3 (minor) One caller's interrupt becomes a cache-wide failure episode.** `DiscoveryResolver.java:190-193` turns an interrupt
  into a `TransportException`; the cache records it as `FAILED`, starts the backoff and logs WARN 115. With the Quarkus default
  grace of zero and an expired TTL, a cancelled request thread that wins the slot makes every joiner fail closed. PLAN-08
  deliverable 2.
- **L3-4 (minor) Joiners wait for a slow refresh even when a stale document is servable** (`join:218-227`, `resolve:189-192`); the
  cost is reasoned, not measured. PLAN-08 deliverable 2, decided at outline.
- **L3-5 (minor) Extreme durations overflow.** `success.plus(ttl)` (`:233`), `lastSuccess.plus(ttl).plus(gracePeriod)` (`:379`),
  `lastFailureAt.plus(minRefreshInterval)` (`:375`) and `jitter.toMillis()` (`:262`) are unguarded; the constructor validates only
  sign and `jitter < ttl`. The result for a huge value (nothing cached, every call refetches and throws) is the leaf's reasoning
  from the `java.time` contract, not run. PLAN-08 deliverable 2.
- **L3-6 (minor) Disabled-cache injection fails lazily.** The producer is `@ApplicationScoped` and unconditional
  (`TokenSheriffClientProducer.java:171-182`); CDI semantics suggest the `IllegalStateException` comes at first use, not at
  injection. Reasoned, not run. PLAN-08 deliverables 1 and 4.
- **L3-7 (minor) A shared exception instance.** When nothing was ever resolved, `serveStaleOrFail` rethrows the stored
  `lastFailure` itself to every caller (`:277-279`), so callers share one `Throwable` with the winner's stack trace. PLAN-08
  deliverable 2.
- **L3-8 (minor) The manager's catch narrowed.** The old helper's `IllegalStateException` was quarantined whatever raised it; now
  only the two carriers are (`TokenLifecycleManager.java:881`). The verifier wraps only `TokenValidationException`, and no path
  was found that raises a bare `IllegalStateException` from the bridge, so this is theoretical.
- **L3-9 (note) The stale-snapshot fix is correct.** Only a slot holder publishes with `state.set` (`:204`); the slot is a
  compare-and-set on `inFlight` (`:175`); the winner re-reads the state after the claim (`:176`) and re-evaluates when it differs
  (`:177-185`), completing the claim so no joiner is left waiting. One residual: `serveStaleOrFail` compare-and-sets
  `withExpiryLogged` from non-slot-holders (`:288`), so a winner deriving `failed` from `previous` can drop the flag and log the
  fail-closed WARN twice. No data corruption.
- **L3-10 (note) Fail-open probes found nothing.** The verifier never returns normally on a refusal; a `null` return means no ID
  token was issued; the bound compare is constant-time (`MessageDigest.isEqual`); the cache publishes only `delegate.resolve()`
  output and never hands out its own instance; the default `DiscoveryResolver` bean is untouched.

## Conformance

- Standing constraints: no `@Deprecated`; the default behaviour is unchanged and the eight named existing tests are not in the diff;
  `RefreshFlow.refresh` gained no behaviour (Javadoc only); the check still precedes the family advance; constant-time bound compare;
  JUnit rules and test sizes met. Not met as written: the INFO-per-refresh constraint (L1-4).
- `AGENTS.md` verification: the lane reports `verify` and the `-Ppre-commit` gate done; the orchestrator did not re-run them.
- The PR body Test Plan ticks "Verification command passed" for `verify -pl token-sheriff-client -am`; the lane's own candidate lesson
  (`-009`) says the create-pr agent had not run it. Recorded as the false-evidence defect it is (lesson `2026-11-15-18-006`), not
  as a verified pass.

## Metrics

| Metric | Value |
|--------|-------|
| Deliverables | 6 of 6 (the plan's numbering; the spec has 5) |
| Tokens | 20,460,947 (a floor: the `1-init` phase ran unmeasured) |
| Wall time | 173,973 s (about 48.3 h, including waits) |
| Harness, model, effort | claude, claude-sonnet-5-5, medium |
| Plugin and executor | 0.1.2106 |
| Review (lane) | functional-review 6 deliverables met, 0 with residuals, 0 not met, 0 unevidenced; code-review 0 blocking, 6 advisory, 5 rules cited |
| Merge | merge queue |

Review surface, read by the orchestrator (the landing supplied `unknown` for all three counts):

- Threads: 2 review threads, both resolved. One was fixed in `36b61921` (the hand-kept key list in the orphan test) and one declined
  with a stated reason (the joined-entry freshness suggestion at `CachingDiscoveryResolver.java:182`).
- CodeRabbit review bodies: "Actionable comments: 2" (the two threads) and "Nitpick comments: 2", both nitpicks answered in a
  triage comment as known test-hardening gaps and not fixed (`DiscoverySpecIT:102-103`, `CachingDiscoveryResolverAdversarialTest:157-158`).
  The lane says CodeRabbit could not fully review 8 files; the provider read below says the same.
- `runbook_lane review-gate --pr-number 907`: `merge_allowed: false`, `reason: undecidable_read`, with the detail "unreadable
  review-body section: 'Files not reviewed due to moderation or processing errors: 8'". Recorded as unknown.
- Sonar: `read_open_issues --pr 907` returned `count_status: undecidable` (`head_revision_unavailable`; analysis revision `36b61921`,
  head not retrievable). The lane's own fact is `new_code_issue_count=0` with hotspots not read. Recorded as unknown, never 0.
- CI: the `e2e-tests` job failed twice on one Playwright screenshot protocol error (`accessibility-wcag-compliance.spec.js:60`, heads
  `fd77ed55` and `36b61921`) and passed on rerun. The lane says no branch code is implicated; the orchestrator did not reproduce it.

## Routing

- **The inbox.** 10 messages: 1 landing and 9 candidate lessons.
  - Filed as new lessons: `-001` as `2026-11-15-18-001` (interleaving review for shared mutable state), `-002` as `2026-11-15-18-002`
    (re-review scope), `-003` as `2026-11-15-18-003` (the Playwright flake), `-006` as `2026-11-15-18-004` (argparse rejection carries
    no cause), `-008` as `2026-11-15-18-005` (the dispatch seam's `prompt_not_found`), `-009` as `2026-11-15-18-006` (the PR Test
    Plan claim). The component of `-001`, `-002`, `-004`, `-005`, `-006` is a plan-marshall one and was filed with
    `--allow-foreign-store`, as the earlier lessons of this epic were.
  - Folded, not filed: `-004` (a hand-kept mirrored list in a test) is a recurrence of `2026-11-15-11-001`; `-005` (the invented
    `--number` flag) is a recurrence of `2026-11-11-12-003`; `-007` (a finalize fix commit refused as `no_sanction`) is a third
    recorded recurrence of `2026-11-12-18-003`.
- **Layer-3 and layer-1 defects** (L1-2, L1-4 to L1-7, L2-2 to L2-4, L3-1 to L3-7) were reported to the operator and staged as
  **PLAN-08** (`plans/PLAN-08-discovery-cache-and-verifier-hardening.md`) on the operator's word. L1-2 was re-rated from the leaf's
  "major" to a documentation gap before routing; the operator's question described it as a gap in the verifier, so it is stated
  here that PLAN-08 deliverable 5 may close it by documenting the rule.
- **The Playwright flake** is an epic-level Open Defect (no plan staged; the operator has not asked for one).

## Reconciliation and moved anchors

- Queue: PLAN-04 is `shipped`, `pr` `#907`, `landing` `landings/PLAN-04.md`, `plan_marshall_plan_id`
  `client-refresh-verification-discovery-cache`.
- Moved anchors for PLAN-03 (the outline re-reads each at HEAD): `TokenLifecycleManager.doRefresh` is `:836-935` with the verifier call
  at `:880` and the private helpers gone; `TokenSheriffClientProducer` gained `cachingDiscoveryResolver` (`:171`) and
  `validateDiscoveryCacheOnStartup` (`:193`) and its later producers, `clientAuthentication` among them, moved down by about 60 lines;
  `ClientRuntimeConfig` gained the `discovery-cache` keys (`:168` onward); `ClientConfigMapper` gained `DISCOVERY_CACHE_SETTINGS`
  (`:120`), `validateDiscoveryCache` (`:178`) and `mapDiscoveryCache` (`:209`); `quarkus-config-doc.adoc` gained a discovery-cache
  section (`:103`); `doc/client/requirements.adoc` took `CLIENT-28` and `CLIENT-29` (the next free is `CLIENT-30`);
  `doc/client/README.adoc` states `CLIENT-1`–`CLIENT-29`; `test-strategy.adoc` gained rows at `:567-573`; `doc/LogMessages.adoc` took
  INFO `4` and WARN `115` and `116`; `token-sheriff-client/pom.xml` coverage includes are now 14.
- PLAN-07 inherits `doc/LogMessages.adoc` (INFO `4`, WARN `115` and `116` taken here, INFO `10` and WARN `173` by PLAN-06).
