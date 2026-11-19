# Landing Analysis: PLAN-13 — Final finalize

epic: api-sheriff-seams
workstream: WS-02
pr: #931 (https://github.com/cuioss/TokenSheriff/pull/931), merged through the merge queue as 0cb136a70178e49011442277f96a47bd2c70622a

> Landing record for one shipped plan. Lives at `landings/PLAN-13.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-13-final-finalize-023.md` (lane `plan-marshall`, plan id `plan-13-final-finalize`), plus twenty-two candidate-lesson messages (`-001` to `-022`).
PR state read through `ci pr view` (`state: merged`, merge commit as above). The merge commit's first parent is `2a543d53` (the ledger landing #930), so the plan's diff is `git diff 2a543d53 0cb136a7` (46 files, 34 modified and 12 added, +2581/-1223, re-counted).
`inbox landing-check`: `complete: false`, `missing_keys` `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` (all `unknown`: no finalize step records them); `surface_delta` is `unmeasured`, so the declared-versus-realized comparison below is the orchestrator's own, from the diff.
The `steps` fact was split on its last colon; every element is a bare step id with outcome `done`. The archived plan directory (`2026-11-19-plan-13-final-finalize`) was read for `logs/decision.log`, `logs/work.log`, `metrics.md` and `work/code-review-findings.json`; the PR body (read through `gh pr view` output of the same PR) carries the plan's verification table and review records and is the lane's own account.
The three-layer analysis was read by one read-only leaf (`execution-context-level-3`, role `orchestrator.analyze`, resolved from `plan.effort`; one leaf for this one landing); the orchestrator re-read every `checked` point recorded below at its line at the merge commit.

## Deliverable Fidelity vs Spec

The spec lists six deliverables; the landing reports `deliverables_total=6`, `deliverables_done=6`. The nine operator decisions of the spec were settled at outline and are stated in the PR body: the exchange's `IllegalArgumentException` branch narrowed; `CancellationException` mapped to a fixed `IOException`; the request timeout stated as the wait bound; the raw request-side `IllegalArgumentException` stated as the contract; the package-level `@Vetoed` read as ArC reads it; the DPoP floor kept and pinned; one shared server fixture; the mapping inventory hand-kept; the ten Playwright runs not done in this plan; a native build run.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Transport exchange | **Delivered, with two gaps** | `isMalformedFraming` takes `@Nullable` and recognises a `NumberFormatException`, an `IllegalArgumentException` caused by one and a `ProtocolException` whose direct cause is one anywhere in an eight-link chain (`BackChannelHttp.java:184-198`, re-read); `CancellationException` from `pending.get()` maps to `IOException(EXCHANGE_CANCELLED)` (`:523-524`, re-read); the wait bound is stated at `:475-483`; the raw request-side exception is stated in the `@throws` of `TokenEndpointClient`. `BackChannelHttpExchangeTest` (473 lines) and `TokenEndpointClientRequestRefusalTest` are new. Gaps: a future failed with a wrapped `CancellationException` and any other response-side `IllegalArgumentException` still take the `RuntimeException` rethrow (`:535-539`, layer 3); red proofs and the JDK 21/25/27 behaviour were not re-run (the lane lists four red proofs in its PR body). |
| 2. Deployment scope check and startup guards | **Delivered by reading; ArC agreement not shown** | `TokenSheriffProcessor.inVetoedPackage` reads `{package}.package-info` from the index (`:454-466`, re-read); two tests with positive controls, a boot test, the control class in its own file, the DPoP floor Javadoc and the guard test's display name changed. That ArC treats the package the same way rests on the boot test, not run here. |
| 3. Tests and e2e helpers | **Delivered with deviations** | The three framed names are renamed, `TokenEndpointClientTest` (903) is split into 244 + 376 + 320 lines and `BearerTokenProducerTest` (607) into 404 + 205 + 111 (counted, all at most 500, the largest changed class is 473); one `RawHttpServer` fixture serves two tests; `devui-navigation.js` uses the declared selector; the mapping rows exist for every new class and the three fixtures (`doc/developer/test-impl-mapping.adoc:216-221, :251-256, :316-317, :355-361`, re-read). Deviations: see layer 2 (one framed name left, assertions added inside a move, a third server loop, renames bundled in moves). |
| 4. Documents | **Delivered, one item kept by decision** | COMMONS-17, ADR-0031, `retrieval-flow.adoc`, `AGENTS.md:159` (`1-99`), the exception Javadoc and the read-timeout statements changed; no added line carries the framing words searched for (leaf). `doc/client/requirements.adoc` is not in the diff (re-read): the logout sentence was kept, and the PR body's D6 table gives the reason (it matches `EndpointSchemePolicy`, whose scheme check I read at `252006c7`). The mapping inventory stayed hand-kept (decision). |
| 5. Playwright flake and start script | **Code done; the runs were not** | `self-devui-accessible.spec.js` now calls `goToTokenDebugger` (the render wait) and `devui-navigation.js` waits on the declared selector. The ten runs and the start script were recorded as not run (the operator's answer: they need Keycloak and Quarkus dev mode, and the Windows npm failed on UNC paths); the PR body names the flake's signature. The flake is neither reproduced nor shown fixed, as for PLAN-12. |
| 6. What PLAN-12 could not show, and the review records | **Delivered by the PR record, not by the tree** | The PR body carries a verification table and per-PR review tables for #898 to #925 (the lane's account). Orchestrator re-reads: the Sonar read is `undecidable` for a merged PR (below); `review-gate` for #931 returns 0 unresolved, 10 total, 0 handled; the remaining rows (red proofs, `-Ppre-commit`, `clean install` into a private repository, the native build, `DiscoverySpecIT`, the JMH workflow) are the lane's reports and were not re-run. |

Files of the diff no deliverable names, with their explanation: the eight sibling exception classes (`ClientAuthenticationFailedException`, `CredentialRejectedException`, `InvalidScopeException`, `RedeemedIdentityRefusalException`, `RedeemedResponseException`, `RedeemedScopeRefusalException`, `RedeemedValidationRefusalException`, `RefreshTokenFamilyRevokedException`) are Javadoc and comment edits only, added by the operator's widening of TASK-8 at 4-plan after the plan's own scope validator found the same framing in the siblings (inbox `-013`; `decision.log:83`, `927a9b`); `ClientConfiguration.java`, `doc/client/architecture.adoc`, `doc/commons/threat-model.adoc` and `doc/validation/threat-model.adoc` are the wording alignment of the code-review fix tasks TASK-011 and TASK-012 (inbox `-001`, `-002`); `TokenEndpointStubDispatcher.java` and `RawHttpServer.java` are test fixtures that follow from deliverable 3; `VetoedPackageEndpoint.java` and `vetoed/package-info.java` are the fixtures of deliverable 2. The scope-creep guard flagged eight and then twelve files and the operator accepted both (inbox `-008`, `-009`).

## Three-Layer Analysis

Verdict line: layer 1 sound with three spec defects of the orchestrator's own staging; layer 2 delivered with four minor deviations; layer 3 no blocker, no major, two minor points (reported to the operator in the same turn, not fixed, not staged).

### Layer 1 — Specified

Claims of the staged spec (corpus indexes 0-18; verdicts persisted by `corpus set-verdict`, producer `api-sheriff-seams/analyze`):

- Corroborated: claim 0 (`isMalformedFraming` returned true for any `IllegalArgumentException`, `exchange` waited without timeout and caught only two exception types at `252006c7`), claim 2 (`isBeanClassCandidate` read the class annotation only), claim 3 (the literal `4`), claim 5 (the control class in a second top-level declaration), claim 6 (the stale catch wording and ADR-0031 `:196`), claim 12 (a `CancellationException` from `pending.get()` propagated raw), claim 14 (a raw request-side `IllegalArgumentException` leaves `TokenEndpointClient` uncaught, re-read at `:617-630`), and claim 11 at code level only (that the JDK ever produces a response-side `IllegalArgumentException` other than the `Content-Length` one is not shown).
- Unverifiable: claim 13 (every request carries a timeout, so the wait is bounded in practice). The structural half holds (the helper never checks the request's timeout; the lane's `BackChannelHttpExchangeTest` asserts it for the five clients, not re-read), the conclusion does not: the lane's JDK 25 probe says the timeout stops at the response headers, which the merged text states.
- Not persisted: claims 1, 4, 7, 8, 9 were read by the orchestrator at `252006c7` when staging and are not leaf findings; claims 10, 15, 16, 17, 18 were not settled by reading (the JDK form of the exception, a package-level `@Vetoed` bean candidate, the validator's reading of the mapping document, the flake cause, the Sonar count).

Three defects of the spec, all the orchestrator's:

1. **The test id.** The spec says the waits in `devui-navigation.js` "repeat a test id that `utils/constants.js` declares", and the Expected Surface lists `constants.js` as "the declared test ids". I took this from the PLAN-12 advisory and did not read `constants.js`. It declares a CSS selector, `JWT_DEBUGGER_CONTAINER: '[data-testid="jwt-debugger-container"]'` (`utils/constants.js:49`, re-read), not the bare id, so passing it to `getByTestId` would never match; the plan's outline caught it (inbox `-014`) and required `.locator` with the selector.
2. **The framing in the siblings.** Deliverable 4 named `PushedAuthorizationEndpointMissingException` as the file carrying the compatibility rationale. At `252006c7` the same sentences ("keeps holding unchanged", "load-bearing for source compatibility") stand in `ClientAuthenticationFailedException:56,65`, `CredentialRejectedException:54,64`, `InvalidScopeException:56,65` and `RedeemedResponseException:47,55` (re-read) and in four more siblings; the plan's scope validator found them (inbox `-013`), and I had written the criterion as a phrase search without searching for it first.
3. **The fixtures.** Deliverable 4 required a mapping row for every test class the plan adds; the document's own rule covers fixtures too (`test-impl-mapping.adoc:170, :251` at `252006c7`, "Fixtures that are not test classes"), and the spec left them out (inbox `-015`). The plan added the three fixtures.

### Layer 2 — Implemented

Deviations, each re-read:

- **A framed name remains.** `TokenEndpointClientErrorMappingTest.java:157` is `shouldKeepInvalidGrantAsCredentialRejected`, the framing the pre-1.0 rule forbids; the spec's verification clause ("no framed name in the changed files") is not met. The method was moved, not added, and the spec named three other names.
- **Assertions were added inside a move.** The three negative matrices of `TokenEndpointClientErrorMappingTest` now assert `assertEquals(TransportException.class, thrown.getClass(), ...)` (`:145`, `:226`, `:289`, re-read; the base `TokenEndpointClientTest` has no such line). That is the sanctioned review fix TASK-013 (commit `95f0e11a`, CodeRabbit `5b6007`, inbox `-018`), so the split plus fix is explained, but the lane's weakened-test counter read 91 assertions at the base and 92 at HEAD, which does not equal three added lines; whether its count method differs was not checked.
- **Renames inside moves.** The two `DisplayName`s `"an mTLS-constrained request still accepts a Bearer token"` and `"an unconstrained request still accepts a Bearer JWT without cnf"` lost their "still" in the move to `TokenEndpointClientDpopBindingTest` (`:270`, `:281` against base `:657`, `:668`, re-read), and `resultUnchangedAfterCallerClearsRequirementSets` became `resultIsIndependentOfCallerRequirementSets` inside `BearerTokenProducerRequirementsTest` (leaf; not re-read at the line). The spec asked for a rename in its own commit; the merge is squashed, so the commit split cannot be read.
- **A third copy of the server loop.** `TokenEndpointClientRequestRefusalTest` carries its own `ServerSocket` loop and `readRequest` (`:55-134`, the leaf's line range; the class header and the two `readRequest` occurrences re-read, as in `RawHttpServer`) because `RawHttpServer` is package-private in `internal` and this test is in `flow`; the PR body records it as a known limit, and the deliverable asked for one shared fixture.
- Test methods are preserved (the leaf counted 38 at the base and 8 + 20 + 10 = 38 after the split, plus two new in the request-refusal class, by reading), and the largest changed test class is 473 lines.
- `doc/client/requirements.adoc` was not changed; the decision (keep) is recorded in the PR body, not in a changed file.

### Layer 3 — Correct

No blocker or major. Two minor points and two limits:

- **Minor — a response-side failure is rethrown raw, after redemption.** In `BackChannelHttp.exchange` a cause that is a `RuntimeException` is rethrown unchanged (`:535-539`, re-read): any response-side `IllegalArgumentException` that is not the `Content-Length` form (the operator's narrowing) and a future failed with a wrapped `CancellationException` (an `ExecutionException` whose cause is the `CancellationException`, which the `catch (CancellationException)` at `:523` does not see). `TokenEndpointClient.send` catches only `IOException` and `InterruptedException` (`:617-630`, re-read) and `ParClient` likewise (leaf, `:125-131`, not re-read), so the exception leaves the client as a raw runtime exception; in `TokenEndpointClient` the status handler may already have observed a success status, so a caller that keys its post-redemption handling on `TransportException`/`RedeemedResponseException` would miss it. Whether the JDK ever produces either shape was not shown. The wrapped-cancellation case has no test and no document line.
- **Minor — the raw server fixture stops on a reset.** `RawHttpServer`'s loop treats a `SocketException` as "closed" (`:86`, re-read), so a client reset during `readRequest` ends the server; the server thread is shut down but not awaited (leaf).
- **Limit stated, not a defect — the body phase has no time bound.** The merged text says so in each place (`BackChannelHttp.java:475-483`, `ClientConfiguration`, COMMONS-4/17, `transport.adoc`, both threat models, `doc/client/architecture.adoc`, ADR-0031); the operator accepted the limit. No test pins the claim "the request timeout does not cover the body". I searched the unchanged documents for the same promise: `quarkus-config-doc.adoc:70`, `openid-discovery.adoc:82` and `best-practices.adoc:150` speak of "read timeout" without promising a bounded wait. The validation-side statements assume cui-http sets only the connect and request timeouts (PR body: not inspected).
- **Not shown by reading:** that ArC agrees with the processor's package veto (the boot test), the JDK 21/25/26/27 behaviour of the client, the red proofs, the full build, the Playwright runs.
- Advisories the lane left (PR body and `decision.log:109`, `:130`): the `readTimeoutSeconds` Javadoc names the payload ceiling as the body bound where discovery uses the discovery document size; the new mapping rule says every class is in its module's section while client guard classes are under "Cross-cutting guards"; `BearerScopeValidationCombinedIndexBuildStepTest` still has a display name "Should still refuse ..." at about `:150`; one review point on the package-info lookup (the combined index versus ArC's bean-archive index) was held by the operator for a later plan.

### Conformance

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` | pre-1.0: no `@Deprecated`, no transitional or compatibility wording, including test names | **deviates** | `TokenEndpointClientErrorMappingTest:157`; added lines in the changed files are free of the searched framing words (leaf) |
| `AGENTS.md` | test classes at most 500 lines | **conforms** | the largest changed class is `BackChannelHttpExchangeTest` at 473 lines (counted) |
| `AGENTS.md` | splits are pure moves | **deviates** | three added assertions, two display names and one method renamed inside moves (above) |
| `AGENTS.md` | JUnit 5, AAA, no Mockito or Hamcrest | **conforms** | leaf, by reading |
| `AGENTS.md` | CWE-117 sanitising, fixed refusal texts | **conforms for the new texts** | `EXCHANGE_CANCELLED`, `EXCHANGE_FAILED`, `MALFORMED_RESPONSE` are fixed and the JDK exception is neither chained nor echoed (`BackChannelHttp.java:530-534`); `IOException` messages in `TokenEndpointClient` (`:618,626` at the merge commit, re-read) are passed on unsanitised, which the leaf also reports for `ParClient:126-127` (not re-read); pre-existing, not changed |
| `AGENTS.md` | LogRecord ranges `INFO (1-99)` | **conforms** | `AGENTS.md:159` |
| `doc/developer/test-impl-mapping.adoc` | a row for every added class and fixture | **conforms** | all seven classes and three fixtures found in the document (re-read) |
| `ref-code-quality` | no duplicated declaration meant to agree | **deviates** | the raw server loop three times, `createAccessToken` twice |
| Done-when | full `-Ppre-commit` plus `clean install`, Sonar `0` at the head, named tests pass | **not shown by reading** | the lane's reports only (below) |

## Metrics / Anomalies

| Metric | Value |
|--------|-------|
| Tokens | 19,949,441 (`metrics.md`; a mixed population: dispatched and main-context spend, floor-and-ceiling; the accumulator holds a placeholder for one Sonar re-read) |
| Wall / worked | 9h30m reported (4h11m in 5-execute), 2h25m worked, 7h4m idle (3h1m idle in 1-init) |
| Phase tokens | 1-init 491,099; 2-refine 479,377; 3-outline 2,587,319; 4-plan 1,638,770; 5-execute 12,214,463; 6-finalize 2,538,413 |
| Loop-backs | three in finalize (code-review at 1/5 and 2/5, then the wait-region triage back to 5-execute for TASK-013), the lane's account re-read in `work.log:374,411,479` |
| Review closure | `functional-review` and `code-review` closed by operator override on four `weakened_test` rows (pure splits and renames); the closing code-review round was a declared `delta` over 6 files (`decision.log:129`) and the findings file's `files_read` is 15 (`code-review-findings.json`), so the closing-round reuse question of lesson 2026-11-19-07-008 does not arise as a `full` round with partial reading |

Anomalies:

- **The version skew again.** The skill documents loaded in the run were 0.1.2159 while the installed executor is 0.1.2133 (the registry pins 0.1.2139 and 0.1.2167), so `--changed-paths-file` and `--record-change` were rejected, the change-ledger rows were appended by hand, and one commit (`756f0736`) had no row, which refused the 5-execute to 6-finalize transition once (`work.log:357-358`). Recurrence on lesson 2026-11-18-12-002.
- **A wording correction took two review loop-backs** because its first fix did not sweep the repository for the claim (inbox `-001`, `-002`).
- **The PR's Sonar numbers.** The lane's first read at `a9f97d16` found four `java:S125` issues (`work.log:475`), fixed in `17ae6487`; the PR body states 0 open issues and 0 hotspots at `95f0e11a`. The Sonar read now is `undecidable` (`head_revision_unavailable`, analysis revision `95f0e11a`), so the count is recorded unknown and the lane's `0` is its own.
- **The landing fact and the residue agree.** `sonar_open_new_issues=0` is the last head's number; the four at `a9f97d16` are an earlier head's (inbox `-007` states them with their head).
- **`marshal.json` is stale against the installed manifest** (the lane's residue; `/marshall-steward` refreshes it); nothing in this epic edits it.

## Routing / Merge Behavior

| Fact | Landing says | Provider re-read |
|------|--------------|------------------|
| PR / merge | #931, `merged`, queue merge `0cb136a7` | `ci pr view`: `state: merged`, merge commit `0cb136a7`, head branch `feature/plan-13-final-finalize`; **confirmed** |
| `unresolved_review_threads` | `unknown` | `review-gate`: `0`; the eight review threads each read `resolved: true` through `ci pr comments`; **no claim to compare** |
| `review_body_findings_total` / `_handled` | `unknown` / `unknown` | `review-gate`: total `10` (coderabbit `nitpick-1`, `additional-1`, `inline-1` to `inline-8`), handled `0`, `merge_allowed: false`, `body_findings_undisposed`; the lane's replies exist on the PR (read) and the gate does not read them; **no claim to compare** |
| `sonar_open_new_issues` | `0` | `sonar read_open_issues --pr 931`: `undecidable` (`head_revision_unavailable`), analysis revision `95f0e11a`; recorded **unknown**, the lane's `0` is its own |
| Merge path | merge queue | merged by the queue; the lane's checks ran green at `95f0e11a` (PR body); not re-read |

## Reconciliation

- Queue: PLAN-13 `running` → `shipped`; `pr` `#931`; `landing` `landings/PLAN-13.md`; `plan_marshall_plan_id` `plan-13-final-finalize` (carried by the landing message).
- Parallelization: nothing ran beside it; no overlap with a running plan was predicted or observed. The scope-creep guard fired twice (eight, then twelve files) and the operator accepted both; every file is explained above.
- Queue state: PLAN-01 to PLAN-13 are all `shipped`; nothing is staged and nothing is running, so the queue is empty.
- Layer-1 verdicts persisted for claims 0, 2, 3, 5, 6, 11, 12, 13 and 14 of `plans/PLAN-13-final-finalize.md`.
- Candidate lessons: see the Decisions entry in `epic.md` (new lessons, recurrences on existing lessons, items absorbed into Watches, items discarded).
- `epic.md`: the PLAN-13 Queue annotation, the Decisions entries, the Open Defects entry "PLAN-13 landed" and the extended Watches were written with this landing.

## Follow-ups

None is staged: the operator's word is needed for any new plan, and the queue is empty. Items carried to `epic.md` Open Defects: the Playwright flake and the never-run start script (second plan); the layer-3 points (raw rethrow after redemption, the wrapped cancellation, the fixture's reset handling) and the stated body-phase limit; the framed name, the added assertions and renames inside moves and the third server loop; the lane's advisories and the review point the operator held; the review gate's `body_findings_undisposed` for #931 (10, handled 0); and the three spec defects above. Marketplace-side findings go to the Watches and the lessons store.
