# Landing Analysis: PLAN-09 — Public helper residuals

epic: api-sheriff-seams
workstream: WS-02
pr: #916 (https://github.com/cuioss/TokenSheriff/pull/916), merged as 67f8e480960dad36e3b784cb1feb123301165554

> Landing record for one shipped plan. Lives at `landings/PLAN-09.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-09-public-helper-residuals-014.md` (lane `plan-marshall`, plan id `plan-09-public-helper-residuals`), plus thirteen candidate-lesson
messages (`-001` to `-013`). PR state read through `ci pr view` (`state: merged`, merge commit as above, base `main`, 5281 tests in the PR body's Test Plan, the lane's figure).
`inbox landing-check`: `complete: false` (missing `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and `sonar_open_new_issues`, all `unknown` in the landing);
`surface_delta` is `unmeasured` (neither the declared nor the realized surface was supplied), so the declared-versus-realized comparison below was made by hand from `git diff --stat dfab40cb 67f8e480`
(32 non-ledger files). The `steps` fact was split on its last colon; every element is a bare step id with outcome `done`. The lane's account of its own finalize (the rebase and force-push at the
branch-cleanup gate, the merge through the queue instead of the documented immediate merge, the post-merge CI wait that hit the Bash ceiling) is its own and was not re-run. The archived plan directory
(`2026-11-17-plan-09-public-helper-residuals`) was read for its work log and metrics only.

## Deliverable Fidelity vs Spec

The spec lists five deliverables; the landing reports `deliverables_total=5`, `deliverables_done=5`, and its functional-review facts say 2 met and 3 met with residuals, 0 not met. **The spec's own text is not
fully met by the merge**: the landing's Residue names none of the five gaps below (L2-1 to L2-5), so `5 of 5` overstates what the spec asked for. The four operator decisions at outline were taken by the lane and
were read off the merged code and the PR threads, not from a record of the operator's answer: **where an invalid annotation scope fails** — at deployment (a new `@BuildStep`), with a defined 500 as the request-path backstop;
**the guard-before-window order** — a read-only window pre-check before the guard, and a failed hand-off still burns the window; **how the body handler's object count is bounded** — one growable array that doubles (not a coalescing step, not an object-count statement),
with the memory bound restated (L2-7); **the `error_description` grammar** — kept as the RFC 9110 quoted string.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Invalid `@BearerAuth` / `@BearerToken` scope never an unhandled exception | shipped-modified: more than the spec asked (a deployment check **and** a 500 backstop, one new public method), with one verification gap (L2-4) | `TokenSheriffProcessor` (new build step; `refusal` calls `BearerTokenResponseFactory.insufficientScopeChallenge`, `:430`, re-read) and the producer calls the same method (`BearerTokenResponseFactory.java:222`, re-read; declared `public static` at `:262`, re-read); the request path answers a set the deployment check could not resolve with a 500 and a `TokenSheriff_Q-201` ERROR (leaf; `doc/LogMessages.adoc:198`, re-read). The frozen criterion "no new public API" was waived by the operator (the lane's account; the waiver is not visible by reading). Tests: `BearerScopeValidationBuildStepTest`, `BearerScopeValidationBootTest`, `BearerScopeValidationRefusalBootTest`, `BearerTokenResponseFactoryUnrepresentableScopesTest` (leaf) |
| 2. Unknown-`kid` claim path does what its documents say | shipped-modified, with one documentation gap (L2-3) | `HttpJwksLoader.scheduleUnknownKidRefresh` (`:870-909`, re-read): a read-only pre-check before the guard, the guard, a re-check of the window and a window CAS, each lost claim releasing the guard, and an `execute` failure releasing the guard and keeping the window (the burn). The Javadoc, ADR-0030 and `openid-discovery.adoc` state the order and the burn (the first re-read, the others leaf). Tests: `HttpJwksLoaderExecutorHandOffTest` (gated clock, burn test; leaf) |
| 3. Helpers bound their own work on hostile input | shipped-modified: the memory bound was weakened instead of kept (L2-7), with two test gaps (L2-5, L2-6) | `BearerChallenge` checks the lengths before the join (`:202-205`, before `:216`, re-read) and stops retaining past 65 scopes (`:165`, re-read); `BoundedBodyHandler` keeps one array that doubles up to the ceiling (`:362-374`, re-read); a non-numeric `Content-Length` is a normal failure with a fixed message (leaf, `:179-194`); the header round trip sits in the client test tree (leaf, `InsufficientScopeChallengeRoundTripTest`) |
| 4. Hub documents and decision record match what shipped | shipped-partly: two of the five locations not delivered as specified (L2-1, L2-2) | `doc/README.adoc` lists ADR-0030 and ADR-0031 and the commons README says `COMMONS-1` to `COMMONS-17` (leaf); `IssuerConfigResolver` Javadoc now says the builder refuses `initialLoadRetry` with refresh interval 0 and that the window names the Quarkus key (leaf; both properties already had tests in `IssuerConfigResolverJwksLoaderOptInTest`, not in the diff); ADR-0031 itemisation and `MAX_CEILING` `@since` are missing |
| 5. One test can fail for the property it names | shipped-as-specified | the hostile-issuer test and the signalling-clock control in `LogoutTokenValidatorNumericDateTest`; the 16-digit-mantissa case still does not reach the bound (the parser refuses it upstream) and the test records why, which is the spec's "replace or record why not"; ADR-0029's revision history became Amendments (all leaf) |

Diff files outside the spec's Expected Surface (every one explained by a deliverable, per the leaf): `TokenSheriffProcessor.java` and the three build, boot and refusal-boot test classes (deliverable 1's deployment decision; the deployment module is not in the declaration),
`TokenSheriffQuarkusLogMessages.java`, `BearerToken.java`, `BearerTokenResult.java`, `BearerTokenResponseFactoryUnrepresentableScopesTest`, `META-INF/quarkus-config-doc.adoc`, `BoundedBodyHandlerTestSupport`, `HttpJwksLoaderExecutorHandOffTest`,
ADR-0029, ADR-0030, ADR-0031, `doc/validation/requirements.adoc`, `doc/commons/specification/transport.adoc`, `doc/commons/requirements.adoc` (declared). Declared but not touched: `BearerTokenInterceptor.java`, `BearerTokenResponseFactoryTest.java`,
`HttpJwksLoaderConfig.java` (see L2-3). Wildcard `java.util.concurrent.*` and `java.time.*` imports appear in two touched test classes; the pre-commit formatter produces that form (lesson 2026-10-31-12-005), so it is not recorded as a deviation.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface), and the orchestrator re-read the points marked "re-read" at `67f8e480` (file reads and `git show`) before writing them here.
Points marked "leaf" are the leaf's reading that this analysis did not re-read: they are recorded as the leaf's account, not as settled. The reading is **by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf — reason: every full-ship landing gets its own leaf (merged-code depth, no shared context with another landing's read).
- **Verdict:** layer 1 findings (3 info, one of them a tension between spec clauses); layer 2 findings (7 minor, the leaf's remaining ones info); layer 3 findings (1 minor test-rule deviation, the rest info or not shown by reading). No blocker and no major code defect was found at any layer.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec says nothing widens the public API beyond what a fix needs, and criterion D1 asks the check to reuse the builder's rule. Sharing the challenge construction including realm and `error_description` needed one new public method. The lane records an operator waiver of the frozen clause; the waiver is not visible by reading, only the method is | checked (method re-read; waiver the lane's account) | confirms (the tension is real) | `BearerTokenResponseFactory.java:262` (re-read) |
| L1-2 | D3's two options (coalesce, or state the object-count factor) and the standing constraint "`COMMONS-17`'s ceiling plus one in-flight chunk stays true; a single growable buffer broke it and was reverted in #908" cannot all hold: the lane chose the single growable array, so the constraint was weakened instead of kept. Lesson 2026-11-16-06-001 (the #908 revert) is not in the lessons store, so the spec's reference could not be followed up | checked | refutes (the spec clauses conflict with each other, and the outcome follows the option the constraint forbids) | `BoundedBodyHandler.java:362-374`, `doc/commons/requirements.adoc:152-165` (re-read) |
| L1-3 | The spec's premises for D1, D2 and D3 held on main (the interceptor calls `createErrorResponse()` with no catch, the guard was taken before the window, `subscriberFor` read `firstValueAsLong` outside any `try`, the client main code has no `commons.http` import) | checked (leaf; not re-read at the pre-merge head) | confirms | `HttpJwksLoader.java` (pre-merge), `BoundedBodyHandler.java` (pre-merge) |

**Layer 1 verdict:** findings — 3 (all info; L1-2 is the spec's own conflict and is carried at layer 2 as L2-7)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `TokenSheriffProcessor` (build step), `BearerTokenResponseFactory:111-137`, `:222`, `:262` | the build-step, boot and request-path tests (leaf) | the non-ASCII scope is untested on the new paths (L2-4) | checked, with a gap |
| 2 | `HttpJwksLoader:870-909` (re-read) | `HttpJwksLoaderExecutorHandOffTest` (leaf: the periodic fetch count distinguishes old from new, the burn test fails if the window were refunded) | the config Javadoc burn statement (L2-3) | checked, with a gap |
| 3 | `BearerChallenge:163-167`, `:194-206`, `BoundedBodyHandler:362-378`, the `Content-Length` handling | `BearerChallengeTest`, `BoundedBodyHandlerTest`, `InsufficientScopeChallengeRoundTripTest` (leaf) | L2-5, L2-6, L2-7 | checked, with deviations |
| 4 | the doc files, `IssuerConfigResolver:605-646` (leaf), `BoundedBodyHandler:110`, `:204` | n/a (documents) | L2-1, L2-2 | partly delivered |
| 5 | the two test classes | `JwksEndpointHealthCheckIssuerStatusFailureTest:209-217`, `LogoutTokenValidatorNumericDateTest:132-175` (leaf) | none | checked (leaf) |

Lane claims against the diff: the 500 backstop and the deployment build step, the new public method, the read-only pre-check with a burned window on a failed hand-off, the doubling array with a revised bound, and the two accepted deployment-check limits (an interface default method
and a concrete-typed override of a generic method, both documented, no fixture for the first) are borne out (leaf, with the code points above re-read). The `weakened_test` row on `JwksEndpointHealthCheckIssuerStatusFailureTest.ConfiguredIssuer::checkConfigured` is a counter artefact by the leaf's reading
(the issuer assertion moved into `assertWarnNamesIssuer`, still called by four tests); the lane's count of 12 rows at six heads is its account, not checked. Five gaps against the spec's own text are **not in the lane's report**: L2-1 to L2-5.

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L2-1 | D4 first bullet not delivered: ADR-0031's "Changed default behaviour" still omits the 100-year `LogoutTokenValidator.MAX_FRESHNESS_WINDOW`, the unchanged-JWKS discard moving from WARN to DEBUG, the health-check WARN without a stack trace and with a sanitised issuer, and the client body handler's switch to a chunk list. A search of ADR-0031 for those terms finds nothing | checked | refutes | `doc/adr/0031-…adoc:155-187` (re-read by search) |
| L2-2 | D4 asks `@since` on `OverLimitPolicy`, `BoundedBody` and `MAX_CEILING`. The merge adds it at `:110` and `:204`; `MAX_CEILING` (`:94-97`) still has none | checked | refutes | `BoundedBodyHandler.java:94-97`, `:110`, `:204` (re-read) |
| L2-3 | D2 asks the window burn stated in ADR-0030 **and the config Javadoc**. `HttpJwksLoader`, ADR-0030 and the discovery doc state it; the builder Javadoc of `unknownKidRefreshWindow` still ends "A rejected hand-off releases the guard and never throws" and the file is not in the merge | checked | refutes | `HttpJwksLoaderConfig.java:933-957` (re-read) |
| L2-4 | D1's verification names a non-ASCII scope. The three new test classes contain no non-ASCII scope name (space, quote, backslash, newline, 65 distinct and over-length are covered). The old `createResponse` test covers non-ASCII only on the exception path | checked (search of the three classes) | refutes | `BearerScopeValidationBuildStepTest.java`, `BearerTokenResponseFactoryUnrepresentableScopesTest.java`, `BearerScopeValidationRefusalBootTest.java` (re-read by search) |
| L2-5 | D3 asks a test that delivers many one-byte buffers **in one call**. `ManualDrive.feed` always calls `onNext(List.of(buffer))`, so the loop over several buffers in one `onNext` and a mid-list ceiling crossing are never exercised; the added tests feed one buffer per call. The assembled-body and ceiling checks are real | checked | refutes | `BoundedBodyHandlerTestSupport.java:163-167` (re-read), `BoundedBodyHandlerTest.java:352` (leaf) |
| L2-6 | The retention bound has no failing test: with the `scopes.size() <= MAX_SCOPE_TOKENS` guard removed, a 100,000-scope builder still refuses at `build()` with the same constant message, so `shouldRefuseFarTooManyScopesLikeOnePastTheBound` stays green. ADR-0031 states honestly that the work on the input is not bounded, only retention and copy | checked (by reading, not a mutation run) | refutes | `BearerChallenge.java:165`, `BearerChallengeTest.java:290-304` (re-read) |
| L2-7 | The standing constraint and the Done-when keep `COMMONS-17`'s bound (ceiling plus one in-flight chunk while streaming). The merge keeps one array that grows by doubling and rewrites `COMMONS-17`, `transport.adoc` and ADR-0031 to "below twice the ceiling plus the chunk in flight". That is the single-growable-buffer approach the spec says broke the bound. The review thread carries an operator decision to keep it ("Operator decision: keep the single doubling array and revise the wording", TASK-18; the thread reply is posted under the operator's account and is the lane's record, not an independent one). In effect the worst-case streaming peak doubles. The restated bound is arithmetically true by the leaf's reading | checked | refutes | `BoundedBodyHandler.java:362-374`, `doc/commons/requirements.adoc:152-165` (re-read); the thread in `ci pr comments` on #916 (read) |
| L2-8 | The D2 tests, the hostile-issuer test, the numeric-date control and the header round trip each can fail for the property they name; the D4 hub items for the README ranges are delivered | checked (leaf; not re-read) | confirms | `HttpJwksLoaderExecutorHandOffTest:187`, `:231`, `JwksEndpointHealthCheckIssuerStatusFailureTest:209`, `LogoutTokenValidatorNumericDateTest:132`, `InsufficientScopeChallengeRoundTripTest:175`, `doc/README.adoc:43` |
| L2-9 | Every changed file is explained by a deliverable; no touched test class is over 500 lines (474, 430, 422, 367, 344, 295, 242 — the first five re-counted) | checked | confirms | `wc -l` at `67f8e480` (re-counted) |

**Layer 2 verdict:** findings — 9 (L2-1 to L2-7 minor, L2-8 and L2-9 info)

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `HttpJwksLoader.java:903-906` (re-read) | an `Error` (not a `RuntimeException`) thrown by the executor's `execute` | the hand-off `catch` is for `RuntimeException` only, so the in-flight guard stays held and no further unknown-`kid` fetch is scheduled. The leaf calls it pre-existing (the claim that the old code behaved the same was not checked) | info |
| L3-2 | `HttpJwksLoader.java:870-909` (re-read) | a miss that reads the window as open and then loses the window | it can pulse the guard before its re-check loses, so a periodic cycle that meets it is skipped uncounted. The method Javadoc says a miss inside a claimed window never does that (true), and ADR-0030 and the discovery doc say the pulse remains; the lane's advisory that another Javadoc keeps an unqualified claim was **not re-read** | info |
| L3-3 | `BearerTokenResponseFactory.java:111-137` (leaf) | any other `IllegalArgumentException` from `createResponse` | `createErrorResponse` catches the whole method, so it would be reported as an unrepresentable scope set with `Q-201`. No other source is visible in the branches, so latent only | info |
| L3-4 | `BoundedBodyHandler.java:181` (leaf) | `Content-Length: -5` | parses, is not above the ceiling and is read as a normal body; benign, the JDK client rejects it itself. A non-numeric, `12abc` and overflow value fail with the fixed message (leaf; the test is `BoundedBodyHandlerTest:405`) | info |
| L3-5 | `doc/commons/requirements.adoc:163` (leaf) | the JDK HTTP/1.1 client parsing `Content-Length` itself after the handler returned | the requirement states that observation for JDK 25; no test in the diff pins it | info, not shown by reading |

**Layer 3 not shown by reading:** L3-5; that `@BuildStep` is wired into Quarkus augmentation (`BearerScopeValidationRefusalBootTest` is tagged `quarkus-boot` and was not run); the `-Ppre-commit` result and the 5281 tests (the lane's figure); the native-image effect; Sonar. The leaf read and found sound:
the deployment check and the request path refuse exactly the same set by construction (both call `insufficientScopeChallenge`), the annotation merge matches the interceptor's (method merged with the bean class, `@BearerAuth` not `@Inherited`), the new pre-length check refuses a subset of what the final check refuses and leaves output for valid input byte-identical,
the body handler's byte ceiling and `OverLimitPolicy` behaviour are unchanged, and `Q-201` sanitises its inputs (CWE-117).

**Layer 3 verdict:** findings — 5 (all info; no defect rises above info); no blocker or major

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | pre-1.0: no `@Deprecated`, no transitional comments | conforms | leaf (none in main code; "before this amendment" wording only in ADR text) |
| AGENTS.md | CWE-117 (ADR-0022) for every new or changed log line | conforms | leaf: `Q-201` sanitises the scope list and the refusal message; the new DEBUG line in `HttpJwksLoader` is a constant |
| AGENTS.md | a new `LogRecord` takes the next free id and is in `doc/LogMessages.adoc` | conforms | `Q-201` follows 200; the row is at `doc/LogMessages.adoc:198` (re-read) |
| AGENTS.md | JUnit 5, no Mockito/PowerMock/Hamcrest | conforms | leaf |
| AGENTS.md | parameterized for three or more similar variants | deviates | `BearerScopeValidationBuildStepTest.java:89-121` (re-read: three near-identical refusal tests for method, class and injection point) and the three request-path tests in `BearerTokenResponseFactoryUnrepresentableScopesTest` (leaf) |
| AGENTS.md | injected time, never sleeps | conforms | leaf |
| AGENTS.md | a negative control per regression test | deviates, in one place | L2-6 (the retention bound) |
| AGENTS.md | new or grown test classes at or under 500 lines | conforms | L2-9 |
| spec standing constraint | `COMMONS-17`'s bound stays true | deviates | L2-7 |
| spec standing constraint | native image: plain classes, no reflect-config, no new dependency | conforms | leaf (nothing in the diff); the native build itself was not run |
| spec Done-when | the Sonar open-issue read at the PR head is `0` or each remaining issue is dismissed | not shown by reading | the orchestrator's read is undecidable (see Routing) |
| AGENTS.md | pre-commit sequence, `clean install` | not shown by reading | needs a build |

## Metrics and Anomalies

- Tokens: 33,889,428 total (the lane's figure and the archived `metrics.md` total, which spans populations: main and sub-agent for phases 2 to 6; the billing-weighted total is 159,851,613). Phase 6 is 22,984,365 tokens and 5 h 38 min of the 9 h 14 min reported wall time.
- Duration: 33,262 s of wall time (about 9.2 h; the lane's figure; `metrics.md` reports 9h14m with 1h30m idle).
- Model and effort: `claude-sonnet-5-5`, `medium`, plugin 0.1.2133 (the lane's figures).
- Anomalies: fifteen `loop_back` step records read from the archived work log (`functional-review` seven, `code-review` seven; `ci-verify` ended `loop_back` once, at 12:50:35Z, for six SonarCloud new-code issues that the local `-Ppre-commit` gate had passed — `TASK-014`, read at
  `work.log:495-525`); the branch moved from `57c29c23` to `0926f3ad` on a rebase and force-push after the branch-cleanup gate read an overlap with main (#915) without a content conflict, and the merge went through the queue although the documented route after that verdict is the immediate merge (the lane's account);
  the post-merge `checks wait` ran past the Bash ceiling because a bot check was pending, while the merge-group run had concluded success (the lane's account); the `weakened_test` row was accepted at seven heads (candidate messages `-006` and `-007`); a scope-creep warning was filed three times as the fix-task output grew (`-008`).
- `script_failure` clusters (read in the archived plan's work log, `work.log:44`, `:308`, `:338`, `:371`, `:418`, `:698`, `:715`, `:716`; eight lines, all classified): `manage-findings … query` twice (08:19:04Z under `qgate`, 15:35:22Z at the top level; invented verb), `manage-solution-outline get-deliverable --number` twice (10:14:41Z, 11:39:39Z; the declared flag is `--deliverable-number`),
  `git-workflow commit --record-change` (10:43:13Z; not a declared flag), one `no_sanction` commit refusal at 11:02:32Z (`script_internal_failure`, exit 1), and two `review_completeness` `malformed_bot_flag` rejections three seconds apart (16:08:09Z and 16:08:12Z: a bare token given to a pair flag, then a pair token given to a bare flag).
  Recurrences of lesson 2026-11-11-12-003 (not in the lessons store), of the lesson behind the `no_sanction` Watch, and one new notation (`review_completeness`).

## Routing and Merge Behavior

- Review: CodeRabbit raised inline threads (7 threads, all resolved, each with a reply by the lane) and a review body; Sourcery declined on its weekly quota. Re-read through `runbook_lane review-gate --pr-number 916`: `unresolved_review_threads` 0, `review_body_findings_total` 8,
  `review_body_findings_handled` **not re-verified** (the dispositions live in the run's records, which the provider does not reproduce; the parser reports `handled` 0 and `merge_allowed: false` with `reason: body_findings_undisposed`), so the landing's four `unknown` values are a missing fact, not a false claim.
  The lane's account is that eight CodeRabbit findings were fixed in-run (the thread replies read name a fix commit, `f0fdf6b` for the three in `BearerChallenge`, `BoundedBodyHandler` and `HttpJwksLoaderExecutorHandOffTest`; the others were not each read) and that CodeRabbit was quota-limited on the last push, the merge relying on its finished review on record. `ci pr comments` reports 23 comments with `unresolved: 9` beside the gate's 0 unresolved threads;
  the difference is the review-body items, not threads (counted, not each item reconciled).
- CI/merge: merged by the merge queue as `67f8e480`; the queue re-tested the head and the merge-group run concluded success (the lane's account; run 44122451872 not re-read).
- Sonar: the landing's `sonar_open_new_issues` is `unknown` (the lane's narrative says 0 new-code issues confirmed at head `0baf5776`, issues only, hotspots not counted). The orchestrator's read (`read_open_issues --pr 916 --project cuioss_TokenSheriff`) is **undecidable**
  (`count_status_reason: head_revision_unavailable`, analysis revision `0baf5776`, which equals the lane's last head), so the figure is recorded as `unknown`, not `0`.
- Surface collision: none observed as a conflict. The pairing's predicted seven-file overlap with PLAN-03 (`LogMessages.adoc`, `doc/commons/requirements.adoc`, `IssuerConfigResolver`, `openid-discovery.adoc`, `BoundedBodyHandler`, `HttpJwksLoaderConfig`, `BoundedBodyHandlerTest`) was already known to be over-predicted by three at PLAN-03's landing; PLAN-09 then edited
  `IssuerConfigResolver`, `openid-discovery.adoc`, `doc/commons/requirements.adoc`, `BoundedBodyHandler`, its test and `doc/LogMessages.adoc` (the `Q-201` row) on a branch rebased over #915, and the branch-cleanup gate read no content conflict. PLAN-10, running, shares `doc/LogMessages.adoc` only and re-reads it at HEAD.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-09 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-09 --field pr --value "#916"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-09 --field landing --value landings/PLAN-09.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-09-public-helper-residuals`
- [x] epic.md narrative reconciled (queue annotations, Decisions, Open Defects, Watches)
- [x] two lessons promoted: 2026-11-17-16-001 (bound hostile input before the allocation it sizes; propagate worker-thread test failures) and 2026-11-17-16-002 (a deployment-time check calls the same construction seam as the request path); eleven candidate messages recorded on existing or new Watches
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-09's residuals have no staged spec: the five gaps against the spec's own text (L2-1 to L2-5), the retention-bound test (L2-6), the weakened memory bound and its decision record (L2-7), the parameterized-test deviation, and the lane's thirteen advisory findings (the leaf confirmed those it could test: the `Q-201` truncation unpinned, the Javadoc literals `64` and `8192`, the unobserved retention bound,
  the ADR-0030 "first miss" wording, the ADR-0031 peak without the in-flight chunk, the programmatic-set omission). Staging them is the operator's call (recorded in `epic.md` Open Defects). **L2-7 is the one to read first**: it changes a stated memory bound on a path that reads a remote response.
- What API Sheriff must re-measure on bump (recorded in `epic.md`): an annotated method whose scopes cannot be carried by the challenge now fails the **deployment** of the consumer (a new build step), and a set it could not resolve is answered 500 `SERVER_MISCONFIGURATION` with no `WWW-Authenticate` header and a `TokenSheriff_Q-201` log; `BearerTokenResponseFactory.insufficientScopeChallenge` is public;
  `BearerChallenge` now refuses an over-long scope, realm or `error_description` before it copies it (the output for valid input is unchanged); `BoundedBodyHandler` collects into one array that doubles, so its peak while streaming is below twice the ceiling plus the chunk in flight, not the ceiling plus one chunk; a non-numeric `Content-Length` is a normal failure; the unknown-`kid` miss inside a claimed window no longer touches the in-flight guard and a failed hand-off burns the window.
- Owed outside the epic tree (message `-013`): one `architecture enrich best-practice --module maven` call with the verbatim hint text (frontend build output is classified as build errors by the log parser); the lane did not run it because it would land as an uncommitted diff on main. It is the operator's to run or drop.
- The review of lesson 2026-11-02-14-001 and the missing lessons: see `epic.md` Open Defects.
