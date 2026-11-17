# PLAN-11: Final residuals sweep

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-11-final-residuals-sweep.md` and is queued as one row file, `queue/PLAN-11.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the operator's word of 2026-11-17, after the PLAN-09 landing: "create a final follow-up plan with the residuals and open issues". It gathers what the
> two landings of 2026-11-17 left with nothing staged: PLAN-09's (`landings/PLAN-09.md`), PLAN-03's (`landings/PLAN-03.md`), the review-body findings of #915 and #916, and the
> repository-side open issues recorded in `epic.md` (Open Defects, Watches). PLAN-10 (running) already holds the client and discovery-cache residuals and the review-body
> findings of #898, #899, #901, #906, #907, #908 and #912; nothing here repeats it. None of it is a blocker or major defect; one item (deliverable 1) is a stated bound that got weaker
> than the spec asked and needs the operator's decision. It is staged because it ships in 0.9.10 text and code that API Sheriff's PLAN-34 adopts.
> Scope: six deliverables, at the guard's limit. The operator asked for ONE final plan, so it is not split here; the outline splits it if it scopes above about six tasks (see the verify-first clause).

## Objective

The last two plans of this epic to land left a short list of known gaps: five items the PLAN-09 spec asked for that the merge did not deliver, tests that cannot fail for the property they name,
a memory bound on a remote-response read that the merge weakened from "ceiling plus one chunk" to "below twice the ceiling plus the chunk" without the spec's agreement on record, allow-list validator behaviour
(zoned IPv6, trimming, a spelling difference) whose documentation or tests say less than the code does, two test classes over the repository's size rule, thirteen review-bot body findings with no recorded
disposition, and three repository-side loose ends (a Playwright flake, the test-to-implementation mapping, an architecture hint). This plan closes them in one pass, so that the documents, decision records and tests that
ship in 0.9.10 say what the code does and can fail when it stops doing it. No public API is added beyond what a fix needs, and the default behaviour of every class named here does not change except where a decision below says so.

## Deliverables

1. **The body handler's memory bound is a recorded decision, and a test holds it (PLAN-09 L2-7, L1-2).** The PLAN-09 spec kept `COMMONS-17`'s bound (ceiling plus one in-flight chunk while streaming) and said a single growable buffer
   broke it and was reverted in #908; the merge keeps one array that doubles and rewrote `COMMONS-17`, `transport.adoc` and ADR-0031 to "below twice the ceiling plus the chunk in flight". A review-thread reply under the operator's account records
   an "Operator decision" for it; that is the lane's record, not an independent one. Settle it at outline with the operator, then make the code, `COMMONS-17`, `transport.adoc` and ADR-0031 say the same thing.
   Either the doubling array stays and ADR-0031 records the decision and its cost in its own words (the worst-case streaming peak doubles on a read of a remote response; the spec conflict is named), or the bound is restored by
   a coalescing step or a chunk list with a ceiling of its own. Verification: a test that fails when the stated factor is exceeded (the array capacity after any sequence of buffers is at most the stated multiple of the bytes received and never above the ceiling),
   with a control for the ceiling and for both `OverLimitPolicy` values; the byte ceiling and the policy behaviour hold unchanged.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality, pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: implementer
2. **PLAN-09's undelivered spec items and its wording errors are fixed (PLAN-09 L2-1 to L2-3, L3-2, the lane's advisories).** Each location is re-read against the code at HEAD and corrected where it is still wrong.
   - ADR-0031 "Changed default behaviour" itemises the 100-year `LogoutTokenValidator.MAX_FRESHNESS_WINDOW`, the unchanged-JWKS discard moving from WARN to DEBUG, the health-check WARN without a stack trace and with a sanitised issuer, and the client body handler's switch from a `ByteArrayOutputStream` to the body handler's array (or points at the ADR that holds each).
   - `BoundedBodyHandler.MAX_CEILING` gets the `@since` the class uses; the builder Javadoc of `HttpJwksLoaderConfig.unknownKidRefreshWindow` states that a failed hand-off burns the window (the rest of the claim path's text already does).
   - ADR-0030 and `openid-discovery.adoc` call the guard-holding miss "the first miss" although it is the one that loses the window; ADR-0031's Consequences state the body-handler peak without the chunk in flight and limit the request-time 500 to sets that pass deployment, omitting a set supplied programmatically through the public `BearerTokenProducer.getBearerTokenResult`; `VALIDATION-14.2` over-states when the 500 applies; the Javadoc of `BearerTokenResult`, `BearerAuth` and `BearerToken` carries the literals `64` and `8192` instead of the `BearerChallenge` constants; `IssuerConfigResolver`'s `@throws` lists only `IllegalStateException` although the retry combination ends in `IllegalArgumentException`.
   - `HttpJwksLoader`'s Javadoc keeps an unqualified statement that a miss never makes a periodic cycle skip its fetch (the lane's advisory; not re-read): re-read it and qualify it as ADR-0030 does.
   - An `Error` thrown by the executor's `execute` leaves the in-flight guard held (`HttpJwksLoader.scheduleUnknownKidRefresh`, the hand-off `catch` is for `RuntimeException` only): settled at outline as accepted and stated in the Javadoc, or released in a `finally`; either way with a test.
   - Verification: a re-read of each location; the final text matches the shipped behaviour; no statement is added that only a run could settle.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation, pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: documenter
3. **PLAN-09's tests can fail for the property they name (PLAN-09 L2-4 to L2-6 and the parameterized-test deviation).**
   - A scope containing a non-ASCII character is refused at deployment, answered with the defined 500 on the request path, and refused by the boot test; each with a valid scope as its positive control.
   - A test delivers several buffers in one `onNext` call (a mid-list ceiling crossing included) and checks the assembled body; `BoundedBodyHandlerTestSupport.ManualDrive.feed` today always calls `onNext(List.of(buffer))`.
   - The retention bound of `BearerChallenge.Builder.scopes` (`scopes.size() <= MAX_SCOPE_TOKENS`) has a test that fails when the guard is removed; today a 100,000-scope builder still refuses with the same constant message, so `shouldRefuseFarTooManyScopesLikeOnePastTheBound` stays green. Assert on something the retention changes (the builder's held count through a seam, or the work done), or record why it cannot be observed and say so in ADR-0031 as it already states the work is not bounded.
   - The three near-identical refusal tests of `BearerScopeValidationBuildStepTest` (method, class, injection point) and the three request-path tests of `BearerTokenResponseFactoryUnrepresentableScopesTest` become parameterized tests (three or more similar variants); a control-character scope-name case, the `Q-201` 256-character truncation and the interface-default-method branch each get a test (the last needs a fixture; if the build step cannot reach it, the test pins the request-path backstop for it).
   - Verification: each new test has a negative control and is shown to fail without the change it pins (a recorded red proof); no test class grows past 500 lines.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: module-tester
4. **PLAN-03's residuals are closed (PLAN-03 L1-2, L2-2, L2-4 to L2-6, L3-1, L3-4, L3-2 and L3-3).** `ClientConfigurationTest` is PLAN-10's (deliverable 4 (5), the split); it is not touched here.
   - The wider refusal is stated where a user reads it: `openid-discovery.adoc` and the requirements say which allow-list entries now fail startup or the mapping (a port, a scheme or any URL syntax, an underscore, a non-ASCII name, `idp.123`, a malformed IPv6 literal, a name with `%`, `null`), and that blank and whitespace-padded elements are dropped on the issuer path while the client path does not trim (`keycloak, dex` fails the mapping). Settled at outline with the operator: keep the client path untrimmed and document it, or trim the elements (a behaviour change, with a test).
   - A zoned IPv6 entry `[fe80::1%eth0]` is now refused by the `%` rule and may have been a working entry. Settle it with a test that RUNS: does `java.net.URI` return such a host unchanged from `getHost()`? If it does, admit it (and test the match end to end with a real URL); if not, say in the requirements and the validator Javadoc that it was never matchable.
   - `ClientConfiguration` keeps the configured spelling while the other three builders store the canonical form (`getAllowedEgressHosts()`, `equals` and `toString` differ): settle at outline whether that is the contract and state it in the Javadoc and `CLIENT-30`, or make the four builders agree.
   - The `CLIENT-30` row of `test-strategy.adoc` names the egress validator, the three builder tests, `IssuerConfigResolverEgressHostTest`, `BackChannelEgressPolicyTest` and the `TokenSheriffClientProducerTest` mapper case; the `CLIENT-30` traceability row sits in order.
   - The three allow-list builders (`EgressPolicy`, `WellKnownConfig`, `HttpJwksLoaderConfig`) test the same refusal classes as the validator unit (whitespace, control, non-ASCII, invalid host), not only null, blank, port and `[::1]:port`; as parameterized tests.
   - `HttpJwksLoaderConfigTest` (537 lines) and `TokenSheriffClientProducerTest` (523) are split at or under 500 lines, as pure moves.
   - `PemKeyPairLoaderAdversarialTest`'s off-curve EC case is made independent of whether the JDK's `KeyFactory` validates the point at decode time (build the key the way `JwkPublicMembersTest` does, or assert on either outcome the policy allows); the loader's symlink and special-file handling (`Files.isRegularFile` follows symlinks; a FIFO swapped in after the check blocks at the open) and the unwipeable key text are settled at outline as accepted and stated in the Javadoc, or changed with a test.
   - Verification: a test or a re-read per item; every changed refusal text is a fixed text that never echoes the entry or a key.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality, pm-documents:ref-asciidoc, pm-documents:ref-documentation, plan-marshall:persona-plan-marshall-agent
   - Persona: implementer
5. **Every review-bot body finding of #915 and #916 has a recorded disposition.** `review-gate` returns `body_findings_undisposed` for #915 (5: `nitpick-1`, `nitpick-2`, `inline-1` to `inline-3`) and #916 (8: `nitpick-1`, `inline-1` to `inline-7`). For each, the plan records one of:
   fixed at HEAD (with the commit or the line), fixed in this plan, or declined with a reason, in the PR thread or a PR comment and in the run's progress record. A finding whose defect has since moved, or that deliverables 1 to 4 close, is recorded as such and not fixed twice.
   The lane's own thread replies are the starting point (the #916 replies name `f0fdf6b` for three of them); the dispositions live there. Verification: `runbook_lane review-gate --pr-number N` for both PRs, and a table of finding, disposition and evidence.
   - Skills: pm-documents:ref-documentation, plan-marshall:ref-code-quality
   - Persona: documenter
6. **Three repository-side loose ends are closed.**
   - The Playwright flake `accessibility-wcag-compliance.spec.js:60` (the Token Debugger page accessibility step; two failures on #907, both passing on rerun, recorded as lesson 2026-11-15-18-003): reproduce or characterise it, and fix the root cause (the lane's hypothesis: the page is scanned or screenshotted before the debugger component has rendered; not established), or record why it cannot be fixed and what identifies it as a known flake. Verification: the spec passes on the number of consecutive runs the outline names, or the record states why not.
   - The `module_mapping_validator` flagged deliberate pairings in four of five PLAN-09 deliverables (a Javadoc-only target profiled as module testing, a cross-module pin, a test-only fix, `documentation.module_testing`), and each needed an operator disposition. `doc/developer/test-impl-mapping.adoc` names the client round trip only in prose in its `commons.http` row. Settle at outline which row shape the validator reads and add the rows (client round trip to `commons.http`, deployment-module tests to `TokenSheriffProcessor`), or record that the validator's input is not this document.
   - One architecture hint is written for the log parser's frontend-output noise (npm peer-dependency and engine warnings, the Jest summary, timing and snapshot lines and WSL path diagnostics are read as build errors although the build did not fail): `architecture enrich best-practice` with the hint "judge a build by its exit status and its failing-test list, not by the parser's finding count". The lane named the module `maven`; `.plan/project-architecture/` has no `maven` directory, so the target module is settled at outline.
   - Skills: pm-dev-frontend:javascript, plan-marshall:ref-code-quality, pm-documents:ref-asciidoc
   - Persona: implementer

Done when: all criteria pass, the existing tests named in the Standing Constraints pass without edited assertions except where a deliverable names the decision, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar
open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **The body handler's memory bound** (deliverable 1): keep the doubling array and record the decision and its cost in ADR-0031, or restore the bound. A stated bound on a remote-response read; a 0.9.10 contract.
- **Client-path trimming of allow-list entries** (deliverable 4): document that entries are not trimmed, or trim them.
- **Zoned IPv6 allow-list entries** (deliverable 4): admit them if `java.net.URI` returns them, or document the refusal.
- **`ClientConfiguration`'s configured spelling** (deliverable 4): keep it as the contract and say so, or make the four builders agree.
- **`Error` from the executor's `execute`** (deliverable 2): accept and document, or release the guard in a `finally`.
- **Body findings the lane declined** (deliverable 5): whether a declined finding stands.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no history-framed wording in anything this plan writes.
- **Default behaviour stays unchanged and is proven so**, except where a decision above states a change and a test proves it: `BearerChallenge` output for a valid input (`BearerChallengeTest` byte-identical expectations), `BoundedBodyHandler`'s ceiling and `OverLimitPolicy` behaviour, the unknown-`kid` refresh's default (off) behaviour, the allow-list builders' acceptance of a bare host, and the existing tests of PLAN-03 and PLAN-09 (`EgressHostValidatorTest`, `PemKeyPairLoaderTest`, `BearerScopeValidationBuildStepTest`, `BearerTokenResponseFactoryUnrepresentableScopesTest`, `HttpJwksLoaderExecutorHandOffTest`).
- **Say only what was read.** A corrected statement is checked against the code at HEAD before it is written; a statement that only a run could settle is run or left out. Every line number in this spec is re-read at HEAD at outline.
- **Memory bound.** Whatever deliverable 1 settles, `COMMONS-17`, `transport.adoc`, ADR-0031 and the code state one bound in one form.
- **Security.** Attacker-controlled text goes through `ErrorTextSanitizer` (validation) or `LogSanitizer` (client); no token or key material is logged; the CWE-117 rule of ADR-0022 holds for every new or changed log line; refusal texts stay fixed and never echo an entry, a path or a key. A new LogRecord takes the next free identifier in its range at write time and is added to `doc/LogMessages.adoc`.
- **Native image.** The helpers stay plain classes; no reflect-config entry and no new dependency without operator approval.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never sleeps), a negative control per regression test, new or grown test classes at or under 500 lines and a split a pure move.
- Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.
- **Review-fix discipline** (lessons `2026-11-16-06-001` and `2026-11-16-06-002`, the first not in the lessons store): before applying a review-bot suggestion that changes a bounded structure, read the requirement row that states the bound; after a refactor onto a helper, list the guards the old call site applied and test the input each one existed for. New lessons `2026-11-17-16-001` and `2026-11-17-16-002` apply to deliverables 1 and 3: bound the input before any allocation it sizes, and propagate a worker-thread assertion to the test thread.

## Claim Labels

- OBSERVED: the merge keeps one array that doubles (`BoundedBodyHandler.append`, `:362-374`) and `COMMONS-17` now reads "peaks below twice the ceiling plus the one chunk in flight" (`doc/commons/requirements.adoc:152-165`); the PLAN-09 spec's standing constraint said the bound stays true — read at `67f8e480` (`landings/PLAN-09.md` L2-7)
- OBSERVED: ADR-0031's "Changed default behaviour" contains none of `MAX_FRESHNESS`, `100 year`, `stack trace`, `chunk list` or `DEBUG` — read at `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc` (`:155-187`, a search)
- OBSERVED: `MAX_CEILING` (`BoundedBodyHandler.java:94-97`) has no `@since`; `@since 1.0` is at `:90`, `:110` and `:204` — read at `67f8e480`
- OBSERVED: the Javadoc of `HttpJwksLoaderConfig.unknownKidRefreshWindow` ends "A rejected hand-off releases the guard and never throws" and does not state the burned window — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` (`:933-957`)
- OBSERVED: the hand-off `catch` of `scheduleUnknownKidRefresh` is for `RuntimeException` only (`HttpJwksLoader.java:903-906`), so an `Error` from `execute` leaves `fetchInFlight` set — read at `67f8e480`
- OBSERVED: `ManualDrive.feed` calls `subscriber.onNext(List.of(buffer))` (`BoundedBodyHandlerTestSupport.java:163-167`); `shouldRefuseFarTooManyScopesLikeOnePastTheBound` (`BearerChallengeTest.java:290-304`) asserts the same message for 65 and 100,000 scopes, and the retention guard is at `BearerChallenge.java:165` — read at `67f8e480`; that the test stays green without the guard is a reading, not a mutation run
- OBSERVED: no non-ASCII scope literal appears in `BearerScopeValidationBuildStepTest`, `BearerTokenResponseFactoryUnrepresentableScopesTest` or `BearerScopeValidationRefusalBootTest` — a search at `67f8e480`
- OBSERVED: `BearerScopeValidationBuildStepTest.java:89-121` holds three near-identical refusal tests; `HttpJwksLoaderConfigTest` is 537 lines and `TokenSheriffClientProducerTest` 523 — `wc -l` at `a428e615`
- OBSERVED: `EgressHostValidator` refuses `%` (`EgressHostValidator.java:92`, `:158-160`), so a bracketed zoned literal is refused; `ClientConfigMapper:114-116` passes elements on untrimmed; `ClientConfiguration:452-464` keeps the configured spelling and `ClientConfigurationTest:908-914` pins it; `IssuerConfigResolver:555-563` turns every builder refusal into a startup failure and `ConfigValueParser.splitCsv:47-52` drops blank elements — read at `dfab40cb` (`landings/PLAN-03.md`)
- OBSERVED: the `CLIENT-30` row of `test-strategy.adoc` (`:603-621`) omits the egress validator, builder and Quarkus tests; the per-site builder tests cover null, blank, `host:port` and `[::1]:port` (and a URL-shaped entry in `EgressPolicy` only) — read at `dfab40cb`; the second part is the PLAN-03 leaf's reading
- OBSERVED: `PemKeyPairLoader.java:180-184` follows symlinks through `Files.isRegularFile` and reads through `readNBytes(MAX_FILE_BYTES + 1)`; the file text is held in a `String` (`:198`) — read at `dfab40cb`
- OBSERVED: `accessibility-wcag-compliance.spec.js` holds the "Token Debugger page accessibility" test, which calls `goToTokenDebugger(page)` and `accessibilityHelper.analyze()` — read at `token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js` (`:57-69`); lesson 2026-11-15-18-003 records two failures at `:60` on #907
- OBSERVED: `.plan/project-architecture/` has no directory named `maven` (the modules listed are the repository's artifacts, such as `token-sheriff-parent` and `e-2-e-playwright-maven`) — a listing at `a428e615`
- HYPOTHESIS: a bracketed zoned IPv6 literal such as `[fe80::1%eth0]` was returned unchanged by `URI.getHost()` and could have matched an allow-list entry — JDK behaviour, not shown by reading; confirm or refute by a test that runs (verify-at-outline)
- HYPOTHESIS: the `Q-201` log truncates the sanitised text at 256 characters and no test pins it; `BearerTokenResult`, `BearerAuth` and `BearerToken` carry the literals `64` and `8192`; `VALIDATION-14.2` over-states when the 500 applies; `IssuerConfigResolver`'s `@throws` omits `IllegalArgumentException` — the PLAN-09 leaf's and the lane's reading, not re-read (verify-at-outline)
- HYPOTHESIS: `HttpJwksLoader`'s Javadoc still carries an unqualified "never makes a periodic cycle skip" sentence outside the method Javadoc at `:848-866` (which is qualified) — the lane's advisory, not re-read; locate it first (verify-at-outline)
- HYPOTHESIS: the `module_mapping_validator` reads rows of `doc/developer/test-impl-mapping.adoc` — the lane's remedy in each message; the row shape and whether the validator reads that file at all were not checked (verify-at-outline)
- HYPOTHESIS: the flake's cause is a scan or screenshot taken before the Token Debugger component has rendered — the lane's hypothesis on #907, never reproduced (verify-at-outline)
- HYPOTHESIS: the review-body findings of #915 and #916 are mostly already answered in their PR threads (the lane's replies), and `handled` reads 0 only because no finalize step records it — `review-gate` returns `handled: 0` on both; not checked per finding
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (PLAN-10 is running and edits `ClientConfiguration`, `ClientConfigurationTest`, `ClientConfigMapper`, `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc` and `doc/LogMessages.adoc`; read what has merged before touching them), (3) which deliverable 4 items PLAN-10 already closed, and (4) that the deliverables stay at about six tasks or fewer; if the outline scopes above that, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java`:362 — the doubling array; `:94` `MAX_CEILING`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/BearerChallenge.java`:165 — the retention bound
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:870 — `scheduleUnknownKidRefresh`, the hand-off `catch` at `:903`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:933 — `unknownKidRefreshWindow` Javadoc
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressHostValidator.java`:92 — the `%` rule
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`:452 — `validateAllowedEgressHosts`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/auth/PemKeyPairLoader.java`:180 — the regular-file check
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`:114 — the untrimmed read
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`:555 — `configureAllowedEgressHosts`, the `@throws` list
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResult.java`:258 — Javadoc literals
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerAuth.java`:157 — Javadoc literals, the accepted limits
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerToken.java`:1 — Javadoc literals
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java`:111 — the backstop, `Q-201` at `:126`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTest.java`:352 — the one-byte buffers
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTestSupport.java`:163 — `ManualDrive.feed`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/http/BearerChallengeTest.java`:290 — the retention test
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderExecutorHandOffTest.java`:187 — the hand-off tests
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/BearerScopeValidationBuildStepTest.java`:89 — the three refusal tests
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactoryUnrepresentableScopesTest.java`:1 — the request-path tests
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/EgressPolicyTest.java`:246 — per-site refusal coverage
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigTest.java`:126 — per-site refusal coverage
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigTest.java`:172 — per-site refusal coverage; 537 lines
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducerTest.java`:392 — the mapper case; 523 lines
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/auth/PemKeyPairLoaderAdversarialTest.java`:1 — the off-curve case
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/dpop/JwkPublicMembersTest.java`:160 — the `FixedEcPublicKey`
- OBSERVED: `doc/adr/0030-An_opt-in_unknown-kid_refresh_claims_one_window_per_loader_and_installs_only_a_key_set_holding_the_kid.adoc`:159 — Risks
- OBSERVED: `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc`:155 — "Changed default behaviour", Consequences
- OBSERVED: `doc/commons/requirements.adoc`:152 — `COMMONS-17`
- OBSERVED: `doc/commons/specification/transport.adoc`:247 — the memory-bound statement
- OBSERVED: `doc/validation/requirements.adoc`:667 — `VALIDATION-14.2`
- OBSERVED: `token-sheriff-validation/doc/configuration/openid-discovery.adoc`:241 — "the first miss"; the allow-list refusal text
- OBSERVED: `doc/client/requirements.adoc`:1 — `CLIENT-30` and its traceability row
- OBSERVED: `doc/client/specification/test-strategy.adoc`:603 — the `CLIENT-30` row
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:33 — the `commons.http` row
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js`:57 — the Token Debugger test
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/fixtures/test-fixtures.js`:1 — `accessibilityHelper`
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/` — the split of `TokenSheriffClientProducerTest` (new classes at or under 500 lines; the `HttpJwksLoaderConfigTest` split is in its own entry above) (verify-at-outline)
- HYPOTHESIS: the architecture hint of deliverable 6 writes one enriched data file of one module in the project-architecture data directory; which module's is settled at outline, so no path is declared for it here (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff's PLAN-34 adopts text, code and tests that match.
- Depends on: PLAN-03 (shipped, #915, `dfab40cb`) and PLAN-09 (shipped, #916, `67f8e480`); the plan edits code, tests and documents those two landings created.
- Overlaps with PLAN-10 (running): `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc` and, by deliverable 4's client items, `ClientConfiguration`, `ClientConfigMapper` and the client test tree. PLAN-10 splits `ClientConfigurationTest`; this plan does not touch it. The `next` admission gate decides from the declared surface; deliverables 1 to 3 and the validation-module part of 4 are disjoint from PLAN-10, and the client items of 4 are what the overlap is.
  Read what PLAN-10 has merged before touching a client file, and rebase over it.
- Adjacent to: the marketplace-side findings of the same two plans, which are NOT part of this spec because they are not this repository's files: the `no_sanction` commit refusal (five plans), the `weakened_test` counter attributing a helper body to the previous method and the code-review validator ignoring an accepted artefact, the scope-creep guard re-filing at each loop-back, the `review_completeness` flag families, and lesson 2026-11-17-06-001's checker pattern. Also not part of it: the lessons missing from the store, API Sheriff's deletions of its own copies (its PLAN-34), and the narrower RFC 6750 `error_description` grammar (OD-3 of ADR-0031, kept as the quoted string).
- Requirement, ADR and log ids are next free at write time (ADR numbering continues from `0031`; the next free is `0032`).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-11-final-residuals-sweep.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
