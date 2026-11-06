# PLAN-25: Epic leftover sweep — native-image coverage, client DSL-JSON sites, dead code, residual tests

epic: quality-report
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Staged 2026-11-05 at the operator's instruction ("create a final followup plan for all the leftovers"). This is the
> epic's terminal plan: it carries every residual that no other plan owns. The orchestrator EMITS the command below;
> it never launches the plan inline.

## Objective

Every other plan of the epic owns a bounded set of findings. What was merged unfixed, or noticed and not claimed by
any plan, is collected here and closed last, so the epic ends with no orphaned residual. The security-relevant item is
the class-level `@BearerAuth` check, which was verified in a native container only with fixtures that carry
`@RegisterForReflection`. The rest is small: three client-side DSL-JSON parse sites that the validation-only sweep in
PLAN-15 does not reach, one method with no production caller, three test gaps, and two project-level questions that
need a recorded answer. The PLAN-13 and PLAN-14 landings added a sweep of log records that still render remote text
without `ErrorTextSanitizer`, and a batch of advisory residuals (documentation, two log-message causes, a weak test,
two ADRs, client response-strictness decisions). Fail-closed only: nothing here may widen acceptance.

## Deliverables

1. **Native-image coverage of class-level `@BearerAuth` (PLAN-08 residual, security-relevant)** — add an integration
   endpoint that carries a class-level `@BearerAuth` and does NOT carry `@RegisterForReflection`, with a
   `BearerAuthSpecIT` case that proves an unsatisfied class requirement is still enforced (refused, never allowed)
   and a satisfied one passes, run in the native container. Settle the two CodeRabbit notes PLAN-08 left unverified:
   (a) whether the class-level lookup needs native reflection metadata, and (b) whether the interceptor can fail
   open when ArC reports an unannotated generated override. A fail-open, if found, is fixed in
   `BearerTokenInterceptor` with a test that fails before and passes after. Also settle, and record in the PR body,
   whether the CI runner can run the native container build: cleanup re-grounding (2026-11-06) found that the CI path
   DOES build native (`integration-tests.yml` runs `verify -Pmulti-idp-tests`, whose profile enables native and runs
   `build-native-if-needed.sh`), so the question reduces to whether the reusable workflow in `cuioss-organization`
   gives the runner a container runtime, which is not readable from this repository. Exercise `StrictStringMapper` once in
   the native run (PLAN-11 residual: its `reflect-config.json` entry was never exercised natively).
2. **DSL-JSON truncated-escape guard (defect class, not sites)** — the three client sites this item first named (`DiscoveryResolver`, `ParClient`, `UserInfoClient`) were FIXED by PLAN-15 (#867, merge `262369d6`): each now catches `IOException | ArrayIndexOutOfBoundsException`, with tests (`DiscoveryResolverTest` +71, `ParClientTest` +27, `UserInfoClientTest` +26), and `DpopProofValidator`, `JwksParser`, `NonValidatingJwtParser` and both transport converters likewise; `TokenEndpointClient` had been fixed by PLAN-10 and PLAN-14. What is left is the class-level guard PLAN-15's candidate lesson 003 asks for: a defect tied to a library call pattern is scoped by the call pattern, not by the sites a request named. Add one source-scan architecture test that finds every `dslJson.deserialize` / `dslJson.newReader` call in main sources across the modules and fails when a call is not covered by a catch of `ArrayIndexOutOfBoundsException` in the same method, with an explicit, reasoned exemption list (at 262369d6 the only uncovered call is `MapRepresentation.fromJson`, whose two callers, `DpopProofValidator` and `NonValidatingJwtParser`, catch it). Shape it on `UncheckedThrowBoundaryTest`. Test-only; no production change expected.
3. **Dead code** — `ClaimValue.isNotPresentForClaimValueType()` has no production caller; only `ClaimValueTest`
   exercises it. Delete the method and its five assertions in `ClaimValueTest`, unless the outline finds a caller
   outside the swept tree (then record that and keep it).
4. **Residual test gaps** — (a) name the `params == null` branch of `SignatureVerificationUtil.checkEcdsaComponentRange` (now at line 132)
   with a test (a stub `ECPublicKey` whose `getParams()` returns `null` must be refused with a `SignatureException`);
   (b) establish whether `PsychicSignatureAttackTest` can fail without the explicit range check (PLAN-09 code-review
   claim, never tested: the JDK may reject an all-zero signature itself): either make it able to fail by removing
   the guard in a scratch run and recording the result, or remove the row; (c) DROPPED 2026-11-06 (cleanup re-grounding): the JWE top-level JSON `null` header is already logged and counted. `NonValidatingJwtParser.decodeJwtHeader` maps a `null` header to `rejectUndecodablePart`, which logs a WARN `FAILED_TO_DECODE_JWT` and increments that event, and `NonValidatingJwtParserJweDecodeModeTest` (PLAN-22, #863) pins one WARN and one count on normal decode and a count with no log on quiet decode. Nothing to do. (d) PLAN-15's landing names a known gap: the JWE-header catch in `NonValidatingJwtParser` (the `IOException | ArrayIndexOutOfBoundsException` catch at about line 372; PLAN-15 added one truncated-escape row to `NonValidatingJwtParserTest`) has no dedicated test; find which of the two header catches is untested, add the row, or record that the case is covered.
5. **Two project-level questions (decide at outline, record the answer)** — (a) `doc/developer/test-impl-mapping.adoc`
   does not exist, and the plan-time `module_mapping_validator` flagged cross-module test routing without it
   (PLAN-21): the plan asks the operator once whether the project wants the mapping document, writes it if so, and
   records "not wanted" if not; no code either way. (b) The Quarkus extension has no configuration key for the
   opt-in `maxStaleness` that `HttpJwksLoaderConfig` supports (PLAN-07): the plan asks the operator once whether to
   expose it. If yes, add the key to `JwtPropertyKeys`, the config mapping, `quarkus-config-doc.adoc` and a config
   test, default off (never widening); if not, record it as a deliberate non-goal in the PR body.
6. **Log-record sanitisation sweep (PLAN-13 lesson 006 and follow-up 1, CWE-117)** — route every log record that
   renders remote-supplied text through `ErrorTextSanitizer`, in one pass: `JWKSKeyLoader` (the `DUPLICATE_JWKS_KID`
   `kid`) and `KeyProcessor` (the remote `kty`, `kid`, algorithm
   and `e.getMessage()` in its warn and debug calls). Add a guard test that enumerates the module's log records by
   placeholder and fails when a new record takes an unsanitised remote-text argument, in the shape of
   `LogMessageIdentifierUniquenessTest` (PLAN-13, +278 lines). Do not change a template, an identifier or an event
   type; sanitise the argument only. `JwksParser` (the JSON-failure message) and `DpopProofValidator` were sanitised by PLAN-15 (#867) and drop out of the list. Never pass the raw exception object to the logger where its message embeds the input, and check every comment that asserts sanitisation against the actual logger arguments (PLAN-15 lesson 004: `DiscoveryResolver` once claimed CWE-117 safety while still logging the raw exception, caught only in the second review round); the guard test covers exception arguments too. Fail-closed: sanitising never removes the diagnostic, it escapes control
   characters and bounds the length.
7. **PLAN-13 and PLAN-14 advisory residuals** — each verified at outline, fixed or dropped with a reason:
   (a) `maxHttpResponseSize` (PLAN-13 V-15) is not described in `doc/validation/architecture.adoc` and
   `token-sheriff-validation/doc/developing.adoc`; (b) the `ParserConfig` Javadoc on `maxPayloadSize` still reads as
   if it bounded HTTP bodies; (c) the oversized-content test in `HttpJwksLoaderFailureTest` also passes on a missing
   `jwks_uri`, so make it fail for the oversize cause alone or remove the row; (d) `HttpJwksLoader`'s
   `UNUSABLE_URI_REASON` omits the unsupported-scheme cause and `ADVERTISED_URI_EGRESS_BLOCKED` omits the hostless-URI
   cause (the fixed reason strings, lines 138 and 146); (e) two ADRs through the ADR tooling: an amendment of ADR-0017
   recording an unusable advertised `jwks_uri` as a fourth terminal rejection (PLAN-22 amends the same file for
   retry-pending, so this runs after it), and a new ADR for "discovery refuses a non-`https` endpoint advertised by an
   `https` issuer, regardless of `allowInsecureHttp`" (PLAN-14); (f) three client response-strictness questions, each
   asked of the operator once and recorded: a blank optional endpoint in an `https` discovery document is refused (keep,
   fail-closed), a quoted numeric `expires_in` is accepted (PLAN-14 pinned it as observed; reject it or keep), and only
   `DiscoveryResolver` enforces the response `Content-Type` (`UserInfoClient` reads it to branch on `application/jwt` but
   does not enforce `application/json`; require `application/json` at the token, PAR and userinfo responses too, or
   record why not); (g) PLAN-14 lesson 004: the multi-line `Content-Type` bypass was found only in the
   second review round, so enumerate header line-break variants (CR, LF, CRLF, obs-fold continuation, embedded NUL) as a
   standard attack class for every header the client parses for a security decision, using the `cui-http-testing`
   attack-database pattern. A default of "pin as observed" applies where the operator does not answer;
   (h) PLAN-16 follow-up (folded 2026-11-06): PLAN-16 (#868) added `ClientAuthenticationFailedException`, raised by
   `TokenEndpointClient` for a 4xx `invalid_client` or exactly 401 `unauthorized_client`, and parked its ADR draft
   (it would have cost another finalize cycle). File that ADR through the ADR tooling from the draft preserved at
   `.plan/orchestrator/quality-report/drafts/adr-draft-failed-client-authentication.adoc` (its number 0020 is taken: the tree
   holds 0001 to 0026, so the tooling allocates the next free one, expected 0027 or later, re-checked against `main` at
   finalize because sibling plans add ADRs concurrently), and add the sentence the client specification and threat model
   lack on the new exception (no file under `doc/client` mentions `invalid_client` or `unauthorized_client`). This plan also
   adds the `doc/README.adoc` index rows for the ADRs it files, because PLAN-19, which indexes the ADRs, runs before it;
   (i) PLAN-24 follow-up (folded 2026-11-06, from the #876 landing and its three-layer reading): (1) the sentence at
   `doc/validation/security-reference.adoc` line 35, "so a non-string `sub` cannot satisfy a comparison", is not true as
   worded: `SUBJECT` is mapped by `IdentityMapper`, which stringifies, and `TokenLifecycleManager` compares the access-token
   subject with the ID-token subject as strings, so a numeric access-token `sub` equals the same text in the ID token. The
   comparison is token to token and both tokens are signature-verified, so it is no acceptance defect; correct the sentence
   to say that, or make the comparison type-aware if the operator prefers (a behaviour change, ask once). (2) One question for
   the operator, recorded: explicit JSON `null` for an identifier claim (`iss`, `sub`, `jti`, `client_id`, `azp`) is dropped by
   `MapRepresentation.fromJson` and so reads as absent (fail-closed for `client_id` with `azp` absent, accepted with a valid
   `azp`); the default where the operator does not answer is "pin as observed": add a `ClaimNameTest` row stating that null is
   treated as absent, and reword `ClaimName.java` line 173 and `security-reference.adoc` line 46 from "must be a JSON string" to
   "a present value must be a JSON string; an explicit `null` is treated as absent". Rejecting null needs `MapRepresentation`
   to keep null-valued keys, a cross-claim change: only on the operator's word, with a test per claim. (3) Test hygiene, no
   behaviour: `AudienceValidatorTest` (about line 297) still asserts only `assertThrows(TokenValidationException)` for a
   non-string `azp`, so tighten it to the event type and counts the `AuthorizedPartyValidatorTest` azp test now asserts; rename
   `JsonTypedAzpFixture` and `nonStringAzpValues`, which now also serve `client_id`; the eight-row
   `shouldRejectNonStringClientIdAtBuild` matrix tests one behaviour (the build throws before the validator is reached), so
   drop the `fallbackEnabled` parameter or add a row that reaches the validator. Origin: PLAN-24 (#876).

Scope guard: seven deliverables, above the six-deliverable threshold, so a rationale is recorded (2026-11-05). The
operator asked for ONE final plan for all leftovers, and each item is small and independent of the others; this is the
epic's last plan and it is not emitted until PLAN-13 to PLAN-24 have landed and their own residuals are folded in. The
split decision is therefore taken again at emit time, against the finished spec. Split at outline, and record it, when
deliverable 1 grows (a fail-open fix plus native metadata), deliverable 5(b) adds a config surface, or deliverable 6
touches more than the three named classes. The natural cut is: native-image (1) alone, log sanitisation (6) alone, the
rest together.

## Standing Constraints

- Every claim below was grounded at HEAD `0c56583e` (the PLAN-23 merge, 2026-11-05) unless labelled otherwise;
  re-verify each against the implementing source at outline before scoping on it (refutation loops back to re-scope; a
  residual that no longer exists is recorded and dropped). Plans PLAN-13 to PLAN-24 are expected to have merged first
  and change some of these files; line numbers are stale by then. The original report text is in
  `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- **Before emitting, the orchestrator folds in** the residuals of the landings of PLAN-13 to PLAN-24 that no plan
  owns, and re-reads this spec against them. Do not emit it unchanged.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a configuration that
  is rejected today. State in the PR body anything that narrows.
- This work has no finding ID of its own (it is a residual sweep). The PR title and body name the origin of each item
  (PLAN-05, 07, 08, 09, 10, 11, 21). Do NOT edit `source-report/` or `findings-disposition.md` — the orchestrator
  records dispositions.
- Every behavioural change ships with a test that fails before and passes after (JUnit 5, CUI test conventions,
  generators where variation matters); build and verify via Maven only. Documentation states a guarantee only at the
  scope the enforcing code gives it.
- No new `LogRecord` or event type. If outline finds one is unavoidable, the footprint declares both
  `JWTValidationLogMessages.java` and `doc/LogMessages.adoc` up front; a conditional "add it if needed" clause is not
  allowed (PLAN-11-001).
- A new class that the native image reflects on puts `reflect-config.json` and `NativeImageMetadataTest` into the
  footprint at the same time. The native build is slow; run it once, at the end, and say so in the PR body.
- Operator policy: tool findings (OpenRewrite markers, Sonar issues) are fixed immediately; suppression only in the
  rule's sanctioned syntax with a stated reason.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted via
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count
  at the head — `0` confirmed or every remaining issue dismissed; an `undecidable` read blocks. The Done-when lists the
  PR's open-issue count and the rules left open (`unknown` for a failed read, never `0`).
- The pre-push gate's stand-alone `test-compile` and `module-tests` arms cannot succeed in this reactor (the
  validation test-jar exists only after `package`); the whole-tree `verify -Ppre-commit` is the cover. Do not file the
  failed stand-alone arms as findings (PLAN-11-002).

## Claim Labels

- OBSERVED: both class-level fixtures, `ClassLevelAuthEndpoint` and `ClassLevelUnsatisfiedAuthEndpoint`, carry `@RegisterForReflection` (and extend `AbstractClassLevelAuthEndpoint`), so the unregistered case is untested — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/ClassLevelAuthEndpoint.java` and `ClassLevelUnsatisfiedAuthEndpoint.java` (lines 41)
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: both class-level fixtures carry @RegisterForReflection; no ClassLevelUnregistered* exists
- OBSERVED: `BearerAuthSpecIT` holds the class-level cases (class-only, union, inherited, unsatisfied, no-token) against `/jwt/class-level` and `/jwt/class-level-unsatisfied` — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java` lines 320-404
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: BearerAuthSpecIT lines 326-404 hold the class-level cases
- OBSERVED: `StrictStringMapper` has a `reflect-config.json` entry and `NativeImageMetadataTest` pins it with the constructors-only expectation — read at `token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/reflect-config.json` line 172 and `NativeImageMetadataTest.java` line 119; that the mapper works in a native image is not shown
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: reflect-config.json:172 and NativeImageMetadataTest:119 carry StrictStringMapper
- HYPOTHESIS: no native-image build runs in CI. CONTRADICTED 2026-11-06 and re-scoped: the CI path builds native through `-Pmulti-idp-tests` (`build-native-if-needed.sh`); only the runner's container support is unconfirmed (the reusable workflow lives in `cuioss-organization`). The integration-tests workflow runs `verify -Pmulti-idp-tests` and the native build lives behind the module's `integration-tests` profile — confirm/refute at `.github/workflows/integration-tests.yml` (line 41) and `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/pom.xml` (profile `integration-tests`, line 236), and read the reusable workflow it calls (verify-at-outline)
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: CI path builds native via -Pmulti-idp-tests and build-native-if-needed.sh; runner container support unconfirmed; deliverable 1 re-scoped
- HYPOTHESIS: the interceptor can fail open when ArC reports an unannotated generated override (CodeRabbit note on #850, never verified) — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java` (verify-at-outline; needs a native run to settle)
  - verdict: unverifiable | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: extractMergedAuth fails closed with no annotation on method or bean class; the unannotated-override case cannot be settled from source
- OBSERVED: `DiscoveryResolver` (catch at line 202), `ParClient` (line 147) and `UserInfoClient` (line 261) each catch only `IOException` around the JSON decode — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`, `.../flow/ParClient.java`, `.../token/UserInfoClient.java`. Whether a truncated escape leaks `ArrayIndexOutOfBoundsException` at each is untested (HYPOTHESIS, from the PLAN-10 lesson 004 on the validation-side sites); the outline writes the test first and lets it decide
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: PLAN-15 (#867) added ArrayIndexOutOfBoundsException to the DiscoveryResolver, ParClient and UserInfoClient catches with tests; deliverable 2 replaced by a class-level guard test
- OBSERVED: `ClaimValue.isNotPresentForClaimValueType()` is declared at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` line 113 and its only references in the tree are five assertions in `ClaimValueTest.java` lines 97-101 (grep over every `*.java`, excluding `target/`)
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: ClaimValue.java:113 declared; only ClaimValueTest 97-101 references it
- OBSERVED: the `params == null` branch exists in `SignatureVerificationUtil.checkEcdsaComponentRange` (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java` lines 134-137) and runs before `initVerify`; the PLAN-09 landing claim that these guards are shadowed by `initVerify` is refuted in part (recorded at the PLAN-09 landing). Whether a test names the branch is HYPOTHESIS (verify-at-outline in `SignatureVerificationUtilTest.java`)
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: params==null at SignatureVerificationUtil.java:132, untested
- HYPOTHESIS: `PsychicSignatureAttackTest` passes even without the explicit range check because the JDK rejects an all-zero signature itself (PLAN-09 code-review claim, never tested) — confirm/refute at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java` (verify-at-outline)
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: PsychicSignatureAttackTest cannot tell the explicit range check from a JDK rejection
- HYPOTHESIS (CONTRADICTED 2026-11-06 and re-scoped: it is logged WARN and counted `FAILED_TO_DECODE_JWT` via `rejectUndecodablePart`; item 4(c) dropped): a top-level JSON `null` JWE header is rejected but neither logged nor counted (PLAN-05 landing message, pre-existing) — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (verify-at-outline)
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: null JWE header is logged WARN and counted FAILED_TO_DECODE_JWT via rejectUndecodablePart; pinned by NonValidatingJwtParserJweDecodeModeTest; item 4(c) dropped
- OBSERVED (read 2026-11-05 at `origin/main` `7145aebb`, after PLAN-13 #861): `JWKSKeyLoader` line 423 logs the remote `kid` as `LOGGER.warn(WARN.DUPLICATE_JWKS_KID, keyInfo.keyId())`, `JwksParser` line 114 logs `e.getMessage()` of the JSON failure, and `KeyProcessor` logs the remote `kty`, `kid`, algorithm and `e.getMessage()` (lines 86, 112, 131, 151, 168, 193, 201, 223 among others), and none of the three files uses `ErrorTextSanitizer` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java`, `.../jwks/parser/JwksParser.java`, `.../jwks/parser/KeyProcessor.java`; `ErrorTextSanitizer` lives at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` and `HttpJwksLoader` already uses it. Whether each `e.getMessage()` can carry remote text is HYPOTHESIS (verify-at-outline).
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: JwksParser:114 message is now sanitised by PLAN-15 (#867); JWKSKeyLoader:423 and KeyProcessor sites still log remote text unsanitised; deliverable 6 narrowed
- OBSERVED (read at `7145aebb`): `LogMessageIdentifierUniquenessTest` exists (PLAN-13, +278 lines) at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogMessageIdentifierUniquenessTest.java` and is the model for the guard test; no test enumerates log-record arguments for remote text
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: LogMessageIdentifierUniquenessTest exists (278 lines); no guard test for remote-text arguments
- OBSERVED (read at `7145aebb`): `HttpJwksLoader` declares `UNUSABLE_URI_REASON` (line 138) and `ADVERTISED_URI_EGRESS_BLOCKED` (line 146) as fixed reason strings; the landing says they omit the unsupported-scheme and hostless-URI causes (landing message, not re-read for wording) — `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: UNUSABLE_URI_REASON at 138 and ADVERTISED_URI_EGRESS_BLOCKED at 146 omit the causes
- OBSERVED (read at `f29ca9cb`, after PLAN-14 #862; CONTRADICTED 2026-11-06 in part and re-scoped: `UserInfoClient` also reads the response `Content-Type` to branch on `application/jwt`, without enforcing `application/json`): only `DiscoveryResolver` checks the response `Content-Type` (every line must be `application/json`); `TokenEndpointClient`, `ParClient`, `RevocationClient` and `UserInfoClient` define a `Content-Type` constant for the request and no response check was found by grep over `token-sheriff-client/src/main/java`; `ParResponse.expiresIn` is a `long` (the quoted-number leniency is PLAN-14's pinned observation, not re-read)
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: UserInfoClient reads Content-Type to branch on application/jwt without enforcing application/json; item 7(f) reworded
- OBSERVED (read at `f29ca9cb`): PLAN-14 changed `DiscoveryResolver.java` (+80: scheme check on eight endpoints, `Content-Type` check) and `TokenEndpointClient.java` (+104: a repeated top-level member in a token response is refused); line numbers in deliverable 2 are stale after it
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: f29ca9cb changed DiscoveryResolver +80 and TokenEndpointClient +104; line numbers stale
- OBSERVED: `doc/developer/test-impl-mapping.adoc` does not exist and nothing in the tracked tree references it except the orchestrator ledger (grep over `*.adoc`, `*.md`, `*.json`, `*.yml`, `*.toml`)
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: doc/developer/test-impl-mapping.adoc does not exist
- OBSERVED: `HttpJwksLoaderConfig` carries the opt-in `maxStaleness` and no class under `token-sheriff-quarkus-parent/*/src/main` mentions staleness — grep `staleness` over `*.java`; read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` line 205
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: HttpJwksLoaderConfig:205 has maxStaleness; no Quarkus key
- Verified and DROPPED at staging (2026-11-05, read in `0c56583e`), so not carried: the authorization query is appended before a `#fragment` (`AuthorizationRequestBuilder` and `EndpointQuery`; PLAN-06 residual, fixed by PLAN-10); `AccessTokenCache.put` does not cache an `iat`-less token when a maximum age is enforced (`AccessTokenCache.java` line 278; PLAN-03 residual); the `HttpJwksLoader` documents that a `LOADING` status is never left in place and `TokenValidatorProducer.destroy()` now catches only `SecurityException` (PLAN-01 residuals). Env-only multi-key JWE configuration skipped without a startup signal is documented and pinned by a test (PLAN-08), an accepted behaviour, not carried.
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: fragment ordering, iat-less cache bypass, LOADING status and destroy() catch hold as recorded
- Verify-first clause: before scoping deliverable 2, write the truncated-escape test for each of the three sites and read the outcome; a site that already returns the typed error becomes a pinned test with no code change, a site that leaks becomes a fix. Before scoping deliverable 1, read the reusable CI workflow to settle whether a native build runs there. Refutation of the premise loops back to re-scope.
  - verdict: unverifiable | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: procedural verify-first clause; its native-CI premise is now answered by claim 3
- OBSERVED: `ErrorTextSanitizer` (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java`) gained `sanitize(String value, char replacement, int maxLength, String truncationMarker)` in PLAN-22 (#863, merge `38aefc0a`); `JweDecryptor` and `JwkKey` call it as `sanitize(value, '?', limit, "...")`. Deliverable 6 reuses that overload for its call sites instead of writing a fourth inline copy, and re-reads the file at outline — read in the merged diff
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: ErrorTextSanitizer:131 overload exists; JweDecryptor:328 and JwkKey:174 call it
- OBSERVED: the 400-line test budget is not stated in `AGENTS.md` or any versioned doc; `JweDecryptorTest` (1020 lines) and `AccessTokenCacheTest` (1000) are over it after PLAN-22 and 52 test classes in the tree exceed 400 lines (17 exceed 600, three exceed 1000: `DpopProofValidatorTest` 1375, `HttpJwksLoaderFailureTest` 1161, `IssuerConfigResolverTest` 1023). CONTRADICTED 2026-11-06 in the count of larger classes (this label first said about a dozen) and re-scoped. The split decision at emit time also settles, with the operator, whether the budget is real (Watch in `epic.md`) — read at `38aefc0a`
  - verdict: contradicted | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: yes | evidence: 52 test files exceed 400 lines (17 over 600, 3 over 1000), not about a dozen; label corrected
- OBSERVED: PLAN-16 (#868, merge `306e6053`) changed `TokenEndpointClient.java` (+62: `clientAuthenticationFailed`, allow-list of 4xx `invalid_client` or 401 `unauthorized_client`), added `ClientAuthenticationFailedException.java` and edited `CredentialRejectedException.java` Javadoc; this spec's HYPOTHESIS on `TokenEndpointClient.java` (response `Content-Type`, quoted `expires_in`) re-reads the file at outline — read in the merged diff
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: 306e6053 changed TokenEndpointClient +62, added ClientAuthenticationFailedException, CredentialRejectedException +7
- OBSERVED: no weak IT assertion shape remains in `token-sheriff-quarkus-integration-tests/src/test` (grep for `assertThrows(RuntimeException|Exception|Throwable.class` and `200 || 302` style predicates returns nothing) — read at `306e6053`; PLAN-16 lesson 002 therefore needs no project sweep
  - verdict: corroborated | checked_at: 262369d6 | by: quality-report/cleanup | rescoped: n/a | evidence: no weak IT assertion shape remains in the integration-tests sources
- OBSERVED: PLAN-15 (#867, merge `262369d6`, 23 files) added `ArrayIndexOutOfBoundsException` to the catch of `DiscoveryResolver` (also now sanitising its logged message), `ParClient`, `UserInfoClient`, `DpopProofValidator`, `JwksParser` (both reads, and the `JWKS_INVALID_JSON` message is sanitised), `NonValidatingJwtParser` (two catches) and both transport converters, with tests for the three client sites; the only `dslJson` read not covered in its own method is `MapRepresentation.fromJson`, covered by its two callers — read in the merged diff and a `git grep` at `262369d6`
- OBSERVED (read 2026-11-06 at `90f8474a`, the PLAN-24 merge): `MapRepresentation.fromJson` filters null-valued entries (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/MapRepresentation.java` lines 108-115, from #511), so an explicit JSON `null` claim never reaches `TokenBuilder.extractClaims` or a mapper; `ClaimName.CLIENT_ID` (line 179) and `AUTHORIZED_PARTY` (line 166) use `StrictStringMapper`, while `SUBJECT` (line 52), `ISSUER` (line 44) and `TOKEN_ID` (line 95) use `IdentityMapper`; `TokenLifecycleManager` (around line 1273) compares access-token and ID-token subjects as strings
- OBSERVED: PLAN-15 left TV-4 open by operator decision (the three enum tests restored, files identical to `main`); trivial enum tests stay as a project convention, no item here

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/AbstractClassLevelAuthEndpoint.java`
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/ClassLevelUnregisteredAuthEndpoint.java` (verify-at-outline — a NEW fixture file, name not fixed)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java` (verify-at-outline — only if a fail-open is found)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptorTest.java` (verify-at-outline — the unit test for a fail-open fix)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/UserInfoClient.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolverTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/ParClientTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/UserInfoClientTest.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValueTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtilTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` (verify-at-outline — only if the null-header event is a code change)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptorTest.java` (verify-at-outline)
- HYPOTHESIS: `doc/developer/test-impl-mapping.adoc` (verify-at-outline — a NEW file, only if the operator wants it)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeys.java` (verify-at-outline — only if the operator wants `maxStaleness` exposed)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/parser/JwksParser.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/parser/KeyProcessor.java`
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogRecordSanitizationGuardTest.java` (verify-at-outline — a NEW guard test file, name not fixed)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderFailureTest.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/ParserConfig.java`
- OBSERVED: `doc/validation/architecture.adoc`
- OBSERVED: `token-sheriff-validation/doc/developing.adoc`
- OBSERVED: `doc/adr/0017-A_JWKS_loader_reports_OK_only_with_usable_keys_and_retries_only_transient_discovery_failures.adoc`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java` (verify-at-outline — only if the operator wants the response `Content-Type` or the quoted `expires_in` tightened)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/hardening/TokenResponseAdversarialTest.java` (verify-at-outline)
- OBSERVED: `doc/validation/security-reference.adoc` (item 7(i), PLAN-24 follow-up: the `sub` sentence at line 35 and the `client_id` wording at line 46)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimName.java` (verify-at-outline — Javadoc wording on null, item 7(i)(2))
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimNameTest.java` (verify-at-outline — a null-is-absent row, item 7(i)(2))
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AuthorizedPartyValidatorTest.java` (verify-at-outline — matrix trim and fixture rename, item 7(i)(3))
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AudienceValidatorTest.java` (verify-at-outline — tighten the non-string azp assertion, item 7(i)(3))
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/JsonTypedAzpFixture.java` (verify-at-outline — rename, item 7(i)(3))
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/MapRepresentation.java` (verify-at-outline — only if the operator wants an explicit JSON null rejected, item 7(i)(2))

## Dependencies and Sequencing

- Depends on: PLAN-15, PLAN-16, PLAN-22 and PLAN-24 merged first (PLAN-13 and PLAN-14 have merged: #861 and #862; their landings changed `DiscoveryResolver.java`, `TokenEndpointClient.java`, `HttpJwksLoader.java`, `ParserConfig.java` and the converters, so those are re-read at outline). PLAN-14 declares `DiscoveryResolver.java` and the client tests; PLAN-15 and PLAN-22 declare validation files this plan touches (`SignatureVerificationUtilTest.java` and `JweDecryptor.java` among them, verify at outline); PLAN-16 and PLAN-17 touch the Quarkus integration tests and `BearerAuth.java`. Run it LAST, after PLAN-13 to PLAN-24, so it sees their final trees and the residuals they leave.
- Overlaps with: every plan above on the files named in them. They are single or few shared files and this plan runs after all of them, so none blocks.
- Adjacent to: PLAN-15 deliverable 6 (the validation-side DSL-JSON sweep). Use the same test shape and the same typed-error assertion, so the two sweeps read alike.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-25-epic-leftover-sweep.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
