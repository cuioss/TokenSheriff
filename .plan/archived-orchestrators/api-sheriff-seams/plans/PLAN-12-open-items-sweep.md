# PLAN-12: Open items sweep

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-12-open-items-sweep.md` and is queued as one row file, `queue/PLAN-12.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the operator's word of 2026-11-18 ("handle-open", answered "Stage PLAN-12, all repo items") after the PLAN-10 and PLAN-11 landings. It gathers what those two landings left with nothing staged:
> the Open Defects entries "PLAN-10 landed" and "PLAN-11 landed" in `epic.md` (`landings/PLAN-10.md`, `landings/PLAN-11.md`), the review-body findings of #920 and #921 the lane did not change, and the repository-side loose ends. One item (deliverable 1) is a fail-open path by reading
> in a startup guard; the rest are minor. It is staged because it ships in 0.9.10 text and code that API Sheriff's PLAN-34 adopts.
> Scope: six deliverables, at the guard's limit. The operator asked for ONE plan with every repository-side item, so it is not split here; the outline splits it if it scopes above about six tasks (see the verify-first clause).
> NOT in this spec (not this repository's files, or not a plan's work): the marketplace-side findings (the `no_sanction` commit refusal, now six plans; the `weakened_test` carry-forward ask, five plans; the code-review validator; the scope-creep guard; the `review_completeness` flag families; the docs-versus-executor version skew, lesson 2026-11-18-12-002),
> and the lessons missing from the lessons store.

## Objective

The last two plans of this epic to land (#920 and #921) left a short list of known gaps: a startup guard whose list is now derived by reflection with no check that the derivation found anything, wording and identifiers that disagree with each other, tests that cannot fail for the property they name or that no test pins, a number a stated bound leaves out, review-bot body findings the lane did not change,
test classes with no row in the test-to-implementation mapping, and an end-to-end environment that cannot test the branch it runs on. This plan closes them in one pass, so that the code, documents and tests that ship in 0.9.10 say what they do and can fail when they stop doing it. No public API is added beyond what a fix needs, and the default behaviour of every class named here does not change
except where a decision below says so.

## Deliverables

1. **The orphaned-DPoP property guard cannot degrade to checking nothing (PLAN-10 L3-1 to L3-3).** `JwtPropertyKeys.ISSUERS.DPOP_SUB_PROPERTIES` is derived by reflection in `ISSUERS.<clinit>` (`declaredKeysUnder`); `IssuerConfigResolver.validateNoOrphanedDpopProperties` iterates it for the fail-closed check of ADR-0026, so an empty result would let `dpop.required=true` without `dpop.enabled` pass silently.
   Make the derivation fail closed: an empty or short result is refused at class initialisation (or at startup) with a fixed message, or the explicit list returns; a `public static final String` that is not a compile-time constant and is declared after the list reads `null` today and fails `ISSUERS` initialisation with a `NullPointerException`: make that a named failure, with a test for each; state in the Javadoc and a test which property a startup failure names when several are orphaned
   (the order changed from declaration order to natural sort). Whether the list is non-empty in a native image is not shown by reading: settle at outline how it is shown (a native build of `token-sheriff-quarkus-integration-tests` that asserts the list, or the fail-closed guard alone, recorded as the reason).
   Verification: a test per failure mode with a positive control, shown to fail without the guard (a recorded red proof); the existing `JwtPropertyKeysTest` and `TokenSheriffProcessorBuildStepTest` pass; the orphaned-DPoP behaviour for a valid configuration is unchanged.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-quarkus, plan-marshall:ref-code-quality
   - Persona: implementer
2. **PLAN-10's code, test and wording leftovers are closed (PLAN-10 L1-6, L2-1 to L2-6, L3-4, L3-6 to L3-8).** Each location is re-read against the code at HEAD and corrected where it is still wrong.
   - `token-sheriff-validation/doc/configuration/openid-discovery.adoc` says `TokenSheriff-010` while `doc/LogMessages.adoc` reads `TokenSheriff-10`; the Javadoc of `TokenSheriffQuarkusLogMessages` says `001-025`; a test pins the unpadded identifier form of the validation and Quarkus INFO rows the way `ClientLogMessagesIdentifierTest` does for the client rows, or the plan records why none can.
   - The new `IllegalStateException` branch in `AuthorizationCodeFlow` (the authorization-endpoint guard after `parameters(...)`) has a test, and the exception type it introduces for a subclass of `parameters(...)` is stated in the Javadoc; the three-argument `AuthorizationRequestBuilder.build` now recurses for a subclass whose four-argument override calls it for defaults: name the hazard in the Javadoc, or guard it, settled at outline with the operator (a public-class contract of 0.9.10).
   - The interrupt-edge text of `CachingDiscoveryResolver` and `retrieval-flow.adoc` says the upstream load is bounded to one request per refresh while a stale, non-backoff cache reaches the delegate on every call of an interrupted thread: state it or change it, with a test; the interrupt test clears the flag in a `finally`.
   - The list of shared back-channel refusals that log "DEBUG at most" in `doc/LogMessages.adoc` omits `BackChannelHttp`'s malformed-response mapping; `WireValues` still echoes the sanitised value into its exception and `AuthorizationRequestOptionsTest` pins the echo: settle at outline whether that is the contract (state it) or a fixed text (change it, with a test).
   - Three test method names keep the framing the pre-1.0 rule forbids (`shouldKeepDefaultFlowUrlByteIdentical`, `shouldKeepDefaultUrlByteIdentical`, `shouldKeepStepUpUrlByteIdentical`, `shouldKeepTwoArgumentFormUnchanged`); `RefreshFlowScopeOverrideTest` asserts only the exception type for the shared invalid-override variants.
   - The `ClientConfigurationTest` split into three classes added three tests and changed assertions in four methods (52 methods became 55), against the pure-move rule: re-read what changed and record it as accepted in the PR body, since the split cannot be undone.
   - Operator decisions PLAN-10's diff does not show: a dedicated reason for a missing PAR endpoint (the generic `ClientProtocolException` stays today), and the two PLAN-04 nitpicks (`DiscoverySpecIT` and `CachingDiscoveryResolverAdversarialTest`, untouched, recorded as not fixed): settle each at outline, then fix or record.
   - Verification: a re-read of each location, a test that can fail for each behaviour item with a negative control; no test class grows past 500 lines.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality, pm-documents:ref-asciidoc, pm-documents:ref-documentation, pm-dev-java-cui:cui-logging
   - Persona: implementer
3. **PLAN-11's leftovers are closed (PLAN-11 L1-2, L2-4, L3-1 to L3-3 and the five unchanged review items of #921).**
   - A test of its own for the malformed `Content-Length` at the `ParClient`, `UserInfoClient` and `DiscoveryResolver` call sites of `BackChannelHttp.send`, each with the positive control of a valid response, so reverting one call site fails a test.
   - The `catch (IllegalArgumentException)` in `BackChannelHttp.send` and `TokenEndpointClient` is as wide as `HttpClient.send`'s own exception, so a request-side fault reads as "malformed response": narrow it, or state the limit in the Javadoc and ADR-0031, settled at outline.
   - `COMMONS-17`, `doc/commons/specification/transport.adoc` and ADR-0031 give the first-allocation term of the one memory bound ("or the first allocation when that is larger") without its value (1024, or the ceiling when smaller); ADR-0031 names the PLAN-09 specification and a replaced decision (history framing, against the pre-1.0 rule) and its "Changed default behaviour" list omits the client's `TransportException` for a malformed `Content-Length`.
   - The five review items of #921 the lane left unchanged are re-decided, each fixed or declined with a reason in the PR thread and the progress record: (1) a `204` with a malformed `Content-Length` at the token endpoint; (2) ArC-generated beans in the deployment scope check (a processor input change plus a boot test); (3) a mutable scope set cleared after `getBearerTokenResult`; (4) deriving the refusal-class test cases from the validator (needs a production enum); (5) the 65-required-scopes test case.
   - Verification: each new test shown to fail without the change it pins (a recorded red proof); the byte ceiling, the `OverLimitPolicy` behaviour and every default behaviour hold unchanged.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-quarkus, plan-marshall:ref-code-quality, pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: implementer
4. **Every test class this plan and its two predecessors leave unmapped has a row in `doc/developer/test-impl-mapping.adoc`.** `CachingDiscoveryResolver*Test`, `RedeemedIdentityRefusalExceptionTest`, `RefreshResultVerifierAdversarialTest`, `RefreshResultVerifierTest` and `TokenEndpointClientScopeNonceTest` appear nowhere in it; every test class deliverables 1 to 3 add or split is mapped in the same plan. Settle at outline which row shape `module_mapping_validator` reads and whether it reads this document at all (the lane's claim, never checked).
   Verification: a search of the document for each class named here and each class the plan adds, with zero misses.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
5. **The Playwright flake and the end-to-end environment are settled (lesson 2026-11-18-12-001).** `accessibility-wcag-compliance.spec.js:60` (the Token Debugger page accessibility step; two failures on #907, both passing on rerun, lesson 2026-11-15-18-003) was neither reproduced nor fixed in #921, because `start-dev-environment.sh` runs `mvnw quarkus:dev` against the shared `~/.m2` snapshot, not the branch.
   Settle at outline with the operator how the branch is tested: the start script builds the branch's modules into a repository private to the run, or refuses to start when the installed module versions do not match the branch; installing into the shared `~/.m2` is not done without the operator's word (it overwrites state other sessions use). Then reproduce or characterise the flake and fix its root cause (the lane's hypothesis: the page is scanned or screenshotted before the debugger component has rendered; not established),
   or record why it cannot be fixed and what identifies it as a known flake. Verification: the spec passes on ten consecutive runs against the branch build, or the record states why not.
   - Skills: pm-dev-frontend:javascript, plan-marshall:ref-code-quality
   - Persona: implementer
6. **The review-body findings still without a shown disposition have one.** PLAN-10's deliverable 5 left triage comments on #898, #899, #901, #906, #907, #908 and #912 but no per-finding table; the review gate still returns `body_findings_undisposed` for each, and for #920 itself (8 findings, `handled` 0). For each, the plan records one of: fixed at HEAD (with the commit or the line), fixed in this plan, or declined with a reason, in the PR thread or a PR comment and in the run's progress record; a finding since covered by deliverables 1 to 3 or by an earlier plan is recorded as such and not fixed twice.
   The lane's own thread replies are the starting point. Verification: `runbook_lane review-gate --pr-number N` for each PR, and a table of finding, disposition and evidence.
   - Skills: pm-documents:ref-documentation, plan-marshall:ref-code-quality
   - Persona: documenter

Done when: all criteria pass, the existing tests named in the Standing Constraints pass without edited assertions except where a deliverable names the decision, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar open-issue read at the PR head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **How the native image shows the DPoP list is non-empty** (deliverable 1): a native build that asserts it, or the fail-closed guard alone.
- **The recursion hazard of the three-argument `build`** (deliverable 2): name it in the Javadoc, or guard it; a public-class contract of 0.9.10.
- **`WireValues`' echo of the sanitised value** (deliverable 2): state it as the contract, or change it to a fixed text.
- **A dedicated reason for a missing PAR endpoint** (deliverable 2): keep the generic `ClientProtocolException`, or add a reason; a public-API change.
- **The two PLAN-04 nitpicks** (deliverable 2): fix, or record as accepted gaps.
- **The width of the `IllegalArgumentException` catch** (deliverable 3): narrow it, or state the limit.
- **The five unchanged review items of #921** (deliverable 3): which are fixed and which stand declined.
- **How the end-to-end environment tests the branch** (deliverable 5): a private repository or version check; never an install into the shared `~/.m2` without the operator.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no history-framed wording in anything this plan writes, **including test method names and ADR text**.
- **Default behaviour stays unchanged and is proven so**, except where a decision above states a change and a test proves it: `BearerChallenge` output for a valid input, `BoundedBodyHandler`'s ceiling and `OverLimitPolicy` behaviour, the unknown-`kid` refresh default (off), the allow-list builders' acceptance of a bare host and their canonical stored form, the orphaned-DPoP check for a valid configuration, and the existing tests of PLAN-03, PLAN-09, PLAN-10 and PLAN-11 (`JwtPropertyKeysTest`, `TokenSheriffProcessorBuildStepTest`, `BearerScopeValidationBuildStepTest`, `ClientConfigurationTransportTest#shouldAcceptBareAllowedHostsInCanonicalForm`, `BackChannelHttpMalformedResponseTest`).
- **Say only what was read.** A corrected statement is checked against the code at HEAD before it is written; a statement that only a run could settle is run or left out. Every line number in this spec is re-read at HEAD at outline.
- **Test-class splits are pure moves** (`AGENTS.md`): a new test goes in a new or an existing class by its own commit, never inside a move.
- **Security.** Attacker-controlled text goes through `ErrorTextSanitizer` (validation) or `LogSanitizer` (client); no token or key material is logged; the CWE-117 rule of ADR-0022 holds for every new or changed log line; refusal texts stay fixed and never echo an entry, a path or a key. A new LogRecord takes the next free identifier in its range at write time and is added to `doc/LogMessages.adoc`.
- **Native image.** The helpers stay plain classes; no reflect-config entry beyond what deliverable 1 settles and no new dependency without operator approval.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never sleeps), a negative control per regression test, new or grown test classes at or under 500 lines.
- Never gate on `test-compile`/`test`; use `verify`. **Never install into the shared `~/.m2` without the operator's word.**
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.
- **Review-fix discipline** (lessons 2026-11-17-16-001, 2026-11-17-16-002 and 2026-11-18-13-003): before applying a review-bot suggestion that changes a bounded structure, read the requirement row that states the bound; when an accepted fix contradicts a frozen deliverable criterion, ask in the same operator question whether to authorize the criterion change.

## Claim Labels

- OBSERVED: `DPOP_SUB_PROPERTIES` is `declaredKeysUnder(ISSUERS.class, DPOP_BASE, Set.of(DPOP_ENABLED))` (`JwtPropertyKeys.java:400`); `declaredKeysUnder` returns `List.copyOf(keys)` sorted in natural order with no check on the size (`:64-78`); `constantValue` returns `(String) field.get(null)` and the caller calls `value.startsWith(base)` (`:70-71`, `:80-86`); `IssuerConfigResolver.validateNoOrphanedDpopProperties` iterates the list (`:441-452`) — read at `dd68517b`
- OBSERVED: `openid-discovery.adoc:227` says `TokenSheriff-010`, `doc/LogMessages.adoc:50` and `:129` say `TokenSheriff-10` — read at `dd68517b`
- OBSERVED: `AuthorizationCodeFlow.java:245-249` throws `IllegalStateException("parameters(...) returned without verifying the authorization endpoint")`; `:236-238` keeps the generic `ClientProtocolException` for a missing PAR endpoint; the three-argument `build` returns `build(configuration, metadata, context, AuthorizationRequestOptions.DEFAULTS)` (`AuthorizationRequestBuilder.java:115-117`) — read at `dd68517b`
- OBSERVED: `WireValues.java:68` throws `new IllegalArgumentException("unsupported " + parameter + " value: " + LogSanitizer.sanitize(value))`; `RefreshFlowScopeOverrideTest.java:285` asserts `IllegalArgumentException.class` and zero calls only; `AuthorizationCodeFlowOptionsTest.java:160` is `shouldKeepDefaultFlowUrlByteIdentical`; the interrupt test sets the flag and clears it after `assertThrows` with no `try`/`finally` (`CachingDiscoveryResolverFailureHandlingTest.java:222-242`) — read at `dd68517b`
- OBSERVED: the three classes of the `ClientConfigurationTest` split hold 24, 20 and 11 `@Test`/`@ParameterizedTest` methods against 52 before — counted at `2b4b3f1f` and `dd68517b`
- OBSERVED: `BackChannelHttp.send` and `TokenEndpointClient` catch `IllegalArgumentException` around `HttpClient.send` (`BackChannelHttp.java:373-379`, `TokenEndpointClient.java:601-611`) — read at `d8aba512`
- OBSERVED: the first-allocation capacity is `INITIAL_CAPACITY = 1024` (`BoundedBodyHandler.java:304`) and `COMMONS-17` says "or the first allocation when that is larger" without a value (`doc/commons/requirements.adoc:177-178`); ADR-0031 names "the PLAN-09 specification" (`:320-321`) and its "Changed default behaviour" list (`:186-199`) does not itemise the client's `TransportException` (carried in Consequences, `:356-361`) — read at `d8aba512`
- OBSERVED: `CachingDiscoveryResolver*Test`, `RedeemedIdentityRefusalExceptionTest`, `RefreshResultVerifierAdversarialTest`, `RefreshResultVerifierTest` and `TokenEndpointClientScopeNonceTest` appear nowhere in `doc/developer/test-impl-mapping.adoc` — a search at `d8aba512`
  - verdict: corroborated | checked_at: ae493e00370ae6da2e715836aad19e80df3a9da1 | by: api-sheriff-seams/analyze | rescoped: n/a | evidence: Three-layer read of #925 (ae493e00): the diff adds rows for CachingDiscoveryResolver*Test, RedeemedIdentityRefusalExceptionTest, RefreshResultVerifierAdversarialTest, RefreshResultVerifierTest and TokenEndpointClientScopeNonceTest; the base had none. Orchestrator re-read: test-impl-mapping.adoc at the merge commit holds one row for each of them.
- OBSERVED: `start-dev-environment.sh:76` starts `"$PROJECT_ROOT/mvnw" quarkus:dev` with no `-am` and no install; the PLAN-11 run could not run the spec because the shared `~/.m2` snapshot failed every JWKS load (`work.log:491` of the archived plan `2026-11-18-plan-11-final-residuals-sweep`) — read at `d8aba512`
  - verdict: corroborated | checked_at: ae493e00370ae6da2e715836aad19e80df3a9da1 | by: api-sheriff-seams/analyze | rescoped: n/a | evidence: start-dev-environment.sh:76 at e53f3e4f is the mvnw quarkus:dev call with no install before it (re-read at the base).
- HYPOTHESIS: `DPOP_SUB_PROPERTIES` can be empty in a native image if the reflective registration (`@RegisterForReflection` on `JwtPropertyKeys`, the `ReflectiveClassBuildItem` in `TokenSheriffProcessor`) does not take effect — the leaf's reading and the PR body's admission that no native run demonstrated it; confirm or refute by a native build at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java` (verify-at-outline)
  - verdict: unverifiable | checked_at: ae493e00370ae6da2e715836aad19e80df3a9da1 | by: api-sheriff-seams/analyze | rescoped: n/a | evidence: Read by the leaf: the explicit ReflectiveClassBuildItem for ISSUERS with fields already existed at the base (TokenSheriffProcessor.java:188), so the list is empty only if that registration is lost; whether it is non-empty in a native executable is not shown by reading. The merged tree turns the empty case into an initialisation failure (JwtPropertyKeys.java:116-119). Not re-read at :188.
- HYPOTHESIS: `ParClient:116`, `UserInfoClient:189` and `DiscoveryResolver:177` call `BackChannelHttp.send` and have no malformed-`Content-Length` test of their own — the PLAN-10 and PLAN-11 leaves' reading (`BackChannelHttpMalformedResponseTest` covers `send` directly, `RevocationClient` and `TokenEndpointClient`); confirm by reverting one call site in a scratch tree (verify-at-outline)
  - verdict: corroborated | checked_at: ae493e00370ae6da2e715836aad19e80df3a9da1 | by: api-sheriff-seams/analyze | rescoped: n/a | evidence: At the base ParClientTest:316 and UserInfoClientTest:338 test Content-Type line breaks only (re-read); the merged tree adds ParClientMalformedResponseTest and UserInfoClientMalformedResponseTest (both exist, re-read in the tree) and a DiscoveryResolverTest case at :167 (re-read).
- HYPOTHESIS: `HttpClient.send` throws `IllegalArgumentException` for a request it considers invalid, so the catch width misattributes a request-side fault — the leaf's reading of the JDK contract, not read here; confirm in `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java` § `send` with a test that sends an invalid request (verify-at-outline)
  - verdict: corroborated | checked_at: ae493e00370ae6da2e715836aad19e80df3a9da1 | by: api-sheriff-seams/analyze | rescoped: n/a | evidence: At the base BackChannelHttp.send caught IOException | IllegalArgumentException around client.send (re-read, base :406); at HEAD exchange() calls sendAsync outside the try and maps only the future's failure (BackChannelHttp.java:473-488, re-read). That the JDK throws the request-side IAE synchronously from sendAsync rests on the HttpClient Javadoc as quoted in the code, not read.
- HYPOTHESIS: the "DEBUG at most" refusal list in `doc/LogMessages.adoc` (about `:238`) names four refusals and omits the malformed-response mapping — the PLAN-10 leaf's reading; re-read (verify-at-outline)
- HYPOTHESIS: an interrupted caller on a stale, non-backoff cache reaches `delegate.resolve()` on every call — the PLAN-10 leaf's reading of `CachingDiscoveryResolver.refresh`; confirm by a test (verify-at-outline)
- HYPOTHESIS: the `module_mapping_validator` reads rows of `doc/developer/test-impl-mapping.adoc` — the lane's remedy in several plans; the row shape and whether it reads that file at all were never checked (verify-at-outline)
- HYPOTHESIS: the flake's cause is a scan or screenshot taken before the Token Debugger component has rendered — the lane's hypothesis on #907, never reproduced (verify-at-outline)
- HYPOTHESIS: the five unchanged review items of #921 are as the lane's triage reply states them (a `204` at the token endpoint, ArC-generated beans in the deployment scope check, a mutable scope set cleared after `getBearerTokenResult`, refusal-class cases derived from the validator, the 65-required-scopes case) and each names code the plan can locate — the lane's account of CodeRabbit's review, not re-read; locate each at outline (verify-at-outline)
- HYPOTHESIS: the review-body findings of #898, #899, #901, #906, #907, #908 and #912 are mostly already answered in their PR threads (triage comments exist on all seven PRs) and `handled` reads 0 only because no finalize step records it — `review-gate` returns `handled: 0`; not checked per finding
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (no other plan is running; read what has merged since `dd68517b`), (3) that the deliverables stay at about six tasks or fewer; if the outline scopes above that, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeys.java`:400 — the derived list; `:64` `declaredKeysUnder`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`:441 — the orphaned-DPoP check
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeysTest.java`:1 — the derivation tests
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`:1 — the reflective registration
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessorBuildStepTest.java`:1 — its build-step test
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/TokenSheriffQuarkusLogMessages.java`:48 — the identifier range Javadoc
- OBSERVED: `token-sheriff-validation/doc/configuration/openid-discovery.adoc`:227 — `TokenSheriff-010`
- OBSERVED: `doc/LogMessages.adoc`:50 — the INFO rows; `:129` the cross-reference; about `:238` the refusal list
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java`:245 — the new guard
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`:115 — the delegating `build`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/WireValues.java`:68 — the echo
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java`:373 — the catch
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`:601 — the catch
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolver.java`:100 — the interrupt text
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlowOptionsTest.java`:160 — a framed method name
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilderParametersTest.java`:161 — a framed method name
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilderTest.java`:1 — the routing tests
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestOptionsTest.java`:221 — the pinned echo
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowScopeOverrideTest.java`:285 — the type-only assertion
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlowPushedTest.java`:221 — the PAR endpoint test
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverFailureHandlingTest.java`:222 — the interrupt test
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverAdversarialTest.java`:157 — a PLAN-04 nitpick
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/DiscoverySpecIT.java`:102 — a PLAN-04 nitpick
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpMalformedResponseTest.java`:1 — the `Content-Length` tests
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/ParClientTest.java`:1 — the call site's tests
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/UserInfoClientTest.java`:1 — the call site's tests
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolverTest.java`:1 — the call site's tests
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClientTest.java`:1 — the token endpoint tests
- OBSERVED: `doc/commons/requirements.adoc`:177 — `COMMONS-17`
- OBSERVED: `doc/commons/specification/transport.adoc`:247 — the memory-bound statement
- OBSERVED: `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc`:186 — "Changed default behaviour"; `:320` the history framing
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:300 — the interrupt text
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:192 — the `discovery` and `flow` rows
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/scripts/start-dev-environment.sh`:76 — the dev-mode launch
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js`:57 — the Token Debugger test
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/fixtures/test-fixtures.js`:1 — `accessibilityHelper`
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java`:1 — `getBearerTokenResult`, the mutable scope set (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducerTest.java`:1 — its tests (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/BearerScopeValidationBuildStepTest.java`:1 — the ArC-generated-beans case (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressHostValidator.java`:1 — a refusal-class enum, if item (4) of deliverable 3 is fixed (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/EgressHostValidatorTest.java`:1 — the derived refusal-class cases (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff's PLAN-34 adopts code, text and tests that match.
- Depends on: PLAN-10 (shipped, #920, `dd68517b`) and PLAN-11 (shipped, #921, `d8aba512`); the plan edits code, tests and documents those two landings created. It depends on no unshipped plan: PLAN-01 to PLAN-11 are shipped and nothing else is running.
- Overlaps with: none running.
- Adjacent to: the marketplace-side findings of the same plans, which are NOT part of this spec because they are not this repository's files (listed in the header). Also not part of it: API Sheriff's deletions of its own copies (its PLAN-34), the narrower RFC 6750 `error_description` grammar (OD-3 of ADR-0031, kept as the quoted string), and the lessons missing from the store.
- Requirement, ADR and log ids are next free at write time (ADR numbering continues from `0031`; the next free is `0032`).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-12-open-items-sweep.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
