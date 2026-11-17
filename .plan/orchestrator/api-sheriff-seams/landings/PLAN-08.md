# Landing Analysis: PLAN-08 — Discovery cache and refresh verifier hardening

epic: api-sheriff-seams
workstream: WS-01
pr: #912 (https://github.com/cuioss/TokenSheriff/pull/912), merged as dcbdcfb6facbd5e2acf07ed7d13549322dfc516b

> Landing record for one shipped plan. Lives at `landings/PLAN-08.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `discovery-cache-verifier-hardening-012.md` (lane `plan-marshall`, plan id `discovery-cache-verifier-hardening`),
plus eleven candidate-lesson messages (`-001` to `-011`). PR state read through `ci pr view` (`state: merged`, merge commit as above, base
`main`). `inbox landing-check`: `complete: false` (missing `unresolved_review_threads`, `review_body_findings_total` and
`review_body_findings_handled`, all `unknown` in the landing); `surface_delta` is `unmeasured` (neither the declared nor the realized surface was
supplied), so the declared-vs-realized comparison below was made by hand from `git diff 1baa0e3d..dcbdcfb6` (27 files, +2865/-206), which holds
PR #912 (26 files, +2864/-205) plus the one-line import fix of #910 (`BearerTokenProducerSchemeRuleTest.java`, merged first). The lane's account of
its own finalize (steps, the two review overrides, the post-merge JMH timeout) is its own and was not re-run.

## Deliverable Fidelity vs Spec

The spec lists five deliverables; the landing reports `deliverables_total=5`, `deliverables_done=5`. Each traces to code and, except the doc-only
deliverable 5 and the dropped sub-case in deliverable 3, to a test that can fail. The five operator decisions at outline were taken by the lane
(the spec asked for them) and were read off the merged code, not from a record of the operator's answer: **disabled-cache injection** — fail at
runtime init through a recorded build-step check, the producer's first-use guard kept as the backstop; **interrupt** — a delegate failure while
the caller's interrupt flag is set aborts the refresh, writes no state and keeps the flag; **bounds** — refuse, not saturate, 365 days inclusive for
`ttl`, `grace-period` and `min-refresh-interval`, `jitter` bounded through `ttl`; **quiet resolve path** — none, the delegate's logging is stated plainly
and `DiscoveryResolver` is not in the diff; **`sub` against an unbound session** — keep and document.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Settings validated for value at startup | shipped-as-specified, with the injection decision taken as fail-fast | `CachingDiscoveryResolver.checkSettings` holds the one rule list; the constructor (`:209`) and `ClientConfigMapper.validateDiscoveryCache` (`:223`) both call it. `propertyOf(Setting)` (`ClientConfigMapper:126-133`, re-read) maps a structured `Setting` to its property, no message parsing. Tests (leaf, test side not re-read): `TokenSheriffClientDiscoveryCacheValidationTest` (14 invalid rows, an orphan-as-disabled twin, positive controls); build step `TokenSheriffClientProcessor.requireDiscoveryCacheForInjection` (`:144-153`, re-read) plus three deployment tests |
| 2. The cache survives what the delegate can throw | shipped-as-specified | `refresh` catches `RuntimeException` and records it through `recordFailure` (`CachingDiscoveryResolver:403-438`, re-read); an interrupted caller rethrows with no state (`:407-411`); the status carries the cause class only; `MAX_DURATION` is 365 days (`:120`, `:270`). Tests (leaf): `...FailureHandlingTest` (unexpected exception, backoff, stale policy, joiner, interrupt, a non-interrupted control), `CachingDiscoveryResolverTest` at the bound and bound+1 ns. The three "settled at outline" sub-items: joiner waits for the winner's full refresh (documented, `retrieval-flow.adoc:263-264`, re-read), the shared stored exception instance (rethrown at `:464`, documented), the duplicate WARN (documented, with the wrong cause, see L1-1) |
| 3. Regression tests prove what they claim | shipped-modified: the "serveStaleOrFail with no stored failure" branch became an invariant | changed endpoint, joined stampede, jitter, stale snapshot and the aborted / `CompletionException` / failed-refresh joiner branches each have a test (leaf: `...RefreshRegressionTest`, `...AdversarialTest.awaitJoined`, `shouldBoundJitter`, `shouldNotOverwriteNewerEntryFromStaleRead`; not re-read). `serveStaleOrFail` now starts with `Objects.requireNonNull(current.lastFailure, ...)` (`:460`, re-read), so the branch cannot occur and no test reaches it. All new classes are at or under 500 lines (largest 416, leaf) |
| 4. Logging and configuration text match the code | shipped-as-specified in five of six places, with residual text errors (L2-1 to L2-4, L2-6) | logging, defaults, injection and the identifier row were rewritten; ADR-0026 `affects` now lists `token-sheriff-client-quarkus` (`:11`, re-read). The residual errors are below |
| 5. The verifier's open question closed and documented | shipped-as-specified (decision: keep and document), rationale overstated (L2-5) | `RefreshResultVerifier`, `StoredToken`, `TokenLifecycleManager` and `RefreshResultVerifierTest` are not in the diff (leaf; consistent with the 26-file list). The decision is written in `token-handling.adoc` and `threat-model.adoc:363-366` (re-read). The pinning test case stays |

Diff files outside the declared Expected Surface: `TokenSheriffClientRecorder.java`, `TokenSheriffClientProcessor.java` and four deployment tests
(`DiscoveryCacheInjectionDeploymentTest`, `...EnabledInjectionDeploymentTest`, `...DisabledNoInjectionDeploymentTest`, `TokenSheriffClientProcessorTest`) are
explained by deliverable 1's injection decision; `AGENTS.md` is explained by deliverable 2 (the new approved broad-catch site, `:318-326`, leaf); the
four new test classes were declared as HYPOTHESIS. `BearerTokenProducerSchemeRuleTest.java` is #910, not PLAN-08, and no deliverable explains it. The declared
`DiscoveryCacheStatus`, `DiscoveryResolver`, `RefreshResultVerifier`, `StoredToken`, `TokenLifecycleManager`, `DiscoveryResolverTest`, `DiscoveryAdversarialTest`,
`CachingDiscoveryResolverStalePolicyTest`, `TokenSheriffClientDiscoveryCacheTest` and `DiscoverySpecIT` were not touched, which fits the decisions taken.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface), and the orchestrator re-read the
points marked "re-read" at `dcbdcfb6` (`git show`) before writing them here. Points marked "leaf" are the leaf's reading that this analysis did not re-read:
they are recorded as the leaf's account, not as settled. The reading is **by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf — reason: every full-ship landing gets its own leaf (merged-code depth, no shared context with another landing's read).
- **Verdict:** layer 1 findings (1 minor, 2 info); layer 2 findings (6 minor, residual doc text); layer 3 findings (2 minor, 3 not shown by reading, 3 test-rule deviations). No
  blocker and no major defect was found at any layer.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec (and the Javadoc and `retrieval-flow.adoc:331-333`) attribute the duplicate fail-closed WARN to "a lost `withExpiryLogged` compare-and-set". A lost CAS suppresses the log: it runs only when `compareAndSet` succeeds. The duplicate comes from the winner's unconditional `state.set(refreshed)`, which overwrites a state already marked `expiryLogged` | checked | refutes | `CachingDiscoveryResolver.java:471-472`, `:356` (re-read) |
| L1-2 | The spec's Done-when has no criterion for the injection, interrupt or `sub` decisions, and "at-bound and just-beyond-bound tested" was delivered as accept/refuse at construction and in `checkSettings`; no test drives `resolve()` with 365-day values, so "cannot overflow" rests on reasoning | checked | confirms | `CachingDiscoveryResolver.java:120`, `:270` re-read; the absence of a `resolve()` test is the leaf's reading |
| L1-3 | The interrupt behaviour assumes the delegate restores the interrupt flag when it turns `InterruptedException` into `TransportException` (`DiscoveryResolver:191-193`); a subclass that does not is recorded as a failed refresh | checked (leaf; not re-read) | confirms | `DiscoveryResolver.java:191-193` |

The spec's labelled premises held at the parent: the OBSERVED refresh catch, the constructor-only rules and the unguarded arithmetic (leaf, at `1baa0e3d`). The spec's
HYPOTHESIS that the stale-snapshot test is vacuous if the clock is read before the state became moot: the test now publishes through the `afterClaim` hook (leaf).

**Layer 1 verdict:** findings — 3 (L1-1 minor, L1-2 and L1-3 info)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `CachingDiscoveryResolver.checkSettings`, `ClientConfigMapper:126-133`, `:283-290` | the 14-row validation test, orphan twin, positive controls | none | checked (code re-read, tests leaf) |
| 2 | `CachingDiscoveryResolver:403-438` | `...FailureHandlingTest`, `CachingDiscoveryResolverTest` bound rows | none | checked (code re-read, tests leaf) |
| 3 | `...RefreshRegressionTest`, `...AdversarialTest`, `...HookFailureTest` | each with a control (leaf) | the `serveStaleOrFail` no-failure branch is unreachable by construction, not reached (`:460`) | checked |
| 4 | `doc/LogMessages.adoc`, `retrieval-flow.adoc`, `requirements.adoc`, `threat-model.adoc`, `quarkus-config-doc.adoc`, ADR-0026 | a re-read; `shouldEmitPinnedRecoveryIdentifier` pins `TokenSheriffClient-4` (leaf) | L2-1 to L2-4, L2-6 | checked |
| 5 | `token-handling.adoc`, `threat-model.adoc` | the existing pin `shouldPassConsistencyWhenAccessTokenHasNoSubject` (leaf) | L2-5 | checked |

Lane claims against the diff: "the afterClaim hook moved inside the claimed region" (borne out, `:344`, re-read), "a superseded claim is completed as aborted" (borne out, `:345-351`, re-read),
"`ClientConfigMapper` derives both registries from one mapping" (borne out in main code, `:126-133`; the test Javadoc still says "registries", leaf), "the orphan-coverage test derives its expected set from
`ClientRuntimeConfig`" (borne out, leaf), and the **`weakened_test` false positive** (borne out: assertion call sites outside imports 49 on `1baa0e3d` and 60 on `dcbdcfb6`, and lines matching the gate's
`\.assert[A-Z]` pattern 9 and 2, both re-counted here; the lane's "55 at an earlier head" is not checkable from the merge).

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L2-1 | The class Javadoc says an interrupt "leaves only the cache's own DEBUG line" and that the cache "adds a DEBUG line per successful and per failed refresh". For an interrupt the cache logs nothing (the exception is rethrown before `recordFailure`'s DEBUG), and a refresh that recovers logs INFO 4 instead of DEBUG. `retrieval-flow.adoc:323` repeats the DEBUG sentence | checked | refutes | `CachingDiscoveryResolver.java:95-102`, `:407-411`, `:416-421`; `retrieval-flow.adoc:323` (re-read) |
| L2-2 | The single-flight paragraph lists two outcomes (a fresh newer state is served, an expired one is refreshed) and omits the third: a newer FAILED state inside `minRefreshInterval` is answered by `serveStaleOrFail` with no claim | checked | refutes | `retrieval-flow.adoc:268-269`; `CachingDiscoveryResolver.java:299-301` (re-read) |
| L2-3 | The identifier alignment is partial: row 4 reads `TokenSheriffClient-4`, rows 1 to 3 still read `-001`/`-002`/`-003`, so the table mixes forms. Only identifier 4 is pinned by a test (leaf); what the runtime prints for 1 to 3 was not read (`LogRecordModel` is a dependency jar) | checked | refutes | `doc/LogMessages.adoc:209-212` (re-read) |
| L2-4 | "the delegate logs its own line for most failed attempts": the leaf counted 13 refusal throw sites in `DiscoveryResolver.resolve` and 5 that log. Not re-read (the file was not opened here); the doc does add "but not for every one" | checked (leaf; not re-read) | refutes | `DiscoveryResolver.java` |
| L2-5 | `threat-model.adoc` gives "nothing is bound and there is no principal to protect" as the reason to keep the unbound case. For an unbound session with an access token without `sub`, `StoredToken.refreshed` stores `newIdToken` unchecked, so the stored ID token can switch `sub` with no comparison against anything. The decision is defensible; the sentence should say the stored ID token is replaced unchecked. PLAN-04's analysis rated this gap major, the orchestrator's staging narrowed it to a documentation gap, and this text records the narrower reading with one reason overstated | checked | refutes | `threat-model.adoc:363-366`, `StoredToken.java:174-182` (re-read) |
| L2-6 | ADR-0026's `affects` now lists the client module, but the metadata `summary` (`:9`) still reads "The validation extension's configuration resolvers..." and the `tags` are unchanged | checked | refutes | `doc/adr/0026-...adoc:9-11` (re-read) |

**Layer 2 verdict:** findings — 6 (all minor; one decision rationale, five text errors)

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `CachingDiscoveryResolver.java:407-411` (re-read) | a caller thread whose interrupt flag is already set calls `resolve()` while the provider is failing or hostile | the refusal is rethrown unrecorded, so that thread writes no failure state and no `minRefreshInterval` backoff, and every such call goes to the network again; other threads still record. It matches the stated design ("fails while the flag is set") and is bounded to flag-leaking callers | minor |
| L3-2 | `TokenSheriffClientRecorder` / `TokenSheriffClientProcessor.java:148-149` (re-read) | an injection point that ArC later removes as unused, or an `Instance<CachingDiscoveryResolver>` used as an optional lookup, with the cache disabled | the match is by required-type name only, so either could fail startup with the cache disabled; whether ArC reports a removed point at this phase is not determinable by reading, and the deployment tests mark the injecting beans `@Unremovable`, so removal is untested | minor, not shown by reading |
| L3-3 | `ClientConfigMapper.java:283-290` (re-read) | `sheriff.client.discovery-cache.ttl=5m` or another non-ISO-8601 form | the message says "ISO-8601 duration such as PT5M" while Quarkus's converter may accept the short form; whether Quarkus's converter throws `IllegalArgumentException` for an unparseable value, and that the startup observer fires with an invalid value, is not shown (the unit test uses a plain `SmallRyeConfigBuilder`) | minor, not shown by reading |

**Layer 3 not shown by reading:** L3-2 and L3-3 above; the shared stored exception instance rethrown to every caller when nothing was resolved (`:464`, re-read, documented as accepted, the suppressed-list
growth under `minRefreshInterval>0` is not shown); concurrency under real contention (the tests script interleavings with latches); the green `-Ppre-commit` and clean install, `RefreshPathCoverageGuardTest` and the
coverage includes (no `pom.xml` change in the diff); and every claim the leaf listed as a lane statement (`sonar_open_new_issues=0`, every thread resolved, the review overrides). Also from the leaf, not re-read: a jitter below 1 ms
is accepted and yields no jitter, and an `InstantSource` near `Instant.MAX` would still overflow in `plus` (neither reachable with the system clock); the single-flight claim/join protocol itself, read by the leaf and found sound
(the `finally` always completes the claim and releases the slot; `refreshAsWinner` re-read at `:340-372`).

**Layer 3 verdict:** findings — 3 (L3-1 minor; L3-2 and L3-3 not shown by reading); no blocker or major

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md Pre-1.0 | no `@Deprecated`, no transitional comments | conforms | leaf; one "Before the fix" history sentence in `...HookFailureTest` Javadoc (borderline) |
| AGENTS.md InvalidExceptionUsageRecipe | production broad catch listed, adjacent directive and reason | conforms | `CachingDiscoveryResolver.java:402-413` (re-read), `AGENTS.md:318-326` (leaf) |
| AGENTS.md InvalidExceptionUsageRecipe | a test-side broad catch only in integration-test code; suppression is not a strategy | deviates | `...FailureHandlingTest:321`, `...HookFailureTest:320` (`RuntimeException`), `...RefreshRegressionTest:287` (`RuntimeException \| Error`), re-read by `git grep` |
| AGENTS.md Test Class Size | at most one package-private `Support` fixture for shared setup | deviates | the harness (caller thread runner, `awaitParked`, scripted delegate, `Hold`) is copied into three sibling classes (leaf, not re-read); `invalidSettings()` duplicates `invalidDurations()` (leaf) |
| AGENTS.md Testing, Parameterized, injected time, class size | JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for 3+, no sleeps, <=500 lines | conforms | leaf (`git grep` clean, largest class 416) |
| Javadoc / documentation accuracy | the text matches the code | deviates | L2-1 to L2-6 |
| Security (`LogSanitizer`, class name only in status) | attacker text sanitised | conforms | `CachingDiscoveryResolver.java:430-431`, `:478-480` (re-read for the sanitise calls at `:435-436` and `issuerOf` `:478`) |
| ADR-0006 overridability | still calls the public `delegate.resolve()` | conforms | `CachingDiscoveryResolver.java:400` (re-read) |
| Standing constraint | default behaviour and existing tests unchanged | conforms | `DiscoveryResolver`, `TokenLifecycleManager`, `RefreshFlow`, `RevocationClient` and the three named test classes are not in the diff |

## Metrics and Anomalies

- Tokens: 26,547,218 total (the lane's figure; no per-phase breakdown arrived).
- Duration: 69,006 s of wall time (about 19.2 h; the lane's figure).
- Anomalies: functional-review and code-review were both closed by operator override at every head, because of one `weakened_test` row (a false positive, see Layer 2); the lane's formatter and OpenRewrite churn was split out and merged first as
  #909 and #910; a post-merge CI wait timed out on the non-gating JMH benchmark job only (the lane's account); some subagent commits carry a `Co-Authored-By` trailer naming a different model than the dictated one (the lane's account).
- `script_failure` clusters (read in the archived plan's work log, `work.log:593` and `:609`): `manage-status` exited 1 with a guarded refusal at the pre-merge ledger check ("1 of 6 worktree-branch commit(s) since diverging from main carry no kind=change
  ledger row"), working as designed; `manage-tasks list --deliverable all` exited 2, an argparse rejection (`--deliverable` takes an int), a recurrence of lesson 2026-11-11-12-003.

## Routing and Merge Behavior

- Review: CodeRabbit raised five inline findings (all in the lane's account answered and resolved before the merge; four fixed with regression tests, one answered "not changed": the `RefreshResultVerifier` ordering sentence in `token-handling.adoc`, the lane's reply states that the original sentence was correct). Sourcery refused every review on quota (the lane's account). Re-read through
  `runbook_lane review-gate --pr-number 912`: `unresolved_review_threads` 0, `review_body_findings_total` 5, `review_body_findings_handled` **not re-verified** (the dispositions live in the run's progress record, which a plan-marshall lane does not keep, and the provider alone does not reproduce them), `merge_allowed: false`
  with `reason: body_findings_undisposed`. The landing wrote all three counts as `unknown`, so there is no false claim, only a missing fact. `ci pr comments` reports 18 comments with `unresolved: 7` beside the gate's 0 unresolved threads of 5; the difference was not reconciled (the extra items are review bodies and issue comments, not threads, by reading the counts; not checked per item).
- CI/merge: merged by the merge queue as `dcbdcfb6` after #909 (`3bc0e33b`) and #910 (`b3a08dfe`); no rebase conflict is recorded in the landing and none was looked for.
- Sonar: the landing's `sonar_open_new_issues=0` is the lane's roundtrip count. The orchestrator's read (`read_open_issues --pr 912 --project cuioss_TokenSheriff`) is **undecidable** (`count_status_reason: head_revision_unavailable`, analysis revision `e6380e6f`), so the figure is recorded as `unknown`, not `0`.
- Surface collision: none observed. PLAN-08 ran alone in its pairing (PLAN-03 held back on the operator's overlap answer). The merge moves anchors that PLAN-03 and PLAN-09 inherit (see the queue annotation in `epic.md`).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-08 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-08 --field pr --value "#912"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-08 --field landing --value landings/PLAN-08.md`
- [x] row `plan_marshall_plan_id` stamped — `discovery-cache-verifier-hardening`
- [x] epic.md narrative reconciled (queue annotation, Decisions, Open Defects, Watches)
- [x] two lessons promoted: 2026-11-17-06-001 (the finalize integrity check counts import lines) and 2026-11-17-06-002 (single-flight cleanup scope and joiner freshness)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- The residual text errors L2-1 to L2-6 (client Javadoc, `retrieval-flow.adoc`, `LogMessages.adoc`, `threat-model.adoc`, ADR-0026 summary), the three test-rule deviations and L3-1: no staged spec is open for them. PLAN-03 is at the scope guard and may not take them without a split; PLAN-09 is the validation module. Routing is the operator's call (recorded in `epic.md` Open Defects).
- L3-2 and L3-3 are not shown by reading; they need a run (a deployment test with ArC's default removal; a `@QuarkusTest` with an invalid `5m` value). Recorded as Watches.
- The finalize integrity check's import-line counting: lesson 2026-11-17-06-001.
