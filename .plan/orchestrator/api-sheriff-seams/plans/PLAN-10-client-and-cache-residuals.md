# PLAN-10: Client and discovery-cache residuals

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-10-client-and-cache-residuals.md` and is queued as one row file, `queue/PLAN-10.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the operator's word at the PLAN-08 analysis (2026-11-17): "one follow-up plan for the residuals and other left-overs and
> open issues". It gathers the minor `token-sheriff-client` residuals the epic's landings left with nothing staged: PLAN-08's
> (`landings/PLAN-08.md`), PLAN-01's and PLAN-02's (`epic.md` Open Defects), PLAN-04's two test-hardening nitpicks, and the unsettled
> CodeRabbit body findings on the shipped PRs. None of it is a blocker or major; it is staged because it ships in 0.9.10 text and API
> Sheriff's PLAN-34 adopts the documented behaviour.

## Objective

Every plan of this epic that touched the client module shipped with a short list of known leftovers: text that says more than the
code does, a Javadoc that omits a case, test helpers that break the repository's own test rules, two untested branches, a public
method with no `@since`, and review-bot body findings nobody recorded a disposition for. Each is small. Together they are the
difference between documentation that matches the code and documentation API Sheriff has to second-guess. This plan clears them
in one pass over `token-sheriff-client`, `token-sheriff-client-quarkus` and `doc/client`. No public API is added beyond what a fix needs,
and the default behaviour of every class named here does not change.

## Deliverables

1. **The discovery cache's shipped text matches its code (PLAN-08 L2-1 to L2-6, L1-1).** Each statement below is re-read against the code at
   HEAD and corrected where it is still wrong; the final text matches the shipped behaviour. Verification: a re-read of each location, and
   `doc/LogMessages.adoc` reads in one identifier form.
   - `CachingDiscoveryResolver` class Javadoc, Logging paragraph: an interrupt leaves no cache `DEBUG` line (`refresh` rethrows before
     `recordFailure`), and a refresh that recovers logs INFO `4` and not `DEBUG`; `retrieval-flow.adoc` repeats "a `DEBUG` line per successful and per
     failed refresh".
   - `retrieval-flow.adoc` single-flight paragraph lists two outcomes of an aborted claim and omits the third: a newer `FAILED` state inside
     `minRefreshInterval` is answered by `serveStaleOrFail` with no claim.
   - `doc/LogMessages.adoc` INFO rows `1` to `3` read `-001` to `-003` while row `4` reads `-4`. Verify what the runtime emits for `1` to `3`
     (a test that formats the identifier, as `shouldEmitPinnedRecoveryIdentifier` does for `4`) and make the column one form.
   - "the delegate logs its own line for most failed attempts" (class Javadoc, `retrieval-flow.adoc`, `LogMessages.adoc` row `115`): count the refusal
     sites of `DiscoveryResolver.resolve` that log against those that do not and state it without "most".
   - `threat-model.adoc` (12.2 row): the reason "nothing is bound and there is no principal to protect" omits that an unbound session's stored
     ID token is replaced by the refreshed one unchecked (`StoredToken.refreshed`). State it; the decision to keep is unchanged.
   - ADR-0026: the metadata `summary` (and `tags`) still describe a validation-only ADR while `affects` lists the client module.
   - The duplicate fail-closed WARN is attributed to "a lost `withExpiryLogged` compare-and-set" in the Javadoc and `retrieval-flow.adoc`; the
     duplicate comes from the winner's unconditional `state.set(refreshed)` overwriting a state already marked `expiryLogged`. Correct the cause.
2. **The discovery cache's tests and edges follow the repository's rules (PLAN-08 conformance, L1-2, L3-1 to L3-3; PLAN-04 nitpicks).**
   - Three unit-test helpers catch `RuntimeException` (one also `Error`) under `cui-rewrite:disable InvalidExceptionUsageRecipe`
     (`CachingDiscoveryResolverFailureHandlingTest`, `...HookFailureTest`, `...RefreshRegressionTest`); AGENTS.md sanctions a test-side broad
     catch only in integration-test code. Catch the exact types, or settle at outline that the helper is a sanctioned boundary and say so in AGENTS.md.
   - The same harness (caller thread runner, `awaitParked`, scripted delegate, `Hold`) is copied into those three classes: extract the one
     package-private `...Support` fixture AGENTS.md advises; `invalidSettings()` duplicates `invalidDurations()` (merge them without losing the
     `jitter above ttl` row); the test Javadoc and assert message still say "the `ClientConfigMapper` registries", which no longer exist.
   - A test drives `resolve()` with a 365-day `ttl`, `grace-period` and `min-refresh-interval` (the construction-time bound is tested, the arithmetic is not).
   - L3-1 (a caller whose interrupt flag is already set gets a failing provider's refusal rethrown unrecorded, so that thread never writes a failure
     state or backoff): settled at outline as accepted and stated in the class Javadoc, or changed; either way with a test.
   - L3-2 and L3-3 are not shown by reading and each gets a test that runs: ArC's default removal of an unused `CachingDiscoveryResolver` injection point with
     the cache disabled does not fail startup (the build step matches by required-type name only); an invalid short-form value (`5m`) and an unparseable
     value both fail startup naming the property under the real Quarkus converter, and the message names the forms the converter accepts.
   - PLAN-04's two nitpicks, answered as known test-hardening gaps and never fixed: `DiscoverySpecIT` and `CachingDiscoveryResolverAdversarialTest` at
     the lines the landing named (locations moved; re-locate).
3. **PLAN-01's minor residuals are closed (see `epic.md` Open Defects).** (a) The delivery rationale in `doc/client/requirements.adoc` says API Sheriff's shape
   uses the engine without the client Quarkus extension; its pom lists `token-sheriff-client-quarkus`, so the sentence is wrong. (b) "SecureRandom holders" in
   `requirements.adoc` and `test-strategy.adoc` where the guard covers every static `Random`. (c) `NativeImageClassScanner`'s `@throws` omits
   `UncheckedIOException`. (d) Two untested 400 branches in `ClientEngineNativeEndpoint`. (e) `ClientNativeImageSpecIT` imports Hamcrest against the spec constraint (module
   precedent): replace it or record the precedent as accepted. Verification: each re-read at HEAD, a test for (d) that can fail.
4. **PLAN-02's minor residuals are closed (see `epic.md` Open Defects).** (1) `ClientConfiguration.validateScopes` is public with no `@since`, tightened for existing
   callers, and the refresh overload in `RefreshFlow` has none either; (2) `requirements.adoc` `Security exclusions` still bans "tokens / codes in URL query" while
   `CLIENT-27` ships an opt-in `query`, `CLIENT-14` dropped "authorization codes" from its MUST NOT, and the `CLIENT-27` traceability row is out of order; (3) the 3-arg
   and 4-arg `AuthorizationRequestBuilder.build` are independent paths, so a subclass override of one is bypassed by the other's callers including `authorizePushed`
   (document it, or route one through the other under ADR-0006); (4) a missing PAR endpoint is the generic `ClientProtocolException`, the endpoint lookup after it is dead
   and the redirect-URI guard exists three times in `AuthorizationCodeFlow`; (5) history-framed wording (`AuthorizationRequestBuilder`, `retrieval-flow.adoc`, five test sites), raw U+00A0
   in two test literals, `ClientConfigurationTest` over 500 lines, refusal tests that assert only the type and no test of the `LogSanitizer` output.
5. **Every review-bot body finding on the shipped PRs has a recorded disposition.** `review-gate` still returns `body_findings_undisposed` for #898 (2), #899 (10), #901 (3), #906 (9),
   #907 (2 nitpicks), #908 (7) and #912 (5). For each, the plan records one of: fixed at HEAD (with the commit or the line), fixed in this plan, or declined with a reason, in the PR
   thread or a PR comment and in the run's progress record. A finding whose defect has since moved or been covered by PLAN-03 or PLAN-09 is recorded as such and not fixed twice.
   The lane's own thread replies are the starting point; the dispositions live there. Verification: `runbook_lane review-gate --pr-number N` for each PR, and a table of finding, disposition and evidence.

Done when: all criteria pass, `RefreshPathCoverageGuardTest` and the coverage includes of `token-sheriff-client/pom.xml` still pass, the full `-Ppre-commit` plus `clean install`
sequence of `AGENTS.md` is green, and the Sonar open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed
read, never `0`).

**Operator decisions needed at outline**

- **Interrupt edge** (deliverable 2): accept and document, or record a failure for a flag-carrying caller.
- **Broad catch in unit-test helpers** (deliverable 2): exact types, or a sanctioned boundary named in AGENTS.md.
- **`ClientNativeImageSpecIT` and Hamcrest** (deliverable 3e): replace, or accept the module precedent.
- **Subclass bypass of the two `build` overloads** (deliverable 4 (3)): document, or route one through the other; a public-class contract of 0.9.10.
- **A dedicated reason for a missing PAR endpoint** (deliverable 4 (4)): keep the generic `ClientProtocolException`, or add a reason; a public-API change.
- **Body findings the lane declined** (deliverable 5): whether a declined finding stands.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no history-framed wording in anything this plan writes.
- **Default behaviour stays unchanged and is proven so.** No class named here changes behaviour for its existing callers except where a decision above says so and a test proves it.
- **Say only what was read.** A corrected statement is checked against the code at HEAD before it is written; a statement that only a run could settle is run or left out.
- **Security.** No token, subject, refresh token or response body is logged unsanitised; attacker-controlled text goes through `LogSanitizer`. A new LogRecord takes the next free identifier in its range at write time and is added to `doc/LogMessages.adoc`.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never sleeps), a negative control per regression test, new or grown test classes at or under 500 lines.
- No new dependency without operator approval. Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and
  transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.

## Claim Labels

- OBSERVED: the Javadoc Logging paragraph says an interrupt leaves "only the cache's own `DEBUG` line" and that the cache "adds a `DEBUG` line per successful and per failed refresh"; `refresh` rethrows an interrupted failure before `recordFailure`, and a refresh after a failure logs INFO `4` — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java` (`dcbdcfb6`: `:95-102`, `:407-411`, `:416-421`) and `doc/client/specification/retrieval-flow.adoc:323`
- OBSERVED: `retrieval-flow.adoc:268-269` names two outcomes and `CachingDiscoveryResolver.resolve` answers a failed state inside `minRefreshInterval` with no claim (`:299-301`); the fail-closed WARN is logged only when `compareAndSet` succeeds (`:471-472`) and the winner's `state.set(refreshed)` (`:356`) is unconditional
- OBSERVED: `doc/LogMessages.adoc:209-212` reads `-001`, `-002`, `-003`, `-4`; ADR-0026's `affects` (`:11`) lists the client module and its `summary` (`:9`) names only the validation extension's resolvers
- OBSERVED: `threat-model.adoc:363-366` gives "nothing is bound and there is no principal to protect" as the reason, and `StoredToken.refreshed` writes `newIdToken` into an unbound bundle unchecked (`StoredToken.java:174-182`)
- OBSERVED: three unit-test helpers catch `RuntimeException` (`CachingDiscoveryResolverFailureHandlingTest:321`, `...HookFailureTest:320`, `...RefreshRegressionTest:287`, the last also `Error`) — `git grep` at `dcbdcfb6`
- OBSERVED: a flag-carrying caller's refusal is rethrown unrecorded at `CachingDiscoveryResolver.java:407-411`; `TokenSheriffClientProcessor.requireDiscoveryCacheForInjection` matches by required-type name only (`:148-149`); `ClientConfigMapper.parsedDuration` catches `IllegalArgumentException` and says "ISO-8601 duration such as PT5M" (`:283-290`)
- HYPOTHESIS: the same harness is copied into three classes, `invalidSettings()` duplicates `invalidDurations()`, and no test drives `resolve()` with 365-day values — the PLAN-08 leaf's reading, not re-read (verify-at-outline)
- HYPOTHESIS: `DiscoveryResolver.resolve` has 13 refusal throw sites of which 5 log — the leaf's count, not re-read (verify-at-outline)
- HYPOTHESIS: with the cache disabled, an injection point that ArC removes as unused still fails startup, and Quarkus's `Duration` converter throws `IllegalArgumentException` and accepts `5m` — not shown by reading (verify-at-outline, by a test that runs)
- HYPOTHESIS: every PLAN-01 and PLAN-02 residual in deliverables 3 and 4 holds at HEAD — carried from `landings/PLAN-01.md` and `landings/PLAN-02.md`; their line numbers have moved (PLAN-04, PLAN-07 and PLAN-08 edited `requirements.adoc`, `test-strategy.adoc` and `retrieval-flow.adoc`) and none was re-read for this spec (verify-at-outline)
- HYPOTHESIS: the body findings of deliverable 5 are mostly already answered in their PR threads (the lane's replies), and `handled` reads 0 only because no finalize step records it — `review-gate` returns `handled: 0` on every one of these PRs; not checked per finding
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (PLAN-03 will have moved `ClientConfiguration`, `ClientConfigMapper`, `TokenSheriffClientProducer`, `requirements.adoc` and `test-strategy.adoc`, and PLAN-09 `LogMessages.adoc`), (3) which deliverable-3 and -4 items PLAN-03 already closed, and (4) that the deliverables stay at about six tasks or fewer; if the outline scopes above that, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:95 — class Javadoc (logging), `:407` interrupt edge
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`:1 — read (the refusal sites that log)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/StoredToken.java`:166 — read (the unchecked replacement of the stored ID token)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverFailureHandlingTest.java`:1 — helper catch, shared harness
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverHookFailureTest.java`:1 — helper catch, shared harness
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverRefreshRegressionTest.java`:1 — helper catch, shared harness
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverTest.java`:1 — `invalidSettings`, `invalidDurations`, the 365-day `resolve()` case
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverAdversarialTest.java`:1 — PLAN-04 nitpick
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/DiscoverySpecIT.java`:1 — PLAN-04 nitpick
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`:283 — `parsedDuration` message
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientDiscoveryCacheValidationTest.java`:1 — stale "registries" wording, `5m` case
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`:144 — `requireDiscoveryCacheForInjection`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`:1 — `validateScopes` (`@since`)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`:1 — the refresh overload (`@since`)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`:1 — the two `build` overloads, history-framed wording
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java`:1 — the PAR endpoint reason, the dead lookup, the triplicated redirect-URI guard
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/config/ClientConfigurationTest.java`:1 — over 500 lines, raw U+00A0
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestOptionsTest.java`:1 — raw U+00A0, refusal assertions
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/NativeImageClassScanner.java`:1 — `@throws UncheckedIOException`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/ClientEngineNativeEndpoint.java`:1 — two untested 400 branches
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/ClientNativeImageSpecIT.java`:1 — Hamcrest import
- OBSERVED: `doc/client/requirements.adoc`:1 — delivery rationale, "SecureRandom holders", `Security exclusions`, `CLIENT-14`, `CLIENT-27`
- OBSERVED: `doc/client/specification/test-strategy.adoc`:1 — "SecureRandom holders"
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:259 — single-flight paragraph, logging paragraph, history wording
- OBSERVED: `doc/client/threat-model.adoc`:363 — the 12.2 row
- OBSERVED: `doc/LogMessages.adoc`:209 — INFO rows `1` to `4`, WARN `115`
- OBSERVED: `doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc`:9 — `summary` and `tags`
- OBSERVED: `AGENTS.md`:318 — the approved broad-catch list (changed only if the outline sanctions the test helper)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverSupport.java`:1 — the one shared fixture (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/config/` — a split of `ClientConfigurationTest` (new classes at or under 500 lines) (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff's PLAN-34 adopts documentation that matches the code.
- Depends on: PLAN-08, shipped (#912, `dcbdcfb6`); PLAN-01, PLAN-02 and PLAN-04 shipped.
- Sequence after PLAN-03 when PLAN-03 is in flight or staged: they share `ClientConfiguration`, `ClientConfigMapper`, `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc` and `doc/LogMessages.adoc`, and deliverables 3 and 4 may be closed in part by PLAN-03's outline. PLAN-09 shares `doc/LogMessages.adoc` only. The `next` admission gate decides from the declared surface; the deliverable-5 surface (the files the body findings name) is declared at outline by the fold rule and is not in the table above.
- Adjacent to: API Sheriff's PLAN-34 (adopts the verifier and the cache; waits for a released 0.9.10; not part of this spec).
- Requirement and log ids are next free at write time.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-10-client-and-cache-residuals.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
