# Landing Analysis: PLAN-11 — Final residuals sweep

epic: api-sheriff-seams
workstream: WS-02
pr: #921 (https://github.com/cuioss/TokenSheriff/pull/921), merged through the merge queue as d8aba51265aabd0db8e37ae939a9d12afedff756

> Landing record for one shipped plan. Lives at `landings/PLAN-11.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-11-final-residuals-sweep-007.md` (lane `plan-marshall`, plan id `plan-11-final-residuals-sweep`), plus one finding (`-001`) and five candidate-lesson messages (`-002` to `-006`).
PR state read through `ci pr view` (`state: merged`, merge commit as above, base `main`). The merge commit's first parent is `2f54697d` (#913, a workflow dependency bump), so the plan's own diff is `git diff 2f54697d d8aba512` (47 files, +1752/-328);
a diff against the ledger base `6bfc910d` also carries `.github/workflows/benchmark.yml`, which is #913's. `inbox landing-check`: `complete: false` (missing `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, all `unknown`);
`surface_delta` is `unmeasured`, so the declared-versus-realized comparison below was made by hand. The `steps` fact was split on its last colon; every element is a bare step id with outcome `done`.
The lane's account of its own run was not re-run. The archived plan directory (`2026-11-18-plan-11-final-residuals-sweep`) was read for `logs/work.log`, `metrics.md` and `solution_outline.md` only.

## Deliverable Fidelity vs Spec

The spec lists six deliverables; the lane's outline split them into **seven** (outline 1 body-handler bound, 2 wording and the `Error` release, 3 tests, 4 validation-side allow-list, 5 client allow-list stored form and client residuals, 6 the review-body findings, 7 the three loose ends), and the landing reports
`deliverables_total=7`, `deliverables_done=7`. The operator kept one plan above the guard's limit (about fourteen tasks, twenty after the re-shape) and the outline records the overrun. The table follows the spec's numbering.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Body handler's memory bound recorded and pinned | shipped-as-specified, with one residual (L1-2) | `BoundedBodyHandler` keeps the doubling array clamped to the ceiling (`append`, `:376-388`, re-read); COMMONS-17 states the bound (`doc/commons/requirements.adoc:177-182`, re-read) and ADR-0031 records the decision, its cost and the conflict (`:308-321`, re-read); `BoundedBodyHandlerCapacityTest.assertBound` checks capacity at most the ceiling, at most `max(2 x received, first allocation)` and at least the bytes received after every buffer (`:69-81`, re-read) |
| 2. PLAN-09's undelivered items and wording errors | shipped-as-specified | ADR-0031 "Changed default behaviour" now itemises the client body buffer, the 100-year freshness window, the unchanged-`kid` DEBUG and the health-check WARN (`:186-199`, re-read); the hand-off guard is released in a `finally` unless the task was accepted, and an `Error` propagates (`HttpJwksLoader.java:896-908`, re-read; class Javadoc `:97-100`, re-read); `MAX_CEILING` `@since`, `unknownKidRefreshWindow` Javadoc, "the miss that lost the window", `VALIDATION-14.2`, the `{@value}` literals and `IssuerConfigResolver`'s `@throws` are the leaf's reading (not re-read). The spec's "unqualified sentence" item did not exist on main: both sentences read "a miss inside a claimed window" (`HttpJwksLoader.java:852-854`, `:876-878`, re-read), as the outline recorded |
| 3. PLAN-09's tests can fail for what they name | shipped-as-specified (every item present by reading; "fails without the change" is run-only) | retention: `Builder.retainedScopeCount` (`BearerChallenge.java:225-232`, re-read) and the guard at `:165`, asserted 65 against 100,000 retained and 64 as the control (`BearerChallengeTest.java:298-301`, re-read); `BearerScopeValidationBuildStepTest` is 493 lines (`wc -l`); the non-ASCII, control-character, multi-buffer (`ManualDrive.feedInOneCall`), `Q-201` truncation and interface-default-method tests are the leaf's reading |
| 4. PLAN-03's residuals closed | shipped-as-specified, with the operator's decisions | `ClientConfiguration` stores `requireBareHost`'s canonical form (leaf, `~460-476`); `ClientConfigurationTest` changes in two hunks inside one method (`git diff -U0`: `@@ -907`, `@@ -910`), the operator-approved exception; the zoned IPv6 test **runs** `URI.getHost()` and asserts `[fe80::1%eth0]` is reported and refused by the `%` rule (`EgressHostValidatorTest.java:210-221`, re-read; that it passes is run-only); the off-curve EC test accepts either fixed refusal (`PemKeyPairLoaderAdversarialTest.java:189-213`, re-read); `HttpJwksLoaderConfigTest` is 461 lines and `TokenSheriffClientProducerTest` 469 (`wc -l`); the `PemKeyPairLoader` symlink/FIFO/unwiped-text items are accepted and documented (leaf, `:72-84`) |
| 5. Every review-bot body finding of #915 and #916 has a recorded disposition | not shown by reading | the dispositions live in PR threads and the run's progress record, which this analysis did not open; the only PR comments read are #921's own (see Routing). `review-gate` for #915 and #916 was not re-run |
| 6. Three repository-side loose ends | partly delivered | the Playwright flake is **neither reproduced nor fixed and the ten runs were not shown**: the E2E environment resolved the shared `~/.m2` snapshot and never became ready (`work.log:491`, read; lesson 2026-11-18-12-001); `accessibility-wcag-compliance.spec.js` and `test-fixtures.js` are untouched (declared surface, no diff). `test-impl-mapping.adoc` gained the deployment-module and client-quarkus-deployment rows, but five existing client classes remain unmapped (L2-3). The architecture hint touched `.plan/project-architecture/_project.json` and `token-sheriff-parent/enriched.json` (names re-read in the diff; content not opened), `token-sheriff-parent` being the outline's chosen module |
| (outline 6, from #916's finding) Malformed `Content-Length` in the client | shipped, **not a spec deliverable**: it is the "fixed in this plan" outcome of re-deciding a #916 body finding | `BackChannelHttp.send` maps the JDK's `IllegalArgumentException` to a `TransportException` with a fixed text (`BackChannelHttp.java:373-379`, re-read), `TokenEndpointClient` does the same by status (`:601-611`, re-read), and `DiscoveryResolver`, `ParClient`, `UserInfoClient`, `RevocationClient` send through it (leaf); `BackChannelHttpMalformedResponseTest` is new |

Diff files outside the declared Expected Surface (36 declared paths, 47 realized): the six client main files of the `Content-Length` mapping (`BackChannelHttp`, `TokenEndpointClient`, `DiscoveryResolver`, `ParClient`, `UserInfoClient`, `RevocationClient`) and its test; `ClientConfigurationTest` (the one-method exception, announced in inbox `-001`);
the new test classes the splits and new tests create (`BoundedBodyHandlerCapacityTest`, `BoundedBodyHandlerMultiBufferTest`, `HttpJwksLoaderConfigEgressHostTest`, `TokenSheriffClientProducerEgressTest`, `BearerScopeValidationDefaultMethodBuildStepTest`), `BearerScopeValidationRefusalBootTest`, `EgressHostValidatorTest`; and the two architecture data files.
Declared but not touched: `JwkPublicMembersTest`, `ClientConfigMapper`, `BearerTokenResponseFactory`, `BoundedBodyHandlerTest`, and the two Playwright files. The six undeclared client main files are the under-declaration the gate could not predict (see Reconciliation).

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface), and the orchestrator re-read the points marked "re-read" at `d8aba512` (`git show` and the archived plan's files) before writing them here.
Points marked "leaf" are the leaf's reading that this analysis did not re-read: they are recorded as the leaf's account, not as settled. The reading is **by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf — reason: every full-ship landing gets its own leaf (merged-code depth, no shared context with another landing's read). The leaf took the ledger base `6bfc910d` as the diff base, so it named `benchmark.yml` as an unexplained file; it belongs to #913.
- **Verdict:** layer 1 findings (2 minor, 2 info); layer 2 findings (3 minor, the rest info or not shown by reading); layer 3 findings (3 minor, 2 info, one not shown by reading). No blocker and no major defect was found at any layer; the leaf's "major" for the `Content-Length` change is recorded here as minor (see L1-1).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The client's malformed-`Content-Length` mapping changes what the client throws (`IllegalArgumentException` becomes `TransportException`) although the spec's standing constraint says default behaviour stays unchanged except where a recorded decision says so; the outline's decision table has no row for it (it says the finding is "re-decided in D6"), and ADR-0031 "Changed default behaviour" does not itemise it. The change is in scope through the spec's "fixed in this plan" outcome for a body finding, which is why it is minor, not the leaf's major | checked (re-read) | refutes (against the standing constraint) | `BackChannelHttp.java:373-379`, `TokenEndpointClient.java:601-611`, `ADR-0031:186-199` and `:356-361` |
| L1-2 | The spec asked for "one bound in one form", and COMMONS-17, `transport.adoc` and ADR-0031 do state one form, but its first-allocation term ("or the first allocation when that is larger") has no value in any document; the value, 1024 or the ceiling when smaller, is only in `BoundedBodyHandler.java:304` and the test | checked (re-read for COMMONS-17 and the constant; `transport.adoc` leaf) | refutes | `doc/commons/requirements.adoc:177-178`, `BoundedBodyHandler.java:303-304` |
| L1-3 | The spec's D2 premise of an unqualified "never makes a periodic cycle skip" sentence did not hold on main | checked (re-read) | refutes | `HttpJwksLoader.java:852-854`, `:876-878` |
| L1-4 | The spec's D1 told ADR-0031 to name the conflict with the PLAN-09 specification, and the ADR does so with a plan id, which conflicts with the pre-1.0 no-history-framing rule the same spec carries (a tension inside the spec; carried at layer 3 as L3-2) | checked (re-read) | refutes (the spec's two clauses disagree) | `ADR-0031:320-321` |

**Layer 1 verdict:** findings — 4 (2 minor, 2 info)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `BoundedBodyHandler.append` (`:376-388`, re-read), COMMONS-17, ADR-0031 | `BoundedBodyHandlerCapacityTest` (`:69-81`, re-read): a 4x growth or a removed clamp breaks it by reading; both policies and the ceiling covered (leaf) | none | checked |
| 2 | `HttpJwksLoader:896-908` (re-read), ADR-0031 `:186-199` (re-read) | `HttpJwksLoaderExecutorHandOffTest` `Error` test (leaf: the guard count and the burned window fail without the `finally`) | none | checked |
| 3 | `BearerChallenge:165`, `:225-232` (re-read) | `BearerChallengeTest:298-301` (re-read), the build-step, boot, request-path, multi-buffer and truncation tests (leaf) | none | checked |
| 4 | `ClientConfiguration`, `EgressHostValidator` (leaf) | `ClientConfigurationTest` (diff re-read), `EgressHostValidatorTest:210-221` (re-read), the three builder tests sharing one source (leaf) | one split is not a pure move (L2-1); the off-curve test is tolerant (L2-2) | checked, with two deviations |
| 5 | n/a | n/a | not shown by reading | not shown |
| 6 | the mapping document and two architecture files | n/a | five client test classes unmapped (L2-3); the flake not run | partly delivered |

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L2-1 | The producer-test split is not a pure move: `shouldRejectPortCarryingAllowedEgressHost` changed `contains("allowedEgressHosts entry 1")` to an exact message equality in `TokenSheriffClientProducerEgressTest` (stronger, not weaker; the spec and `AGENTS.md` say a split is a pure move). The `HttpJwksLoaderConfigTest` move is verbatim (leaf) | checked (re-read, old and new) | refutes | `TokenSheriffClientProducerEgressTest.java:116-130` against `6bfc910d` `TokenSheriffClientProducerTest.java:394-407` |
| L2-2 | The off-curve EC test no longer depends on the JDK, as the spec asked, by accepting either of two fixed refusals (`EC_KEY_NOT_P256` or `KEY_TYPE_NOT_ALLOWED`); on a JDK that validates the point at decode only the second is reached, so the on-curve policy branch is not exercised there (leaf). The loosening is the deliverable's own decision | checked (test re-read; the JDK branch the leaf's reading) | refutes (the assertion is looser than before, by design) | `PemKeyPairLoaderAdversarialTest.java:189-213` |
| L2-3 | `doc/developer/test-impl-mapping.adoc` still lacks rows for existing client classes: `CachingDiscoveryResolver*Test`, `RedeemedIdentityRefusalExceptionTest`, `RefreshResultVerifierAdversarialTest`, `RefreshResultVerifierTest` and `TokenEndpointClientScopeNonceTest` appear nowhere in it (a search at `d8aba512`, 0 hits each); the `discovery` row lists three classes. The lane's residue says the same | checked (re-read) | refutes | `test-impl-mapping.adoc:192-211` |
| L2-4 | The lane's advisory F1 holds: the `Content-Length` test covers `BackChannelHttp.send` directly, `RevocationClient` and `TokenEndpointClient`, with a negative and a positive control, but `ParClient`, `UserInfoClient` and `DiscoveryResolver` have no test of their own, so reverting one call site fails no test | checked (leaf; the call sites are the leaf's reading) | refutes | `ParClient.java:116`, `UserInfoClient.java:189`, `DiscoveryResolver.java:177` (leaf) |
| L2-5 | The lane's account that the five `weakened_test` flags are splits, moves or parameterizations is borne out for four shapes by the leaf; the fifth is the tolerant off-curve test (L2-2), which the deliverable names. The producer-test move strengthened an assertion (L2-1). No other assertion is loosened (leaf) | checked (L2-1 and L2-2 re-read; the rest leaf) | confirms | see L2-1, L2-2 |

**Layer 2 verdict:** findings — 5 (L2-1 to L2-4 minor, L2-5 info); D5 and the Playwright and architecture-hint parts of D6 are not shown by reading

### Layer 3 — Correct

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L3-1 | The `catch (IllegalArgumentException)` is as wide as `HttpClient.send`'s own exception: any `IllegalArgumentException` out of `send` (the JDK also throws it for a request it considers invalid) is reported as "the HTTP client refused the response as malformed", and the cause is dropped (only a fixed DEBUG line remains). No raw value leaks, which is the intent; diagnosability of a request-side fault is lost. The JDK's `send` contract is from the leaf's reading, not from this analysis | checked (catch re-read; the JDK contract the leaf's) | refutes | `BackChannelHttp.java:373-379`, `TokenEndpointClient.java:601-611` |
| L3-2 | History-framed wording in a durable ADR: "It conflicts with the PLAN-09 specification, which kept COMMONS-17's bound of the ceiling plus one in-flight chunk; this decision replaces that bound" names an internal plan id and narrates a replaced decision (pre-1.0 rule of `AGENTS.md`); the spec itself asked for the conflict to be named (L1-4) | checked (re-read) | refutes | `ADR-0031:320-321` |
| L3-3 | The client's changed observable result is described only in ADR-0031's Consequences; the "Changed default behaviour" list that D2 required to be complete does not itemise it | checked (re-read) | refutes | `ADR-0031:186-199`, `:356-361` |
| L3-4 | The guard `finally` releases the guard when the hand-off is not accepted; an executor that runs the task inline (the task releases the guard itself) and then throws an `Error` leaves `accepted` false, so the `finally` clears a guard another thread may have taken since, allowing two concurrent fetches. It needs a direct executor plus an `Error` after the run, so it is contrived | checked (code re-read; the scenario is the leaf's) | refutes | `HttpJwksLoader.java:896-908` |
| L3-5 | The two production seams the tests need (`Builder.retainedScopeCount`, `BoundedSubscriber.capacity()` with the class widened from private) are package-private in plain classes: no API surface, no reflection | checked (`retainedScopeCount` re-read; the rest leaf) | confirms | `BearerChallenge.java:225-232` |
| L3-6 | Cosmetic: new `{@value}` Javadoc lines over about 120 columns in `BearerAuth`, `BearerToken`, `BearerTokenResult`, and long unwrapped lines in the ADR and `transport.adoc` | not re-read (leaf) | refutes | leaf |
| L3-7 | Not shown by reading: whether the zoned-literal test, the `Content-Length` tests and every "fails without the change" claim pass or fail, the full `-Ppre-commit` plus `clean install`, and the Sonar open-issue count | not_shown_by_reading | none | run-only |

**Layer 3 verdict:** findings — 7 (L3-1 to L3-3 minor, L3-4 to L3-6 info, L3-7 not shown by reading). No blocker, no major. Nothing was fixed inline; no staged fix plan is created, because PLAN-11 is the epic's last queued plan and these items wait for the operator's word (see Follow-ups).

### Conformance

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` | pre-1.0: no `@Deprecated`, no transitional or history-framed wording | deviates | no `@Deprecated` (leaf); history framing at `ADR-0031:320-321` (re-read) |
| `AGENTS.md` | JUnit 5, AAA, no Mockito or Hamcrest, parameterized for three or more variants, injected time | conforms (leaf) | build-step 5 rows, request-path 4 rows, refusal classes, capacity, multi-buffer; an injected `AtomicLong` clock in the hand-off tests |
| `AGENTS.md` | a negative control per regression test | conforms (leaf) | controls in the capacity, multi-buffer, boot, retention (64 scopes, re-read), `Content-Length` and PEM tests |
| `AGENTS.md` | test classes at or under 500 lines, a split a pure move | deviates | sizes 493, 461, 469 (re-read); the producer split strengthened one assertion (L2-1) |
| `AGENTS.md` | attacker text through `ErrorTextSanitizer` / `LogSanitizer`; refusal texts fixed and never echo input | conforms (leaf) | `Q-201`, the deployment message, `MALFORMED_RESPONSE` constant, tests assert no cause |
| Standing constraint | default behaviour unchanged and proven so | deviates | the canonical stored form (an operator decision) and the client's `TransportException` (L1-1) |
| Standing constraint | one memory bound, stated once, in COMMONS-17, `transport.adoc`, ADR-0031 and code | conforms, with L1-2 | identical wording; the first-allocation value is unquantified in the documents |
| Standing constraint | native image: plain classes; no new dependency | conforms (leaf) | no reflection, no pom change |
| Spec D4 | `ClientConfigurationTest` untouched except the one approved method | conforms | two hunks inside one method (`git diff -U0`, re-read) |
| Spec | each new test shown to fail without the change; full `-Ppre-commit` plus `clean install`; Sonar 0 | not_shown_by_reading | run-only |

## Metrics / Anomalies

| Fact | Value | Source |
|------|-------|--------|
| Tokens | 25,812,570 (spans populations: 1-init inline, 2 to 6 main plus sub-agents) | `metrics.md` and the landing, read |
| Wall clock | 63,184 s reported (17h33m); worked 3h11m; idle 14h21m (5-execute 9h26m reported, 7h24m idle) | `metrics.md`, read |
| Model / effort | `claude-sonnet-5-5`; effort `unknown` in the landing | landing facts |
| Plugin / executor | 0.1.2133 both (the executor pinned, the plugin cache holds newer versions; lesson 2026-11-18-12-002) | landing facts |
| Finalize | seven `loop_back` step records: `functional-review` three, `code-review` three, `automatic-review` one (`work.log:531,535,547,559,560,591,592`, read) | `work.log` |
| `script_failure` lines | six, all classified in the `script_failure` Watch (`work.log:28,85,98,404,511,572`, read) | `work.log` |

Anomalies:

- 5-execute ended with Playwright ten-run verification owed at the orchestrator tier; it was never run because the E2E environment ran a stale `~/.m2` snapshot (`work.log:484`, `:491`, read; lesson 2026-11-18-12-001). The operator chose to record rather than install into the shared repository.
- The 4-plan phase handshake drifted (`qgate_open_count` and `pending_findings_blocking_count` captured 2, observed 0) because it was captured while two module-mapping Q-Gate findings were pending the operator's ruling (`work.log:83-85`, read): the second drift of that shape, recorded in the `script_failure` Watch.
- A guarded `manage-status` refusal ("1 of 8 worktree-branch commit(s) ... carry no kind=change ledger row", `work.log:511`) and a `no_sanction` source-commit refusal in 6-finalize (`:572`) were met and worked around.

## Routing / Merge Behavior

| Fact | Landing says | Provider re-read |
|------|--------------|------------------|
| PR / merge | #921, `merged` | `ci pr view`: `state: merged`, merge commit `d8aba512`, head branch `feature/plan-11-final-residuals-sweep`; **confirmed** |
| `unresolved_review_threads` | `unknown` | `ci pr comments`: `reviewThreads` 0 observed; **no false claim** (the landing asserted nothing). `runbook_lane review-gate` returned `merge_allowed: false`, `reason: undecidable_read` (the review-body section "Minor comments: 23" could not be parsed), so the gate's own numbers are unavailable |
| `review_body_findings_total` / `_handled` | `unknown` / `unknown` | not re-readable (the parser refused the body); recorded `unknown`. CodeRabbit's one review body lists 23 minor and 2 nitpick comments (its headings) plus one outside-diff comment; the lane's triage reply counts 26 items, 21 fixed in `519a4fa` (a commit that exists, `git cat-file`) and five not changed |
| `sonar_open_new_issues` | `0` | `sonar read_open_issues` for #921: `undecidable` (`head_revision_unavailable`, analysis revision `97ddb582`); recorded **unknown**, the lane's `0` stands as its claim and was not re-verified |
| Merge path | merged through the merge queue | `ci pr view`; `branch-cleanup` "enqueued via pr merge-queue and corroborated merged" (`work.log`, read) |

The five review items the lane left unchanged, as its triage reply states them (the reasons are the lane's): (1) a `204` from the token endpoint with a malformed `Content-Length` (outside RFC 6749 section 5.1's contract); (2) ArC-generated beans in the deployment scope check (a processor input change and a boot test, deferred); (3) a mutable scope set cleared after `getBearerTokenResult` (pre-existing producer behaviour); (4) deriving the refusal-class cases from the validator (needs a production enum, deferred); (5) the 65-required-scopes test case (deferred).
Operator overrides in the run (the lane's account): `functional-review` and `code-review` were closed by operator override (the five `weakened_test` flags, four overrides); `automatic-review` was closed with an operator skip because CodeRabbit's last review covers `f6bb6db9`, not the final head `97ddb582`. Both claims are consistent with the log's `loop_back` records and were not otherwise re-verified.

## Reconciliation

- Queue: PLAN-11 `running` → `shipped`; `pr` `#921`; `landing` `landings/PLAN-11.md`; `plan_marshall_plan_id` `plan-11-final-residuals-sweep` (carried by the landing message, so no operator word was needed).
- Parallelization: PLAN-11 and PLAN-10 were paired on the operator's answer to the overlap prompt (six shared files by the gate's reading). PLAN-10 has not landed, so no rebase conflict is observed; the realized surface shows what the gate could not predict: PLAN-11 touched six client main files and one test PLAN-10 owns (`ClientConfigurationTest`) that its declaration did not list (`BackChannelHttp`, `TokenEndpointClient`, `DiscoveryResolver`, `ParClient`, `UserInfoClient`, `RevocationClient`). PLAN-10 must rebase over `d8aba512` (recorded in its queue annotation). The spec is not edited: it is shipped, so the under-declaration is recorded here for the pairing history.
- `epic.md`: the PLAN-11 Queue annotation and Decisions entry, the PLAN-03 and PLAN-09 residual entries that were "staged as PLAN-11 deliverable N" (what closed and what remains), the new Open Defects and the E2E Watch were written with this landing.
- The announcement of PLAN-11's edit to `ClientConfigurationTest` (inbox `-001`) was never delivered to PLAN-10's mailbox; PLAN-10's plan id is not stamped, so the orchestrator cannot address it.

## Follow-ups

None is staged: PLAN-11 was the last queued plan and the operator's word is needed for any new plan. Open items carried to `epic.md` Open Defects: the five review items the lane left unchanged; the per-call-site `Content-Length` tests (F1); the unmapped client test classes; the unreproduced Playwright flake and the stale-snapshot E2E environment; COMMONS-17's unquantified first-allocation term; ADR-0031's history framing and its missing itemisation of the client change; the width of the `IllegalArgumentException` catch.
