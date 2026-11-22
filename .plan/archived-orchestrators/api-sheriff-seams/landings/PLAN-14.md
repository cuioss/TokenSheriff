# Landing Analysis: PLAN-14 — Closing sweep

epic: api-sheriff-seams
workstream: WS-02
pr: #938 (https://github.com/cuioss/TokenSheriff/pull/938), merge commit `57a50058d0d798b38d7540df5d9b9b2f5e7b0897`, 50 files (+1600/-441)

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims against ground truth. The landing arrived as inbox message `implement-plan-14-closing-sweep-007` (a `landing` with a `landing-facts` block) plus six candidate-lessons (-001 to -006). The lane's plan id is `implement-plan-14-closing-sweep`; the row was staged, never recorded launched or running (the operator's word for those was not given), and is reconciled here straight to `shipped` on the landing's own evidence (merged PR, closed lifecycle).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Response-side failure of the exchange classified; every client treats it the same way | shipped-as-specified (the operator's decision: map both shapes to fixed `IOException`s, no chained cause) | `BackChannelHttp.java:591-620` (`exchangeFailure`): a wrapped `CancellationException` maps to `EXCHANGE_CANCELLED`, any other `RuntimeException` to `EXCHANGE_FAILED` with no cause, `IOException` returned as is, `Error` rethrown, malformed framing still replaced by the supplier. The five clients are unchanged and `TokenEndpointClient.send:621-628` turns the new `IOException` into a `RedeemedResponseException` after a recorded success status. `BackChannelHttpExchangeFailureMappingTest` (16 cases) pins the mapping at exchange level. **Not shown:** the red proofs (a run), and a client-level test for the new shape after a success status. |
| 2. Fixtures and test names meet the rules | **shipped-modified, partly met** | Fixtures delivered by reading: `RawHttpServer` keeps serving after a reset and closes its connection and thread on close; `TokenEndpointClientRequestRefusalTest` uses it; one `ProducerTestTokens` replaces three `createAccessToken` copies. About twenty names renamed, names only. **The spec's own named example `AuthorizationRequestBuilderTest.java:242-243` (`shouldKeepUrlWithoutFragmentUnchanged`) is not renamed and the PR body lists no reason**, and a search finds more framed names (Layer 2). The per-rename commit rule cannot be read from a squash; the lane reports three per-module commits instead of one per rename. |
| 3. Package-veto lookup shown to read what ArC reads | shipped-modified (the limit is stated) | `TokenSheriffProcessor.java:344-345` passes `CompositeIndex.create(beanArchive, combined)` (unchanged); a dependency-archive boot test and a two-index build-step test are added, and the Javadoc `:459-468` states what was shown and what was not (a `package-info` indexed only in the combined index while the classes sit in a bean archive). The subpackage case is held only by the dependency boot test. |
| 4. Statements about the wait and the mapping rule say what the code does | shipped-modified | The criterion "pinned by a stalling-server test" was **changed by the operator** to a per-JDK probe after the JDK 27 CI leg failed (`BackChannelHttpBodyPhaseTest.java:96-125`: accepts either outcome and names it). The statements are per-JDK as observed; the sweep of the old wording found no remaining hit (the leaf's search, not re-run by the orchestrator). The cui-http source claims are attested only by the corrected text (no cui-http source available to read). |
| 5. Playwright runs and start script | **not delivered** | No file changed. The lane ran the start script once: Keycloak came up, Quarkus dev mode never became ready (health 404 for 120 s; "ArC container not initialized"; TokenSheriff-141, no DslJson reader for `commons.transport.Jwks`); the machine has no Linux npm and the Windows npm refuses a UNC working directory. The record names the precondition, as the spec requires. **Whether the dev-mode failure is environmental or a defect of the branch build is unsettled and needs an operator read** (the lane's account, not re-read). |

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, role `orchestrator.analyze`, resolved from `plan.effort`; 71 tool calls, 261k tokens), and the orchestrator re-read every point recorded below as `checked` before writing it. The reading is by reading: no build, no test run, no write. What only a run could show is tagged `not shown by reading`.

- **Dispatch:** one read-only leaf per landing — reason: reading the merged exchange, the fixture and the 50-file diff against the spec is the depth the dispatch rule exists for; one landing, so one leaf; the leaf wrote nothing.
- **Verdict:** layer 1 findings (two minor, both the orchestrator's own spec); layer 2 findings (**one major**, six minor); layer 3 findings (three minor, no blocker, no major).
- **Points the orchestrator re-read at `57a50058`:** `AuthorizationRequestBuilderTest.java:238-248`; `BackChannelHttp.java:588-625`; `RawHttpServer.java:117-136`; `TokenSheriffProcessor.java:342-346` and `:459-470`; `TokenEndpointClient.java:617-632`; `TokenValidatorTest.java` (723 lines); `AGENTS.md:173-175`; `BackChannelHttpBodyPhaseTest.java:90-125`; the subpackage case in the two boot tests (`git grep`: only `BearerScopeValidationDependencyVetoedPackageBootTest.java:81-85`); the framing hits `ClientConfigurationScopeValidationTest.java:135,222`, `ClientConfigurationTest.java:87`, `ClientConfigurationTransportTest.java:188`, `TokenSignatureValidatorES256FormatTest.java:69-70`; the existence of `CachingDiscoveryResolverRefreshRegressionTest` and `HttpJwksLoaderDefaultBehaviourUnchangedTest`. **Two of the leaf's paths were wrong** (it named `…/jws/TokenSignatureValidatorES256FormatTest` and `…/validation/CachingDiscoveryResolverRefreshRegressionTest`; the classes are under `pipeline/validator/` and `client/…/discovery/`); the hits themselves hold. **Not re-read (recorded as unverified leads):** the accept-loop `SocketException` spin at `RawHttpServer.java:151-156`; `isMalformedFraming` (`BackChannelHttp.java:203-217`) not matching a nested cause; the 159-line shrink of `BackChannelHttpExchangeTest` losing no assertion; the "about twenty renames change names only" reading; the old-wording searches; the `doc/LogMessages.adoc` and mapping-document claims; the framing hits `JweDecryptionConfigResolverTest.java:390-391`, `CachingDiscoveryResolverStalePolicyTest.java:201`, `IssuerConfigResolverEgressHostTest.java:100`.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's HYPOTHESIS that the index `isBeanClassCandidate` receives is the combined index (a `package-info` indexed only by a dependency vetoes a class ArC registers) | checked | refutes | `TokenSheriffProcessor.java:344-345`: the production call passes the composite of the bean-archive and the combined index, unchanged by the PR (the PR touched Javadoc only). The question was never "combined-only"; what stays open is what ArC itself reads. Persisted: claim 10 `contradicted`. |
| L1-2 | Done-when / verification: "`wc -l` of every changed test class at most 500" | checked | refutes | `TokenValidatorTest.java` is 723 lines, `RefreshAdversarialTest` 509, `DpopProofValidatorAlgorithmAndClaimTest` 508, each changed by name only; `AGENTS.md:173-175` calls 500 a guideline, not a gate, with no obligation to split an existing class, and the spec's own "splits are pure moves" forbids the split. **The criterion was the orchestrator's own spec defect**; the lane met it as a conflict and the operator closed the review by override on it. |
| L1-3 | Per-rename commits and deliverable 5's missing-precondition record | not_shown_by_reading | none | Main holds one squash commit and the diff holds no deliverable-5 file; neither criterion can be read from the repository. |

**Layer 1 verdict:** findings — 2 (L1-1, L1-2), not shown by reading — 1 (L1-3)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `BackChannelHttp.java:591-620`; `TokenEndpointClient.java:621-628` | `BackChannelHttpExchangeFailureMappingTest` (16 cases: 8 failing shapes, 8 positive controls); the red proof needs a run | **No client-level test drives the new fixed `IOException` after a recorded success status**; the post-redemption claim for the new shape rests on reading (L2-2). Four positive controls repeat `BackChannelHttpExchangeTest`'s (L2-6). | checked (trace), not_shown_by_reading (red proof) |
| 2 | `RawHttpServer.java:117-136`; `ProducerTestTokens` | `RawHttpServerTest` (reset test relies on `SO_LINGER 0`; platform behaviour needs a run) | **`AuthorizationRequestBuilderTest.java:242-243` left unrenamed (L2-1, major)**; further framed names remain (L2-3) | checked |
| 3 | `TokenSheriffProcessor.java:344-345`, `:459-468` | dependency boot test (shows ArC registers no bean, and the subpackage case); two-index build-step test (pins lookup semantics but calls `findScopeViolations` directly, so it does not pin the production wiring: a change of `:344-345` to a single index would not turn it red, L2-5) | Javadoc cites the same-package boot test beside the subpackage sentence, and only the dependency boot test holds the subpackage case (L2-4) | checked |
| 4 | statements in 12 documents and Javadocs | `BackChannelHttpBodyPhaseTest`: a probe; every branch passes, the result is published only through `TestReporter`, so no test fails if a JDK's behaviour changes (L2-7); on a slow runner the 1 s request timeout could fire before the head and the probe would record "bounds the body read" on JDK 21/25 (not shown by reading) | the cui-http claims are attested only by text | checked / not_shown_by_reading |
| 5 | none | none | the runs were not done | not delivered, record stated |

- **L2-1 (major).** `AuthorizationRequestBuilderTest.java:242-243`, display name "Should keep the URL for an endpoint without a fragment unchanged", method `shouldKeepUrlWithoutFragmentUnchanged`: the spec named it as a framed name to rename or justify; the file is not in the diff and the PR body lists no reason. Whether the name is history framing is a judgement (it describes a value, not a history), and the spec's own text says a "keep" for a collection order is not framing; the spec put it on the rename list, so the plan owed either a rename or a listed reason.
- **L2-3 (minor).** The "search with every hit renamed or listed" criterion is not shown met: remaining framed names read by the orchestrator include `TokenSignatureValidatorES256FormatTest.java:69-70` ("should now work", `es256IeeeP1363FormatNowWorks`), `ClientConfigurationTransportTest.java:188` (`shouldKeepIssuerUnchanged`), `ClientConfigurationScopeValidationTest.java:135,222` ("keep accepting"), `ClientConfigurationTest.java:87` ("while still permitting"); the class names `HttpJwksLoaderDefaultBehaviourUnchangedTest` and `CachingDiscoveryResolverRefreshRegressionTest`, and `JweDecryptionConfigResolverTest.java:390-391`, `CachingDiscoveryResolverStalePolicyTest.java:201` and `IssuerConfigResolverEgressHostTest.java:100` are the leaf's, not re-read. About seventy other "keep X" hits mean "preserve" (the leaf's reading, not re-read) and are not framing.
- **L2-4 (minor).** `TokenSheriffProcessor.java:463-467`: the Javadoc sentence "it registers the class in a subpackage … (`BearerScopeValidationVetoedPackageBootTest`, `BearerScopeValidationDependencyVetoedPackageBootTest`)" cites two tests after the subpackage clause, while only the dependency boot test holds the subpackage case.

**Layer 2 verdict:** findings — 7 (L2-1 major; L2-2 to L2-7 minor), not shown by reading — 3 (the red proofs, the reset test on every platform, the probe on a slow runner)

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `BackChannelHttp.java:618` | an `ExecutionException` whose cause is a checked exception that is neither `IOException`, `Error`, `RuntimeException` nor `CancellationException`, or an `ExecutionException` nested in the first cause | falls to `new IOException(EXCHANGE_FAILED, cause)`, which **chains** the cause chain; `isMalformedFraming` (`:203-217`) does not match a `NumberFormatException` below a non-`IllegalArgumentException` head, so a raw `Content-Length` value in such a chain would be chained, and a nested `CancellationException` would not map to the cancelled text; the CWE-117 rule "a JDK exception's message is never chained" holds only for the recognised shapes. No known JDK path produces it; untested | minor |
| L3-2 | `RawHttpServer.java:119-134` | `serverSocket.close()` (`:122`) or `activeConnection.close()` (`:124`) throws inside `close()` | `close()` exits before `shutdownNow` and `awaitTermination`, so the non-daemon single-thread executor keeps running; the lane named it as an advisory; low likelihood | minor |
| L3-3 | `RawHttpServer.java:151-156` (unverified lead) | a persistent accept `SocketException` with the server socket still open | swallowed without limit, the thread spins until `close()` | minor |

**Layer 3 not shown by reading:** the red proofs and every test outcome; whether a JDK completes the future with a wrapped cancellation or a response-side runtime exception (the code comment says "Defensive: no JDK path is known"); the JDK 21/25/27 timeout behaviour (the lane's CI leg and probe, not read); that `-Ppre-commit` and `clean install` were green.

**Layer 3 verdict:** findings — 3 (all minor, no blocker, no major), not shown by reading — 4

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md Pre-1.0 and plan constraint | no history-framed wording in test names or display names | **deviates** | L2-1 and L2-3 |
| ref-code-quality (resource lifecycle) | a handle acquired is released on every exit path | **deviates** | L3-2 |
| ref-code-quality (error handling) | a failure at a boundary is not chained unsafely | **deviates** | L3-1 (unreachable by known paths, untested) |
| ref-code-quality (duplication) | one contract or fixture stated once | **deviates** | the mapping test repeats four positive controls; `BackChannelHttpBodyPhaseTest` adds a loopback server loop beside `RawHttpServer` (justified in its Javadoc) |
| ref-code-quality (documentation accuracy) | docs admit no wrong reading | **deviates** | L2-4 |
| AGENTS.md Pre-1.0 | no `@Deprecated`, TODO or transitional comments | conforms | scan of added lines (the leaf's) |
| AGENTS.md Logging | one new `LOGGER.debug` with `%s`; no INFO/WARN/ERROR added; `doc/LogMessages.adoc` untouched | conforms | `BackChannelHttp.java:611` (re-read), the leaf's scan |
| AGENTS.md Test Class Size | new or grown classes at or under 500 lines | conforms | new classes 108 to 226 lines (the leaf's count) |
| AGENTS.md Test Class Size | splits are pure moves | conforms | `StubClient`/`StubResponse` moved to `StubHttpClient`; `ProducerTestTokens` bodies identical (the leaf's reading) |
| junit-core | JUnit 5, AAA, no Mockito/Hamcrest, no sleeping | conforms | the leaf's scan; the probe uses bounded `Future.get` waits |
| plan security (CWE-117, ADR-0022) | fixed texts, no JDK message chained for the recognised shapes | conforms, with L3-1 | `BackChannelHttp.java:605-614` |
| native image | plain helpers, no reflect-config | conforms | no reflect-config in the diff |
| AGENTS.md Pre-Commit Validation | `-Ppre-commit`, `clean install`, Sonar at the PR head | not_shown_by_reading | runs are not visible from the repository |

## Metrics and Anomalies

- Tokens: 27,563,968 (`total_tokens`); wall time 76,715 s (about 21.3 h); model `claude-sonnet-5-5`, effort `medium`; plugin and executor `0.1.2133` (the pinned-executor skew again, see Follow-Ups).
- Anomalies: the plan ran at `6-finalize` with the row staged, not recorded launched (no word from the operator); the lessons-capture step "was marked done with `--force` by its leaf" (the lane's account, not verified); `archive-plan` was `pending` at the time of the landing message; the first push failed on the JDK 27 CI leg (the original body-phase test asserted the body is not time-bound), which drove the operator's change of criterion.

## Routing and Merge Behavior

- Review: CodeRabbit posted two review bodies (at `c157a844` and `d21fb040`), a walkthrough and six inline threads (all six resolved, the lane replied on the threads), and the provider counts 7 review-body findings; Sourcery approved at `d21fb040` and `132e969d` and posted quota notices; the lane's `Triage dispositions` comments answer the review bodies. **Provider re-read (`runbook_lane review-gate --pr-number 938`):** `unresolved_review_threads: 0`, `review_body_findings_total: 7`, `review_body_findings_handled: 0`, `merge_allowed: false` (`body_findings_undisposed`). **The landing's three counts were `unknown` (not a false claim, a failed read), and the provider's `0` handled is not evidence the lane ignored the findings:** the gate does not read the lane's `Triage dispositions` comments (the same blind spot as PLAN-13, the marketplace list). `ci pr comments` reports `unresolved: 9` over 21 comments: six resolved threads, and nine non-thread items (review bodies and issue comments) that carry no resolved flag. `review_body_findings_handled` is **not re-verified** (the lane's progress file is not a path this verb reads). CodeRabbit's check on the final head `132e969d` was neutral/skipped (quota); its last review covers `d21fb040`; whether either bot reviewed the final head is not told by the bot output. Both reviews were closed by operator override at three heads (`c157a844`, `d21fb040`, `132e969d`) on the same rows: eleven renamed-test rows the counter reads as lost assertions (the leaf's reading: every hunk changes only a name; a counter false positive), the per-module commits, and the 500-line conflict.
- CI/merge: merged through the merge queue as `57a50058`; every gating check green on the final head (the lane's account; the PR body states the JDK 27 leg passes after the probe). A post-merge `ci checks wait` ended at its deadline with only the non-gating JMH workflow running.
- Sonar: the lane reports `sonar_open_new_issues=1` (java:S8714, MINOR, in `BackChannelHttpBodyPhaseTest`, accepted not suppressed; the count excludes hotspots). **Re-read:** `sonar read_open_issues --pr 938 --project cuioss_TokenSheriff` is `undecidable` (`head_revision_unavailable`; `analysis_revision` is `132e969d`), so the figure is recorded as **unknown for the merged PR, never 0**, and the lane's `1` is its own read.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-14 --status shipped`
- [x] row `pr` stamped `#938`
- [x] row `landing` stamped `landings/PLAN-14.md`
- [x] row `plan_marshall_plan_id` stamped `implement-plan-14-closing-sweep` (from the landing message and the PR head branch)
- [x] claim 10 of the spec persisted `contradicted` (`corpus set-verdict`)
- [x] epic.md: Queue annotation, Decisions entry, Open Defects "PLAN-14 landed", Watches extended
- [x] six candidate-lessons dispositioned and archived with the landing message (seven messages)
- [x] resume anchor updated and `queue-view.md` regenerated

## Follow-Ups

- **Layer-3 minors (L3-1 to L3-3) and the layer-2 major (L2-1) are reported to the operator in the same turn; none is fixed inline, none is staged.** They become a plan only on the operator's word.
- **The Quarkus dev-mode boot failure** (deliverable 5's record: "ArC container not initialized", TokenSheriff-141, no DslJson reader for `commons.transport.Jwks`) is a lead the orchestrator did not read; whether it is a defect of the branch build is the operator's read. Recorded in Open Defects, not staged.
- The Playwright runs and the start script are never run: third plan in a row; the 2026-11-15 flake entry stays open.
- Marketplace side: see the Watches (the review gate that does not read the lane's replies; the `weakened_test` carry-forward ask, eighth plan; the docs-versus-executor skew, fifth plan).
