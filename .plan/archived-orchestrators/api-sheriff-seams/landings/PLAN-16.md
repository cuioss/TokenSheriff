# Landing Analysis: PLAN-16 — Statements and fixture residuals

epic: api-sheriff-seams
workstream: WS-02
pr: #945 (https://github.com/cuioss/TokenSheriff/pull/945), merge commit `7dfefcdea50e7ad414f05dc00877b8217f129745` (squash through the merge queue), 17 files (+507/-140)

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims against ground truth. The landing arrived as inbox message `plan-16-statements-and-fixture-residuals-009` (a `landing` with a `landing-facts` block) plus eight candidate-lessons (-001 to -008). The lane's plan id is `plan-16-statements-and-fixture-residuals`, stamped on the row when the operator said the plan was running; executor and plugin `0.1.2133`; 24h13m wall time and 13.7M tokens (the lane's `record-metrics`, not re-run). The row went `staged` to `running` on the operator's word and to `shipped` on this landing's evidence.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Every statement about the stalled body and the parser configuration says what the code does | **shipped as specified** | The three statements say both outcomes now: `ClientConfiguration.java:183`, `TokenEndpointClient.java:619-620`, `doc/commons/requirements.adoc:175` (orchestrator search of the merged tree for the timeout phrases: every remaining hit is two-outcome, a headers-wait statement or an observation; ADR-0031:367, `transport.adoc:239`, both threat models and `BackChannelHttp.java:141` agree). The two "pre-initialized on the caller's thread" sentences of `HttpJwksLoaderConfig` are removed (leaf, `:216` and `:676`, Javadoc only; not re-read), and `JwksParserConfigBootTest`'s Javadoc is rewritten (leaf). Least precise remaining wording: the `transport.adoc:219` table cell (leaf, not re-read). |
| 2. The fixture and its tests release on every path and pin what they claim | **shipped, minor residuals** | `RawHttpServerTest` closes the injected server and the stalling client in an `@AfterEach` (`:78-98`, re-read) and `isConnected()` is gone (leaf); the accept-count reset and the uncounted reset are pinned (leaf, `:301`, `:321`, `:341`); the accept loop's end at the bound is recorded in `endedAtAcceptBound`, asserted in three tests (`RawHttpServer.java:247-250`, re-read) — recorded, not logged, and only on the `SocketException` path (L3-1). `BearerScopeValidationVetoedPackageBuildStepTest:108-150` (re-read) drives the real `validateAnnotationScopes` with the package-info in each build item in turn plus a control without it, so the call site is pinned, not only the helper. The probe (`BackChannelHttpBodyPhaseTest:108-145`, re-read) uses `assertThrows`, and its JDK 27 branch accepts exactly `HttpTimeoutException` or exactly `IOException` and rejects both fixed texts (any message of the plain `IOException`); the Sonar finding at it is closed (below). The other four clients' case is renamed and described as the characterisation it is (`BackChannelClientExchangeFailureTest`, leaf: it does not pin the fixed-text mapping for them, and no new test for that was added). The duplicated checked-cause case is removed from `BackChannelHttpExchangeTest` and its class Javadoc agrees (leaf); the removal needed the operator's acceptance at both reviews. Red proofs are not shown by reading. |
| 3. The framed names that are left are renamed or listed; the broad catch is listed in `AGENTS.md` | **shipped, one inexact sentence, framing words left in Javadoc** | `JwtTokenTamperingUtilReproductionTest` is `JwtTokenTamperingUtilDecodedSignatureTest` (R089, `git diff -M`: no changed assertion line in the test tree, orchestrator count; the `[44]` reference and the mapping row moved, leaf), the "should still close" display name and the `StalePolicyTest:201` display name are changed (leaf). `AGENTS.md:330-333` now names `RawHttpServer.closeRecordingFailure` under "release steps that must all run" (re-read), but says the fixture "follows the same rule" as `TokenValidator`, whose preceding sentence says it rethrows the last failure, while `RawHttpServer.close()` rethrows the first and suppresses the rest (`:192-203`, re-read) (L2-1; the lane lists it). |
| 4. The dev-mode fix is shown, and the class loader that reads the client module's converters is settled | **delivered** (the leaf could not see the evidence: it is in the plan's work files, not in the diff) | The plan's orchestrator ran `start-dev-environment.sh` at the operator's request with port 8080 free (`decision.log:67`, `:70` and `:71` of the archived plan, read; a dispatched leaf could not start the long-lived processes): "E2E Dev-UI Test Environment Ready", health 200 in 28 s; the kept `work/d4-dev-mode/quarkus-dev.log` (re-read) shows Quarkus started in 5.976 s with 0 `TokenSheriff-141` lines and no `ERROR` line (it does not show a JWKS parse attempted, so the absence is a reading, not a proof). The class-loader question was settled by the operator's decision "accept with limit" (`decision.log:70`): `ClientCompiledJsonParserConfigBootTest` (new, 115 lines, in `token-sheriff-client-quarkus-deployment`) parses a client `@CompiledJson` type and `Jwks` through one `ParserConfig` on a thread with the platform context loader; `ParserConfig.java:239-244` states that a client loaded by a class loader this one does not see is not covered (6 added lines, Javadoc only, re-read); `pom.xml` and reflect-config are untouched; the production fix did not change. The landing narrative says nothing of the dev-mode run (the commit message does). |
| Sonar | **the project is at 0** | `sonar read_open_issues --project cuioss_TokenSheriff` on `main`: `count_status: confirmed`, `open_issue_count: 0`, quality gate `OK` (orchestrator, after the merge analysis of 2026-11-22T16:21Z). The finding PLAN-16 carried (`java:S8714`, `AaH5fQ48N3wbMb6uxphI`) is closed. The per-PR read is `undecidable` (`head_revision_unavailable`, analysis revision `50f3d9b0`). |

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, role `orchestrator.analyze`, resolved from `plan.effort`; 43 tool calls, 181k tokens), and the orchestrator re-read the points recorded below as `checked` before writing them. The reading is by reading: no build, no test run, no write.

- **Dispatch:** one read-only leaf per landing — reason: reading the merged fixture, the probe, the veto test and a 17-file diff against the spec is the depth the dispatch rule exists for; one landing, so one leaf; the leaf wrote nothing.
- **Verdict:** layer 1 clean (the spec's premises held; the criteria that need history or runs cannot be read from a squash, a known limit); layer 2 findings (no major, six minor); layer 3 findings (one minor, one not shown; no blocker, no major).
- **Re-read by the orchestrator at `7dfefcde`:** the timeout statements (search over sources and `doc/`); `AGENTS.md` diff; `ParserConfig.java` diff; `RawHttpServer.java:192-205`, `:240-284`; `RawHttpServerTest.java:76-100`, `:300-312`; `BackChannelHttpBodyPhaseTest.java:108-145`; `BearerScopeValidationVetoedPackageBuildStepTest.java:108-150`; the framing words in test Javadoc (two hits); the changed-assertion count of the rename; the line counts of the touched classes; the merge's squash message; the archived plan's `work.log`, `decision.log` and `d4-dev-mode` logs. **Not re-read (unverified leads):** `HttpJwksLoaderConfig` Javadoc `:216`, `:676`; `transport.adoc:219`; `BackChannelClientExchangeFailureTest:204`; the ScriptedAccept test details beyond `:301`; `JwtTokenTamperingUtilDecodedSignatureTest:35` (the Javadoc overclaim); the `Regression tests` Javadoc hits at `PemKeyPairLoaderAdversarialTest:43` and `DiscoveryResolverTest:266`, `:295`; `ClientCompiledJsonParserConfigBootTest` internals.

### Layer 1 — Specified

- **Held:** claims 0 to 10 (the three statements, the probe's too-wide branch and its fixture, the lazy `dslJson` and the stale `HttpJwksLoaderConfig` sentence, the leak and `isConnected()`, the helper-level wiring test, the framed names, the unlisted `AGENTS.md` site, the PLAN-15 dev-mode log, the Sonar read, and the two HYPOTHESES on the four clients and the accept count) all held at `a433e990`.
- **Settled by the plan's own run:** claim 12 (dev mode reaches ready after the fix): it did, in a run the plan's orchestrator made at the operator's request, with the log kept (above).
- **Not shown:** claim 11 (the loader of `ParserConfig` does not see a client jar loaded by a child loader): true by delegation semantics, but whether Quarkus loads the two jars that way is not shown; the plan accepted it with a stated limit.
- **L1-1 (minor, a known limit of the squash).** Criteria that need history or runs cannot be read after the merge: "each rename its own commit", "a recorded red proof" per test, the full `-Ppre-commit` and `clean install`. The commit message claims 5839 and 5833 tests green; only the end state is read.

### Layer 2 — Implemented

- **L2-1 (minor).** `AGENTS.md:330-333` says the fixture "follows the same rule" as `TokenValidator` (rethrow the last, earlier ones suppressed) while `RawHttpServer.close()` rethrows the FIRST and suppresses the later ones; the catch itself matches. The lane lists it; it fails the spec's own criterion "`AGENTS.md` and the code agree on the catch" in one detail.
- **L2-2 (minor).** Framing words left in Javadoc, neither renamed nor listed: `RawHttpServerTest.java:44` ("still closes the served connection", the word the spec itself called framed at the display name) and `BackChannelOriginValidatorTest.java:31` ("Regression tests pinning ...") re-read; the leaf names `PemKeyPairLoaderAdversarialTest:43` and `DiscoveryResolverTest:266`, `:295` too (not re-read). The spec's search covers method names, display names and class names, which are clean, so this is Javadoc wording the rule `AGENTS.md` states (no history framing in Javadoc) still catches.
- **L2-3 (minor, not re-read).** The class Javadoc of `JwtTokenTamperingUtilDecodedSignatureTest` says the two inputs are ones on which "a character-level change alone would leave the decoded bytes", but `JwtTokenTamperingUtil` modifies several characters, so the claim about the last-character padding bits overstates the cases (the lane lists it).
- **L2-4 (minor).** The probe's JDK 27 branch is tight on the class (`HttpTimeoutException` or exactly `IOException`) but not on the message of the plain `IOException` (re-read); that the JDK reports exactly that class and not a subclass on JDK 27 cannot be shown by reading (a subclass would fail the test).
- **L2-5 (minor).** `ClientCompiledJsonParserConfigBootTest` has a positive control (`Jwks`) but no in-test negative control, its precondition assertion (same loader as `ParserConfig`) runs after the parse, and it does not cover a client loaded by a child loader, which `ParserConfig`'s Javadoc and the test's own Javadoc state (leaf, not re-read). Whether it fails without the `ParserConfig` fix is argued, not run.
- **L2-6 (minor).** The four clients' case is a characterisation of their `IOException` catch, and no test pins the fixed-text mapping through them (leaf); the mapping is pinned by the mapping tests and the `TokenEndpointClient` cases, as the PLAN-15 report said.
- **Traced and sound:** D1's sweep, D2's fixture release, the accept-count tests, the veto call-site pin, the rename (no assertion line changed), the mapping rows, the line counts (largest `RawHttpServerTest` 489, up from 322; every touched class at or under 500). All 17 changed files are explained; all main-source changes are comments or Javadoc.

### Layer 3 — Correct

- **L3-1 (minor, reported not staged).** The accept-loop end is recorded in a volatile flag that only the `SocketException` branch sets (`RawHttpServer.java:247-250`); a persistent non-`Socket` `IOException` from `accept()` or from serving ends the thread through an `IllegalStateException` on the pool thread (`:252-254`, `:280-282`), with the flag false and the socket open, so a test that never calls `endedAtAcceptBound()` still sees a dead server as a connect hang or refusal. Fixture only, not covered by a test.
- **L3-2 (not shown by reading).** The probe's `HttpClient` from `sharedClient` is never closed (`BackChannelHttpBodyPhaseTest:163`, unchanged lines); not shown to leak a thread. The boot test's executor is stopped by `shutdownNow` in a `finally` without an await (leaf; minor).
- **Checked and sound:** thread safety of the new flag (volatile, written once, read after `awaitTermination`), `close()` under `connectionLock`, the test helper's narrow catch (no directive needed).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` Pre-1.0 | no `@Deprecated`, no transitional comments | conforms | none in the added lines |
| `AGENTS.md` Pre-1.0 | no history-framed wording in names, display names, Javadoc | **deviates** (Javadoc only) | L2-2; names and display names are clean |
| `AGENTS.md` Test Class Size | new or grown classes at or under 500 lines | conforms | `RawHttpServerTest` 489, the others 349 or less; nothing split |
| `AGENTS.md` Test Class Size | renames are pure moves | conforms | R089, no changed assertion line; the Javadoc was rewritten in the same squash, so a strict pure move cannot be read |
| `AGENTS.md` Testing | JUnit 5, AAA, no Mockito/Hamcrest, no sleeps | conforms | leaf |
| `AGENTS.md` Testing | a negative control per regression test | **deviates** (one) | the new boot test has no negative control (L2-5); red proofs are not shown by reading |
| spec | a fixture closes its sockets and stops its threads on the failure path | conforms | `@AfterEach` and try-with-resources (re-read); L3-2 minor |
| `AGENTS.md:296-345` | a broad catch only in a sanctioned form, listed | conforms, one inexact sentence | the site is listed; L2-1 |
| `AGENTS.md` Logging / CWE-117 | no new log, no token or key material | conforms | leaf |
| spec | no `pom.xml`, reflect-config or new dependency | conforms | not in the diff |
| spec Done-when | `-Ppre-commit` and `clean install` green; Sonar | not shown by reading | the commit message claims the counts; Sonar on `main` is 0 (above) |

## Metrics and Anomalies

- **Landing completeness:** `inbox landing-check` answered `complete: false`, `missing_keys` `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (all `unknown` in the block; the landing's residue says no finalize step records them and gives observed values).
- **Review and Sonar numbers re-read from the provider:** `review-gate` answered 0 unresolved threads, 1 review-body finding, 0 handled (`body_findings_undisposed`, `merge_allowed: false`, the CodeRabbit inline comment on `test-impl-mapping.adoc`; the gate does not read the lane's `Triage dispositions` reply, the known blind spot); `ci pr comments`: one thread, resolved, with the lane's reply and CodeRabbit's acknowledgement; `sonar_open_new_issues`: the PR read is `undecidable` (recorded unknown, never `0`; the lane's `0` is at the head and leaves out hotspots) and the `main` read is 0 confirmed. The landing's `unknown`s are unread numbers, not false claims.
- **Review bots:** CodeRabbit (required) reviewed the final head with one inline comment (derive the test inventory in `test-impl-mapping.adoc` from source), taken into account and deferred; Sourcery (optional) refused on its quota. The CodeRabbit allowance (one review per hour) was used up by this one review, the lane notes.
- **Operator acceptances:** two `weakened_test` findings accepted at both the functional review and the code review at one head (four findings, two loop-back rounds): the deliberately removed duplicate checked-cause case and the class rename (the tenth plan to meet the carry-forward ask; see the Watch).
- **Script failures in the archived work log:** read and classified in the Watch of `epic.md`: `get-deliverable --number` twice (`work.log:57`, `:111`), `--changed-paths-file` once (`:82`, executor 0.1.2133, the skew), a transition blocked by `worktree_mutated` (`:207`) and refused for one commit with no `kind=change` row (`:217`, `50f3d9b0`).
- **Post-merge wait:** the lane's post-merge CI wait did not settle within the 600 s call cap and was stopped (the lane's account; lesson 2026-11-20-15-001's class, already stored).

## Routing and Merge Behavior

- Merged through the merge queue as `7dfefcde` on the first attempt (`merge_mechanism: merge_queue`, `cleanup_owed` false). No queue ejection and no operator departure from the spec this time.
- The installed `marshal.json` is still stale and the executor still 0.1.2133; the steward upgrade has not been run (the operator's call).
- The PLAN-16 ledger writes (the running record) and this analysis land through the next ledger PR.

## Reconciliation Actions

- Landing report written (this file); the row `PLAN-16` `running` to `shipped`, with `pr` `#945` and `landing` `landings/PLAN-16.md` (`plan_marshall_plan_id` was stamped at the running record).
- Layer-1 verdicts persisted by `corpus set-verdict` for the spec's claims 0 to 12 (0 to 10 and 12 `corroborated`, 11 `unverifiable`); `corpus verdicts` `blocking_count` is unchanged by this plan (the rows that block belong to shipped or retired specs and block nothing).
- Candidate-lessons: one folded into a lesson stored last turn, three folded into older lessons, the rest absorbed into Watches, an Open Defect or discarded; each has an `epic.md` entry and a decision-log line.
- `epic.md`: the PLAN-16 queue annotation, the drain Decision, the Open Defects entry "PLAN-16 landed" and the extended Watches.

## Follow-Ups

- Nothing is staged. The layer-2 and layer-3 minors are in the Open Defects entry "PLAN-16 landed", reported to the operator in the same turn; a plan needs the operator's word.
- With PLAN-16 shipped no plan is running, so the steward upgrade (executor regeneration, `marshal.json` refresh) is safe to run; it is the operator's.
