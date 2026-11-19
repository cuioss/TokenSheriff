# PLAN-13: Final finalize

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-13-final-finalize.md` and is queued as one row file, `queue/PLAN-13.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the operator's word of 2026-11-19 ("handle-open. gather in a final finalize plan") after the PLAN-12 landing (#925, `ae493e00`, `landings/PLAN-12.md`). It gathers every repository-side item
> the Open Defects entry "PLAN-12 landed" in `epic.md` lists as still open, so that this is the last plan of the epic: after it, the code, documents and tests that ship in 0.9.10 say what they do, and what was never shown is shown or recorded as not showable.
> Scope: six deliverables, at the guard's limit. The operator asked for ONE final plan, so it is not split here; the outline splits it if it scopes above about six tasks (see the verify-first clause).
> NOT in this spec (not this repository's files, or not a plan's work): the marketplace-side findings (the `no_sanction` commit refusal, seven plans; the `weakened_test` carry-forward ask, six plans; the code-review validator and the reuse rule for a closing `full` round; the scope-creep guard; the `review_completeness` flag families; the docs-versus-executor version skew, lesson 2026-11-18-12-002; the change-ledger remap after a branch-cleanup rebase, lesson 2026-11-19-07-007; the review gate that does not read the lane's replies and cannot read the "moderation" section of #907),
> the lessons missing from the lessons store, API Sheriff's own deletions (its PLAN-34) and the compare-and-set watch (its PLAN-33).

## Objective

PLAN-12 shipped the DPoP guard, the PLAN-10 and PLAN-11 leftovers and the mapping rows, and left a list it recorded and did not fix: a transport exchange whose failure classification is wider than the failure it names and whose branches no test reaches, a deployment check that does not read a package-level `@Vetoed`, test names, file sizes and a second top-level class that break the repository's own rules, documents that still describe the catch the code no longer has, a Playwright flake that was never run, and a set of checks nobody could show (the full build, the red proofs, the native image, the Sonar count). This plan closes them in one pass, so that nothing the epic shipped is left on a list. No public API is added beyond what a fix needs, and the default behaviour of every class named here does not change except where a decision below says so.

## Deliverables

1. **The transport exchange classifies only what it names, and every branch of it is tested (PLAN-12 layer 3).** `BackChannelHttp.isMalformedFraming` returns `true` for any `IllegalArgumentException` of the exchange future, so a response-side `IllegalArgumentException` is reported as "malformed response" with its cause dropped; `BackChannelHttp.exchange` maps no `CancellationException` from `pending.get()`; its mid-wait interrupt branch, the cause unwrap and the cause-mapping tail (`IOException`, `RuntimeException`, `Error`, the wrapped `IOException`) have no test; the parameter of `isMalformedFraming` is nullable by its own Javadoc and not annotated; `pending.get()` has no timeout of its own.
   Re-read the code at HEAD and settle at outline, with the operator: narrow the `IllegalArgumentException` branch to the form the JDK produces for a malformed `Content-Length` (a `NumberFormatException` cause), or state the width in the Javadoc and the documents; map `CancellationException` to a fixed failure, or let it propagate and say so; bound `pending.get()`, or state that the request's own timeout is the bound and show that every request the helper receives carries one; whether a raw request-side `IllegalArgumentException` that leaves `TokenEndpointClient` is the contract (state it in the `@throws` and the documents) or a `TransportException` (change it, with a test). Each behaviour item gets a test that fails without the change, with a positive control; each untested branch of `exchange` gets a test.
   Verification: the existing `BackChannelHttpMalformedResponseTest`, `BackChannelHttpRequestRefusalTest`, `TokenEndpointClientTest` and the per-call-site malformed-`Content-Length` tests pass; the default behaviour for a valid response and for a malformed `Content-Length` (JDK 21, 25 and the CI `build (27)` leg) is unchanged.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-http, pm-dev-java-cui:cui-logging, plan-marshall:ref-code-quality
   - Persona: implementer
2. **The deployment scope check and the startup guards say what they check (PLAN-12 layer 3 and review advisories).** `TokenSheriffProcessor.isBeanClassCandidate` reads `@Vetoed` on the class only, so a concrete class in a `@Vetoed` package is a bean candidate and its class-level scopes are merged; the test `shouldCheckMethodOfVetoedClassAlone` has no positive control; the `@DisplayName` of `BearerScopeValidationCombinedIndexBuildStepTest` is narrower than its `@Vetoed` tests; `BearerScopeValidationGeneratedBeanControlBootTest` is a second top-level class in `BearerScopeValidationGeneratedBeanBootTest.java`; the literal minimum `4` in `JwtPropertyKeys` (the third argument of `declaredKeysUnder` for `DPOP_SUB_PROPERTIES`) duplicates the declared key count, so a fifth sub-property passes silently.
   Settle at outline, with the operator: read the package-level `@Vetoed` (a processor input change and a boot test), or state the limit in the Javadoc and ADR; keep the literal minimum with the exact assertion that pins it, or derive the expectation from an oracle independent of the list. The second top-level class moves to a file of its own as a pure move, and the display name and the missing control are corrected.
   Verification: each new test fails without the change it pins; `BearerScopeValidationBuildStepTest`, `TokenSheriffProcessorBuildStepTest`, `JwtPropertyKeysTest` and `JwtPropertyKeysDerivationGuardTest` pass; the orphaned-DPoP behaviour for a valid configuration is unchanged.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-quarkus, pm-dev-java:junit-core, plan-marshall:ref-code-quality
   - Persona: implementer
3. **The tests and the end-to-end helpers meet the repository's own rules (PLAN-12 rules not met).** Three test names keep the framing the pre-1.0 rule forbids (`shouldPropagateRequestRefusalUnchanged`, `shouldStillMapMalformedResponseToFixedFailure`, `resultUnchangedAfterCallerClearsRequirementSets`); `TokenEndpointClientTest` (903 lines) and `BearerTokenProducerTest` (607 lines) exceed the 500-line rule; `BackChannelHttpRequestRefusalTest` and `BackChannelHttpMalformedResponseTest` each carry their own raw server loop; `BackChannelHostnameVerificationTest` still names `HttpClient.send` in a comment about a path that now runs through `sendAsync`; the waits in `devui-navigation.js` repeat a test id that `utils/constants.js` declares.
   Splits are pure moves by their own commit; a rename is its own commit with no assertion change; a shared server fixture is one class both tests use, if the outline finds the duplication is real. Every test class the splits add has its row in `doc/developer/test-impl-mapping.adoc` (deliverable 4).
   Verification: `wc -l` of every changed test class is at most 500; no test method or class name in the changed files carries the forbidden framing (a search); the assertion count of each moved test is unchanged, shown by a before-and-after count recorded in the progress record.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-frontend:javascript, plan-marshall:ref-code-quality
   - Persona: module-tester
4. **The documents say what the code does, and carry no history or compatibility framing (PLAN-12 documentation lag).** `doc/commons/requirements.adoc` (COMMONS-17, about `:190-203`), `doc/commons/specification/transport.adoc` and ADR-0031 (`:335` and the "Changed default behaviour" list) still say the clients catch the JDK's `IllegalArgumentException` and omit the JDK 27 `ProtocolException` chain that `isMalformedFraming` recognises; the `BoundedBodyHandler` class Javadoc (`:69-73`) says the same; the `Content-Length` timing sentence of COMMONS-17 and ADR-0031 sits next to the `204` exception; ADR-0031 `:196` ("Before, the JDK's `IllegalArgumentException` ... escaped the call") and the compatibility rationale of `PushedAuthorizationEndpointMissingException` (`:29-40`), of `doc/commons/requirements.adoc` and of `doc/client/specification/retrieval-flow.adoc` (`:114-115`, "every existing catch ... stay as they were") are history or compatibility framing against the pre-1.0 rule; `AGENTS.md:159` says `INFO (001-099)` where the identifiers are unpadded (`1-99`); `doc/client/requirements.adoc` carries a logout-endpoint sentence (the `allowInsecureHttp` option) that no deliverable of PLAN-12 names and that matches `EndpointSchemePolicy`: re-read it, keep it with its reason recorded, or correct it.
   Every statement is re-read against the code at HEAD after deliverables 1 and 2, so the text states what they settled. `doc/developer/test-impl-mapping.adoc` gains a row for every test class deliverables 1 to 3 add or split, and the operator decides at outline whether the class inventory is generated from the test declarations (declined in #925, "recorded for a later plan") or stays hand-kept with a search as its check.
   Verification: a search of each document for the stale wording, with zero hits for the old catch and the framed phrases; a search of the mapping document for each class named here and each class the plan adds, with zero misses; `-Ppre-commit` documentation checks pass.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation, pm-dev-java:javadoc
   - Persona: documenter
5. **The Playwright flake and the start script are run, not only read (lessons 2026-11-15-18-003 and 2026-11-18-12-001).** `start-dev-environment.sh` builds the branch into a run-private Maven repository (PLAN-12) and was never executed; the Token Debugger page accessibility step (`accessibility-wcag-compliance.spec.js:57-66`) was given a render wait in `goToTokenDebugger` by reading; `self-devui-accessible.spec.js:48` still navigates to the Token Debugger without it; the ten consecutive runs were not done, by the operator's decision of 2026-11-18T18:01Z, because they need Keycloak and dev mode on the operator's machine and the Windows npm failed on UNC paths.
   Settle at outline, with the operator, whether the runs are done in this plan on a machine that has the environment, or recorded as not done with the reason and the identifying signature of the flake as a known one; the plan never silently skips them. If run: execute the start script, show it prints the repository it uses, run the spec ten times, apply the render wait to `self-devui-accessible.spec.js:48` and show the same step passes ten times. If not run: the record names the missing precondition and who has it.
   Verification: ten consecutive passing runs against the branch build with the log kept, or the record of why not; `start-dev-environment.sh` exits as documented at least once.
   - Skills: pm-dev-frontend:javascript, plan-marshall:ref-code-quality
   - Persona: implementer
6. **What PLAN-12 could not show is shown, or recorded as not showable, and the review records are closed.** Not shown by reading in `landings/PLAN-12.md`: that the new tests fail without their change (the recorded red proofs), the full `-Ppre-commit` plus `clean install` of `AGENTS.md`, the Sonar count at the head (`undecidable` for a merged PR; the lane said `0` at `33e68ac0`; security hotspots not read), whether the DPoP list is non-empty in a native executable (the operator chose the fail-closed guard alone; a native build of `token-sheriff-quarkus-integration-tests` would show it), `DiscoverySpecIT`, and that the post-merge JMH benchmark workflow finished. The review gate for #925 answers `body_findings_undisposed` (5, handled 0) because it does not read the lane's replies, and the per-PR dispositions for #898 to #912 and #920 were not re-read by the orchestrator.
   For each item the plan records one of: shown (the command and its result), or not showable here with the reason. For the review records: a table per PR of finding, disposition and evidence, read from the PR threads and the archived runs, and `runbook_lane review-gate --pr-number N` read for each PR with its answer stated next to the table; no PR comment is posted without the operator's word.
   Verification: the table and the list of shown and not-showable items are in the PR body and the progress record; the Sonar open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).
   - Skills: pm-documents:ref-documentation, plan-marshall:ref-code-quality
   - Persona: documenter

Done when: all criteria pass, the existing tests named in the Standing Constraints pass without edited assertions except where a deliverable names the decision, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`). After the plan ships, every item of the Open Defects entry "PLAN-12 landed" that is this repository's is closed or carries a recorded reason, and the epic has no repository-side item left to stage.

**Operator decisions needed at outline**

- **The width of the exchange's `IllegalArgumentException` branch and the `CancellationException` mapping** (deliverable 1): narrow and map, or state the limit; a public-class contract of 0.9.10.
- **The bound of `pending.get()`** (deliverable 1): a timeout of its own, or the request timeout stated as the bound and shown to be set on every request.
- **The raw request-side `IllegalArgumentException` from `TokenEndpointClient`** (deliverable 1): state it as the contract, or wrap it in a `TransportException`.
- **A package-level `@Vetoed`** (deliverable 2): read it, or state the limit.
- **The literal DPoP minimum of four** (deliverable 2): keep it with its pinning test, or derive the expectation independently.
- **Whether the shared raw server loop becomes one fixture** (deliverable 3): only if the outline finds the duplication is real.
- **Whether the mapping inventory is generated** (deliverable 4): generate it, or keep it hand-kept with a search as the check.
- **Whether the ten Playwright runs are done in this plan** (deliverable 5): on a machine with Keycloak and dev mode, or recorded as not done.
- **Whether a native build is run for the DPoP list** (deliverable 6): run it, or record the fail-closed guard alone as the reason.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no history-framed wording in anything this plan writes, **including test method names, ADR text and the PR body's description of the code**.
- **Default behaviour stays unchanged and is proven so**, except where a decision above states a change and a test proves it: `BoundedBodyHandler`'s ceiling and `OverLimitPolicy` behaviour, the malformed-`Content-Length` mapping for a valid exchange (a `TransportException` with the fixed text, no chained cause), `PushedAuthorizationEndpointMissingException` as a `ClientProtocolException`, the orphaned-DPoP check for a valid configuration, and the existing tests of PLAN-10, PLAN-11 and PLAN-12 (`BackChannelHttpMalformedResponseTest`, `BackChannelHttpRequestRefusalTest`, `ParClientMalformedResponseTest`, `UserInfoClientMalformedResponseTest`, `JwtPropertyKeysTest`, `JwtPropertyKeysDerivationGuardTest`, `TokenSheriffProcessorBuildStepTest`, `BearerScopeValidationBuildStepTest`).
- **Say only what was read.** A corrected statement is checked against the code at HEAD before it is written; a statement that only a run could settle is run or left out; the JDK 27 form rests on the CI `build (27)` leg, not on a local run (the local JDK is Temurin 25). Every line number in this spec is re-read at HEAD at outline.
- **Test-class splits are pure moves** (`AGENTS.md`): a new test goes in a new or an existing class by its own commit, never inside a move; a rename is its own commit.
- **Security.** Attacker-controlled text goes through `ErrorTextSanitizer` (validation) or `LogSanitizer` (client); no token or key material is logged; the CWE-117 rule of ADR-0022 holds for every new or changed log line; the raw header value of a malformed `Content-Length` is never chained or echoed; refusal texts stay fixed. A new LogRecord takes the next free identifier in its range at write time and is added to `doc/LogMessages.adoc`.
- **Native image.** The helpers stay plain classes; no reflect-config entry and no new dependency without operator approval.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never sleeps), a negative control per regression test, new or grown test classes at or under 500 lines.
- Never gate on `test-compile`/`test`; use `verify`. **Never install into the shared `~/.m2` without the operator's word.**
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.
- **Review-fix discipline** (lessons 2026-11-17-16-001, 2026-11-17-16-002 and 2026-11-18-13-003): before applying a review-bot suggestion that changes a bounded structure, read the requirement row that states the bound; when an accepted fix contradicts a frozen deliverable criterion, ask in the same operator question whether to authorize the criterion change.
- **The plan's own rules** (lessons 2026-11-19-07-001 to -009, from PLAN-12's landing): a framed test name, a test class over 500 lines and a second top-level class are each a defect of this plan, not an advisory; the final review rounds record how much of the footprint they read.

## Claim Labels

- OBSERVED: `BackChannelHttp.isMalformedFraming` returns `true` when `failure instanceof IllegalArgumentException` (`BackChannelHttp.java:161-162`); `exchange` calls `client.sendAsync` outside the `try`, waits with `pending.get()` (no timeout argument, `:475`), catches `InterruptedException` (`pending.cancel(true)`, `:477`) and `ExecutionException` only, and maps the cause: malformed framing, `IOException`, `RuntimeException`, `Error`, else a wrapped `IOException` — read at `252006c7`
- OBSERVED: `TokenEndpointClient` sends through `BackChannelHttp.exchange(client, requestBuilder.build(), ...)` (`TokenEndpointClient.java:600-604`) and catches `IOException` and `InterruptedException` around it (`:605-620`); no catch for a runtime exception of the exchange — read at `252006c7`
- OBSERVED: `TokenSheriffProcessor.isBeanClassCandidate` rejects a class when `classInfo.declaredAnnotation(VETOED) != null` and reads no package annotation (`TokenSheriffProcessor.java:447-450`); `VETOED` is `DotName.createSimple(Vetoed.class.getName())` (`:127`) — read at `252006c7`
- OBSERVED: `JwtPropertyKeys.ISSUERS.DPOP_SUB_PROPERTIES` is `declaredKeysUnder(ISSUERS.class, DPOP_BASE, Set.of(DPOP_ENABLED), 4)` (`JwtPropertyKeys.java:461`); `JwtPropertyKeysDerivationGuardTest.shouldDeriveProductionList` is the positive control of the minimum (`:157`) — read at `252006c7`
- OBSERVED: the three method names are `BackChannelHttpRequestRefusalTest.shouldPropagateRequestRefusalUnchanged` (`:163`), `shouldStillMapMalformedResponseToFixedFailure` (`:181`) and `BearerTokenProducerTest.resultUnchangedAfterCallerClearsRequirementSets` (`:264`); `TokenEndpointClientTest.java` has 903 lines and `BearerTokenProducerTest.java` 607 — read and counted at `252006c7`
- OBSERVED: `BearerScopeValidationGeneratedBeanControlBootTest` is declared at `BearerScopeValidationGeneratedBeanBootTest.java:139`, after `BearerScopeValidationGeneratedBeanBootTest` at `:55` — read at `252006c7`
- OBSERVED: `doc/commons/requirements.adoc:190-203` says the discovery, PAR, userinfo and revocation clients and the token endpoint client each "catch that `IllegalArgumentException`", and names no JDK 27 form; ADR-0031 `:196` reads "Before, the JDK's `IllegalArgumentException` ... escaped the call"; `BoundedBodyHandler.java:69-73` names the same catch; `PushedAuthorizationEndpointMissingException.java:29-40` states a source-compatibility rationale; `retrieval-flow.adoc:114-115` says "every existing catch and the HTTP-edge 4xx mapping stay as they were"; `AGENTS.md:159` says `INFO (001-099)` while `TokenSheriffQuarkusLogMessages.java:31` says `1-99` — read at `252006c7`
- OBSERVED: `BackChannelHostnameVerificationTest.java:169` names `HttpClient.send(...)` in a comment on the interrupt route through `TokenEndpointClient` — read at `252006c7`
- OBSERVED: `goToTokenDebugger` waits for `jwt-debugger-container` to be visible after `navigateToDevUIPage` (`utils/devui-navigation.js:118-135`); `self-devui-accessible.spec.js:48` calls `navigateToDevUIPage(page, 'Token Debugger')` and asserts only that `qwc-jwt-debugger` is attached — read at `252006c7`
- OBSERVED: the `doc/client/requirements.adoc` diff of #925 holds two hunks, the `PushedAuthorizationEndpointMissingException` sentence in CLIENT-27 (a PLAN-12 deliverable) and the `allowInsecureHttp` logout-endpoint sentence (`:272-278`), the second in no deliverable and no scope-creep entry — diff read between `e53f3e4f` and `ae493e00`
- HYPOTHESIS: the JDK up to 26 reports a malformed `Content-Length` as an `IllegalArgumentException` whose cause is a `NumberFormatException` — the repository's own Javadoc and COMMONS-17 say so ("cause `NumberFormatException`", observed on JDK 25); the JDK source was not read; confirm with a test that builds the exception the JDK builds, or read the JDK (verify-at-outline)
- HYPOTHESIS: a response-side `IllegalArgumentException` that is not a malformed `Content-Length` can complete the future and is today reported as a malformed response with its cause dropped — the PLAN-12 leaf's reading (`landings/PLAN-12.md`, layer 3); confirm with a test that completes the future with such an exception (verify-at-outline)
- HYPOTHESIS: a `CancellationException` from `pending.get()` propagates raw out of `exchange` and every caller — the PLAN-12 leaf's reading of the catch list; confirm with a test (verify-at-outline)
- HYPOTHESIS: every request the helper receives carries a request timeout, so the unbounded `pending.get()` is bounded in practice — the PR body's "relies on the request's own timeout"; not shown; read each call site of `BackChannelHttp.exchange` and `BackChannelHttp.send` (verify-at-outline)
- HYPOTHESIS: a raw request-side `IllegalArgumentException` now leaves `TokenEndpointClient` uncaught, where the previous catch turned it into a `TransportException` — the PLAN-12 leaf's reading, not shown by a test; confirm by a test that sends a request the client refuses (verify-at-outline)
- HYPOTHESIS: a concrete class in a `@Vetoed` package is a bean candidate for the scope check, so its class-level scopes are merged — follows from the predicate above; confirm by a boot or build-step test with a package-level `@Vetoed` fixture (verify-at-outline)
- HYPOTHESIS: the `module_mapping_validator` reads rows of `doc/developer/test-impl-mapping.adoc` — carried from PLAN-12 where its outline was to settle it; read what its outcome was in the archived plan `2026-11-19-plan-12-open-items-sweep` before relying on it (verify-at-outline)
- HYPOTHESIS: the flake's cause is that the debugger element is attached before its module has loaded, so a scan sees an empty component — the PLAN-12 lane's cause, found by reading code only, never reproduced; confirm by the ten runs (verify-at-outline)
- HYPOTHESIS: the Sonar open-issue count of the merged #925 head was `0` — the lane's statement at `33e68ac0`; `undecidable` (`head_revision_unavailable`) for a merged PR, so read it again at this plan's PR head (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (no other plan is running; read what has merged since `252006c7`, in particular any change to `BackChannelHttp`, `TokenEndpointClient` and the Playwright helpers), (3) that the deliverables stay at about six tasks or fewer; if the outline scopes above that, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java`:161 — `isMalformedFraming`; `:462` the exchange
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`:600 — the exchange call
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/PushedAuthorizationEndpointMissingException.java`:29 — the compatibility rationale
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpRequestRefusalTest.java`:163 — two framed names
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpMalformedResponseTest.java`:1 — the `Content-Length` tests, its own server loop
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHostnameVerificationTest.java`:169 — the stale comment
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClientTest.java`:1 — 903 lines
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`:447 — `isBeanClassCandidate`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/BearerScopeValidationCombinedIndexBuildStepTest.java`:54 — the narrow display name and the `@Vetoed` tests
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/BearerScopeValidationGeneratedBeanBootTest.java`:139 — the second top-level class
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeys.java`:461 — the literal minimum
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeysDerivationGuardTest.java`:157 — the positive control
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducerTest.java`:264 — a framed name; 607 lines
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java`:69 — the class Javadoc
- OBSERVED: `doc/commons/requirements.adoc`:190 — COMMONS-17 catch wording
- OBSERVED: `doc/commons/specification/transport.adoc`:247 — the memory-bound and catch statement
- OBSERVED: `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc`:196 — "Before, ..."; `:335` the catch statement
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:114 — the compatibility framing
- OBSERVED: `doc/client/requirements.adoc`:272 — the logout-endpoint sentence
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:192 — the rows to extend
- OBSERVED: `AGENTS.md`:159 — the INFO range
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/scripts/start-dev-environment.sh`:76 — the dev-mode launch
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/tests/self-devui-accessible.spec.js`:48 — the Token Debugger navigation without the render wait
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js`:57 — the Token Debugger test
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/utils/devui-navigation.js`:118 — `goToTokenDebugger` and the repeated test id
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/utils/constants.js`:1 — the declared test ids
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/RevocationClient.java`:1 — a call site of the exchange whose request timeout is read for the `pending.get()` bound (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java`:1 — a call site of the exchange (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/UserInfoClient.java`:1 — a call site of the exchange (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`:1 — a call site of the exchange (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff's PLAN-34 adopts code, text and tests that match.
- Depends on: PLAN-12 (shipped, #925, `ae493e00`); the plan edits code, tests and documents it created. It depends on no unshipped plan: PLAN-01 to PLAN-12 are shipped and nothing else is running.
- Inside the plan: deliverable 1 and 2 before 3 (the split and rename tests of 3 include their new tests), deliverables 1 to 3 before 4 (the documents state what they settled, and the mapping rows name the classes they add), deliverable 6 last (it shows the final head). Deliverable 5 is independent.
- Overlaps with: none running.
- Adjacent to: the marketplace-side findings of the same plan, which are NOT part of this spec (listed in the header). Also not part of it: API Sheriff's deletions of its own copies (its PLAN-34), the narrower RFC 6750 `error_description` grammar (OD-3 of ADR-0031, kept as the quoted string).
- Requirement, ADR and log ids are next free at write time (ADR numbering continues from `0031`; the next free is `0032`).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-13-final-finalize.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
