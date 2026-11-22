# Landing Analysis: PLAN-10 — Client and discovery-cache residuals

epic: api-sheriff-seams
workstream: WS-01
pr: #920 (https://github.com/cuioss/TokenSheriff/pull/920), merged through the merge queue as dd68517b13370b08484a5797f451da0482380b7a

> Landing record for one shipped plan. Lives at `landings/PLAN-10.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-10-client-and-cache-residuals-008.md` (lane `plan-marshall`, plan id `plan-10-client-and-cache-residuals`), plus seven candidate-lesson messages (`-001` to `-007`).
PR state read through `ci pr view` (`state: merged`, merge commit as above). The merge commit's first parent is `2b4b3f1f` (the ledger land #922), so the plan's diff is `git diff 2b4b3f1f dd68517b` (39 files, +2230/-1233).
`inbox landing-check`: `complete: false` (missing `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues`, all `unknown`); `surface_delta` is `unmeasured`, so the declared-versus-realized comparison below was made by hand.
The `steps` fact was split on its last colon; every element is a bare step id with outcome `done`. The lane's account of its own run was not re-run. The archived plan directory (`2026-11-18-plan-10-client-and-cache-residuals`) was read for `logs/work.log`, `logs/decision.log` and `metrics.md` only.

## Deliverable Fidelity vs Spec

The spec lists five deliverables; the landing reports `deliverables_total=8` and `deliverables_done=8` (the outline split them; the lane's residue adds that the plan finished with 23 tasks, eight outline deliverables plus follow-up tasks for PR #920's Sonar and CodeRabbit findings).
The table follows the spec's numbering.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. The discovery cache's shipped text matches its code | shipped-as-specified, with two documentation leftovers (L2-2, L3-6) | the interrupt and recovery logging text, the third single-flight outcome and the duplicate-WARN cause trace to the code (`CachingDiscoveryResolver.java:100-121` against `refresh()` and `recordFailure`, `retrieval-flow.adoc:274-277`, `:355-360`; leaf); the "thirteen refusals, five log, eight do not" count matches `DiscoveryResolver.resolve` (leaf); `ClientLogMessagesIdentifierTest` formats INFO records 1 to 3 and carries a zero-padded negative control (leaf); the threat-model row 12.2 and ADR-0026's summary and tags are accurate (leaf) |
| 2. The discovery cache's tests and edges follow the repository's rules | shipped-as-specified, with the PLAN-04 nitpicks not changed | one package-private `CachingDiscoveryResolverSupport` fixture replaces the three copies; `AGENTS.md` now sanctions it as a test boundary (`AGENTS.md:331-334`, diff re-read; the rule's "in integration-test code only" qualifier is gone) and the fixture catches `RuntimeException \| Error` under the rewrite directive with a rationale comment (`CachingDiscoveryResolverSupport.java:68-82`, re-read); the 365-day test, the interrupt edge accepted and tested, and the removed-injection and real-converter startup tests are the leaf's reading (negative controls named). `DiscoverySpecIT` and `CachingDiscoveryResolverAdversarialTest` are in the declared surface and **untouched**; the PR body records them as not fixed here |
| 3. PLAN-01's minor residuals | shipped-as-specified | requirements delivery rationale, "SecureRandom holders" wording, `NativeImageClassScanner` `@throws`, the two 400-branch tests in `ClientNativeImageSpecIT` and the removed Hamcrest import (all leaf; the PR body admits the two new tests were not run against an endpoint that answers 200 and not in a native build) |
| 4. PLAN-02's minor residuals | shipped, with deviations (L2-3 to L2-6, L3-4, L3-5) | `@since` on `validateScopes` and the refresh overload; the security-exclusion text; the three-argument `build` delegates to the four-argument one (`AuthorizationRequestBuilder.java:100-125`, re-read) with routing tests; the redirect-URI guard exists once; the five test sites reworded; the raw U+00A0 literals replaced; `ClientConfigurationTest` split into three classes of 442, 434 and 238 lines (leaf; `@Test` counts re-read: 52 methods became 24 + 20 + 11 = 55) |
| 5. Every review-bot body finding on the shipped PRs has a recorded disposition | not shown by reading | the dispositions live in PR threads; `ci pr comments` shows triage-disposition comments on all seven PRs (#898, #899, #901, #906, #907, #908, #912; the latest at 2026-11-18T13:09:19Z on #906), but no per-finding table for #920's plan was found in the archived plan directory (PLAN-11 left one, `work/d6-review-findings.md`), so completeness per finding is not shown |

Diff files outside the declared Expected Surface (28 declared paths, 39 realized): `AGENTS.md`; `doc/developer/test-impl-mapping.adoc` (the consequence of the split); `StepUpHandler` (the single redirect-URI guard);
the new or split test classes (`ClientConfigurationTransportTest`, `ClientConfigurationScopeValidationTest`, `ClientLogMessagesIdentifierTest`, `DiscoveryCacheDisabledRemovedInjectionDeploymentTest`, `HttpJwksLoaderInitialRetryGuardOrderTest`), the reworded `AuthorizationCodeFlowOptionsTest`, `AuthorizationRequestBuilderParametersTest`, `AuthorizationRequestBuilderTest`, `RefreshFlowScopeOverrideTest`;
and **six validation-module and deployment files no spec deliverable names**: `HttpJwksLoader` (+74) with `HttpJwksLoaderInitialRetryGuardOrderTest`, `JwtPropertyKeys` with `JwtPropertyKeysTest`, and `TokenSheriffProcessor` with its build-step test. By the lane's account (inbox `-003` and the PR body) they are operator-decided fixes of CodeRabbit findings on the shipped PR #906 (D5's "fixed here"); the diff does not show that tie.
Declared but not touched: `DiscoveryResolver`, `StoredToken`, `CachingDiscoveryResolverAdversarialTest`, `TokenSheriffClientProcessor`, `ClientEngineNativeEndpoint`, `DiscoverySpecIT`. (`doc/LogMessages.adoc` and ADR-0026 are declared; the leaf's layer-1 claim that they were not is contradicted by the surface read.)

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface), and the orchestrator re-read the points marked "re-read" at `dd68517b` (file reads and `git show`) before writing them here.
Points marked "leaf" are the leaf's reading that this analysis did not re-read: they are recorded as the leaf's account, not as settled. The reading is **by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf — reason: every full-ship landing gets its own leaf (merged-code depth, no shared context with another landing's read). **Disclosed deviation:** the leaf reports that it made scratch copies and one comparison script under the session scratchpad, outside the repository, although the brief said no write; no repository, plan, ledger or corpus write was made, and it used no `.plan` text as evidence.
- **Verdict:** layer 1 findings (4 minor, 2 info); layer 2 findings (8 minor, the rest info); layer 3 findings (one major risk, not demonstrated, 5 minor, the rest info). No blocker. The leaf's major is a risk by reading whose trigger (a native build) is unproven.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | D5's "fixed here" is the only route by which validation-module code changed (`HttpJwksLoader`, `JwtPropertyKeys`, `TokenSheriffProcessor`), and the spec's standing constraint says default behaviour stays unchanged except where a listed decision says so; none of the outline's decisions covers them (`JwtPropertyKeys` changes list order, L3-3) | checked (surface read and diff) | refutes (the constraint and D5 disagree) | spec D5 and Standing Constraints; the realized-versus-declared list above |
| L1-2 | The Expected Surface did not declare `AGENTS.md`, `test-impl-mapping.adoc`, `StepUpHandler` or the validation and deployment files above, although D2 names `AGENTS.md` ("say so in AGENTS.md") and D4 names the redirect-URI guard | checked (surface read) | refutes | surface list above |
| L1-3 | D4 asks for the `ClientConfigurationTest` split (a pure move under `AGENTS.md`) and, on the same tests, message assertions and a `LogSanitizer`-output test, without sequencing them, so a squash cannot show purity | checked (leaf) | refutes | spec D4 |
| L1-4 | D1's premises held at the diff base (mixed identifier forms, the third outcome missing, "most failed attempts", the threat-model omission, ADR-0026's metadata) | checked (leaf) | confirms | leaf |
| L1-5 | The Done-when Sonar clause asks for an open-issue read of `0`; the lane's "0 new-code issues" is a different population, and the provider read is `undecidable` | not_shown_by_reading | none | `sonar read_open_issues` for #920, see Routing |
| L1-6 | The spec's operator decision on a dedicated reason for a missing PAR endpoint is not readable in the diff: the lookup stays the generic `ClientProtocolException` (`AuthorizationCodeFlow.java:236-238`, re-read, message unchanged) | checked (re-read) | refutes (what the decision said is not recorded here) | `AuthorizationCodeFlow.java:236-238` |

**Layer 1 verdict:** findings — 6 (L1-1 to L1-3 and L1-6 minor, L1-4 info, L1-5 not shown by reading)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `CachingDiscoveryResolver`, `retrieval-flow.adoc`, `LogMessages.adoc`, `threat-model.adoc`, ADR-0026 (leaf) | `ClientLogMessagesIdentifierTest` (leaf); the logging, "most" and third-outcome statements are documents, not pinned by a new test | LogMessages re-padding beyond D1 (L2-1); stale `TokenSheriff-010` (L2-2) | checked, with two gaps |
| 2 | `CachingDiscoveryResolverSupport` and `AGENTS.md:331-334` (re-read) | interrupt test with a negative control, the 365-day test, the removed-injection test with a matched control, the real-converter tests (leaf) | the PLAN-04 nitpicks untouched | checked |
| 3 | the four documents and `NativeImageClassScanner` (leaf) | the two new 400-branch IT tests (leaf; run-only whether they fail) | none | checked |
| 4 | `AuthorizationRequestBuilder:100-125` (re-read), `AuthorizationCodeFlow:236-252` (re-read) | routing tests, scope and `LogSanitizer`-form tests (leaf) | the split is not a pure move (L2-3); a new untested branch (L2-4); two items with no hunk (L2-5) | checked, with deviations |
| 5 | n/a | n/a | not shown by reading | not shown |

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L2-1 | `doc/LogMessages.adoc`'s validation INFO table and header (`:38-50`), the Quarkus INFO table (`:150-175`) and the `TokenSheriff-173` cross-reference (`:129`) were re-padded to the unpadded form; D1 named only the client rows, and no test pins the validation or Quarkus identifier form (the leaf judges the form probably right) | checked (`:50` and `:129` re-read: `TokenSheriff-10`) | refutes | `doc/LogMessages.adoc:50`, `:129` |
| L2-2 | `token-sheriff-validation/doc/configuration/openid-discovery.adoc:227` still says `TokenSheriff-010` while `LogMessages.adoc` now says `TokenSheriff-10`; `TokenSheriffQuarkusLogMessages` Javadoc still says `001-025` (leaf), so a reader searching the log for the documented form meets two spellings | checked (re-read) | refutes | `openid-discovery.adoc:227` |
| L2-3 | The `ClientConfigurationTest` split is not a pure move: the three classes hold 24 + 20 + 11 = 55 test methods against the original 52 (`@Test` and `@ParameterizedTest` counts, re-read); the leaf names four scope-class methods that gained or lost assertions and three added methods (`shouldNameNoBreakSpaceByCodePoint`, `shouldKeepSanitizedEntryOutOfMessage`, `shouldRefuseNullScopeList`) | checked (counts re-read; the method names the leaf's) | refutes | `ClientConfigurationScopeValidationTest.java` |
| L2-4 | The new `IllegalStateException` branch for an override of `parameters(...)` that skipped the authorization-endpoint check is reachable only through such a subclass and no test names it; it also changes the exception type for such a subclass from the documented `ClientProtocolException` | checked (branch re-read at `AuthorizationCodeFlow.java:245-249`; "no test names it" the leaf's) | refutes | `AuthorizationCodeFlow.java:245-249` |
| L2-5 | Two D4 items have no hunk: the `CLIENT-27` traceability row order (the leaf finds the rows already in order at `dd68517b`) and the refusal tests that assert only the type (`RefreshFlowScopeOverrideTest:285` still asserts `IllegalArgumentException` and zero calls only, re-read) | checked (`:285` re-read) | refutes | `RefreshFlowScopeOverrideTest.java:285` |
| L2-6 | Pre-1.0 wording: test method names keep the framing (`shouldKeepDefaultFlowUrlByteIdentical` at `AuthorizationCodeFlowOptionsTest:160`, re-read; the leaf names three more); no raw U+00A0 remains (leaf) | checked (one name re-read) | refutes | `AuthorizationCodeFlowOptionsTest.java:160` |
| L2-7 | PLAN-11's assertion survived the rebase: `shouldAcceptBareAllowedHostsInCanonicalForm` keeps the canonical list `List.of("keycloak", "idp.internal", "10.0.0.5", "[::1]", "[2001:db8::1]")` and the `KEYCLOAK` equals `keycloak` assertion; all 52 pre-split methods exist in the three classes (leaf comparison) | checked (re-read) | confirms | `ClientConfigurationTransportTest.java:364-384` |
| L2-8 | The lane claims the diff cannot bear out or refute: eight deliverables and 23 tasks, both review steps closed by operator override after 15 firings each with 0 findings accepted (the work log shows eight `loop_back` records each, see Metrics), CI green with 5644 tests, Sonar 0 new-code issues, the D5 dispositions. Items such as L2-2 and L2-3 are what those reviews would normally catch | not_shown_by_reading | none | lane report |

**Layer 2 verdict:** findings — 8 (L2-1 to L2-6 minor, L2-7 info confirming, L2-8 not shown by reading); D5 is not shown by reading

### Layer 3 — Correct

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L3-1 | **Risk (the leaf's major), not demonstrated:** `DPOP_SUB_PROPERTIES` is now derived by reflection in `ISSUERS.<clinit>`, and `declaredKeysUnder` rejects an empty result; `IssuerConfigResolver` iterates that list for the orphaned-DPoP fail-closed check, so if `getDeclaredFields()` yielded nothing (the leaf's trigger: a native build where the reflective registration does not take effect) `dpop.required=true` without `dpop.enabled` would be silently ignored. The old explicit list could not degrade that way. The code is as described; whether the list is empty in a native image is not shown by reading, and the PR body says no native run demonstrated it | checked (code re-read; the native behaviour not shown) | refutes (a fail-open path exists in the code) | `JwtPropertyKeys.java:64-78`, `:400`; `IssuerConfigResolver.java:441-452` |
| L3-2 | `constantValue` returns `null` for a `public static final String` that is not a compile-time constant and is declared after line 400, because the list is built during `ISSUERS.<clinit>`; `value.startsWith` then throws a `NullPointerException` and `ISSUERS` fails to initialise. The test fixture covers only a non-final field | checked (re-read: `(String) field.get(null)` followed by `value.startsWith(base)`) | refutes | `JwtPropertyKeys.java:70-71`, `:80-86` |
| L3-3 | The order changed from declaration order to natural sort, so with more than one orphaned `dpop.*` value the property named in the startup failure at `IssuerConfigResolver.java:442-449` changes; neither the PR nor "default behaviour unchanged" says so | checked (leaf for the order; the sort re-read at `:76`) | refutes | `JwtPropertyKeys.java:76`; `IssuerConfigResolver.java:442-449` |
| L3-4 | The three-argument `build` now delegates to the four-argument one; a subclass whose four-argument override calls the three-argument form for defaults (valid before) now recurses until `StackOverflowError`. The Javadoc says an override of the four-argument form also governs this one but does not name the hazard. Decided by the operator; pre-1.0 | checked (delegation re-read) | refutes | `AuthorizationRequestBuilder.java:100-125` |
| L3-5 | `HttpJwksLoader.performInitialLoadRetryCycle` reads as correct: the eligibility check and the non-blocking compare-and-set on `fetchInFlight` share one `lifecycleLock` section, no code waits on the guard while holding or wanting the lock, the guard is released in a `finally`, and the unknown-kid and periodic paths are unchanged (diff re-read, `:1374-1430`); the change is limited to the opt-in initial-load retry, so default behaviour holds. The new test and its control are the leaf's reading | checked (diff re-read) | confirms | `HttpJwksLoader.java:1374-1430` |
| L3-6 | The interrupt edge's text (`CachingDiscoveryResolver` Javadoc and `retrieval-flow.adoc:300-305`) omits one effect: a thread whose interrupt flag is set and that calls `resolve()` on a stale, non-backoff cache reaches `delegate.resolve()` on every call, because no backoff is written, while the Javadoc says the upstream load is bounded to one request per refresh; the test sets the flag and clears it only after `assertThrows` returns, with no `try`/`finally`, so a failing assertion leaks the flag (re-read for the test) | checked (test re-read; the cache path the leaf's) | refutes | `CachingDiscoveryResolverFailureHandlingTest.java:222-242` |
| L3-7 | The list of shared back-channel transport refusals that log "DEBUG at most" names four; `BackChannelHttp.send` also maps a malformed response framing to a `TransportException` with a DEBUG line (PLAN-11's change), so the list is incomplete | checked (leaf; `BackChannelHttp` re-read earlier at `d8aba512`) | refutes | `doc/LogMessages.adoc:238` |
| L3-8 | `WireValues` still echoes the sanitised value into the exception (`"unsupported " + parameter + " value: " + LogSanitizer.sanitize(value)`, re-read) and `AuthorizationRequestOptionsTest` now pins that echo; sanitised, not a fixed text | checked (re-read) | refutes (against "refusal texts never echo input", sanitised) | `WireValues.java:68` |
| L3-9 | Not shown by reading: whether the derived list is non-empty in a native image, whether the shipped Quarkus converter accepts `ms` and `1d` (the accepted-forms table), whether the new tests fail without their change, the full `-Ppre-commit` plus `clean install`, `RefreshPathCoverageGuardTest` and the Sonar count | not_shown_by_reading | none | run-only |

**Layer 3 verdict:** findings — 9 (L3-1 major risk not demonstrated; L3-2, L3-3, L3-4, L3-6, L3-7 minor; L3-8 info; L3-5 confirming; L3-9 not shown by reading). No blocker. **Reported to the operator in this turn; nothing fixed inline and nothing staged** (a layer-3 defect is staged only on the operator's word).

### Conformance

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md:331-334` / operator decision | a test-side broad catch is sanctioned only in integration-test code | deviates | the rule now also sanctions one unit-test fixture (the operator's outline decision, diff re-read); the fixture also catches `Error` for one runner, which the bullet does not say (`Support:68-82`, re-read) |
| `AGENTS.md:177-180` | test-class splits are pure moves | deviates | 52 methods became 55 (L2-3) |
| `AGENTS.md` | test classes at or under 500 lines | conforms (leaf) | largest touched class `AuthorizationRequestBuilderTest` at 494 lines |
| `AGENTS.md` / junit-core | JUnit 5 AAA, no Mockito or Hamcrest, parameterized for three or more variants, injected time | conforms (leaf) | the Hamcrest import removed; parameterized tables of 7, 10 and 15 rows |
| `AGENTS.md` / junit-core | a negative control per regression test | conforms (leaf) | named for the interrupt, routing, identifier, removed-injection, guard-order and key-derivation tests; the 365-day test has none beyond its phases |
| `AGENTS.md` pre-1.0 | no `@Deprecated`, no transitional or history-framed wording | deviates | prose reworded; test method names keep "ByteIdentical" and "Unchanged" (L2-6) |
| `AGENTS.md` security / CWE-117 | attacker text sanitised; refusal texts fixed, never echo input | deviates | `WireValues:68` echoes the sanitised value (L3-8) |
| Spec | no new dependency; no new LogRecord | conforms (leaf) | no pom changed; only DEBUG text lines added |
| Spec | `RefreshPathCoverageGuardTest` and the coverage includes still pass | not_shown_by_reading | the pom is unchanged; passing needs a run |

## Metrics / Anomalies

| Fact | Value | Source |
|------|-------|--------|
| Tokens | 46,001,507 (6-finalize 34,271,881 of them) | landing and `metrics.md`, read |
| Wall clock | 134,127 s reported (about 37h15m); 5-execute 19h02m reported, 17h23m idle; 6-finalize 17h27m reported | landing and `metrics.md`, read |
| Model / effort | `claude-sonnet-5-5`, `medium` (start-time self-reported; the lane says the landing-time refresh could not measure the model) | landing facts and residue |
| Plugin / executor | 0.1.2133 both | landing facts |
| Finalize | 19 `loop_back` step records: `code-review` 8, `functional-review` 8, `branch-cleanup` 2, `ci-verify` 1 (`work.log`, counted) | `work.log` |
| `script_failure` lines | five, all classified in the `script_failure` Watch (`work.log:240,256,281,617,618`, read), plus `work.log:654` | `work.log` |

Anomalies:

- The PR sat in the merge queue from 10:44Z, was ejected when GitHub cancelled the hung merge-group run at 11:15:37Z, and was then CONFLICTING because #921 had landed (`decision.log:172-173`, read); recovery was an operator-authorized rebase and force-push and a full re-run of the head-dependent finalize steps (lessons 2026-11-18-13-001 and -002). **The collision is the one the `next` gate predicted** (PLAN-10 and PLAN-11 shared six files and the operator answered "emit"): `doc/client/specification/test-strategy.adoc` and `ClientConfigurationTest.java`.
- The `branch-cleanup` record reads "rebase deferred to queue" although the run rebased onto main with an operator-authorized, conflict-resolving rebase (the lane's residue; `decision.log:173` confirms the authorization); `finalize-step-sync-baseline` records only the first, trivial rebase.
- The lane's account that `functional-review` and `code-review` closed through an operator override after 15 firings each with 0 findings accepted is not matched by the log's eight `loop_back` records each; the two counts may measure different things (firings against recorded loop-backs) and were not reconciled.

## Routing / Merge Behavior

| Fact | Landing says | Provider re-read |
|------|--------------|------------------|
| PR / merge | #920, `merged` | `ci pr view`: `state: merged`, merge commit `dd68517b`, head branch `feature/plan-10-client-and-cache-residuals`; **confirmed** |
| `unresolved_review_threads` | `unknown` | `review-gate --pr-number 920`: `0`; **no false claim** (the landing asserted nothing) |
| `review_body_findings_total` / `_handled` | `unknown` / `unknown` | `review-gate`: total `8` (coderabbit `inline-1`, `inline-2`, `additional-1`, `nitpick-1`, and four more), handled `0`, `merge_allowed: false`, `reason: body_findings_undisposed`; the gate's `handled` is the one number a provider cannot reproduce (it reads dispositions the finalize steps do not record), so it is **not re-verified**. `ci pr comments` shows 26 comments, 11 unresolved, 6 review threads, beside the gate's 0 unresolved threads; not reconciled per item |
| `sonar_open_new_issues` | `unknown` | `sonar read_open_issues` for #920: `undecidable` (`head_revision_unavailable`, analysis revision `303ed9ae`); recorded **unknown**; the lane's roundtrip `new_code_issue_count=0`, `count_status=confirmed` is its own, and it does not count security hotspots |
| Merge path | merge queue | `decision.log:185`: "merged by the platform merge queue as dd68517b after 498s" (second attempt, read) |

## Reconciliation

- Queue: PLAN-10 `running` → `shipped`; `pr` `#920`; `landing` `landings/PLAN-10.md`; `plan_marshall_plan_id` `plan-10-client-and-cache-residuals` (carried by the landing message).
- Parallelization: the overlap the gate predicted between PLAN-10 and PLAN-11 happened at landing (two files, one rebase, about 1h50m); the operator's "emit" answer accepted that risk. The realized surface of PLAN-10 also shows six undeclared files in the validation and deployment modules that the gate could not predict, none of which any other plan of this epic touched concurrently. The spec is not edited (it is shipped).
- Queue state: PLAN-01 to PLAN-11 are all `shipped`; nothing is staged and nothing is running, so the queue is empty.
- `epic.md`: the PLAN-10 Queue annotation and Decisions entry, the Open Defects entries that were "staged as PLAN-10 deliverable N" (what closed and what remains), and the new PLAN-10 residuals entry were written with this landing; settled narrative relocation is the `cleanup` run's.

## Follow-ups

None is staged: the operator's word is needed for any new plan, and PLAN-10 was part of the last two queued. Items carried to `epic.md` Open Defects: the `JwtPropertyKeys` reflective-list risk and its two lesser faults (L3-1 to L3-3); the stale `TokenSheriff-010` and the unpinned identifier forms (L2-1, L2-2); the non-pure split (L2-3); the untested `IllegalStateException` branch and the recursion hazard (L2-4, L3-4); the interrupt-edge text and test (L3-6); the PAR-endpoint decision and the PLAN-04 nitpicks not visible in the diff (L1-6, D2); D5's per-finding dispositions (not shown).
