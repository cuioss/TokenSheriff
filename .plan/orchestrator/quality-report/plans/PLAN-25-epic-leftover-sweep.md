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
   whether any native-image build runs in CI (the workflow files were not read for the native profile; see the claim
   labels) and, if none does, state that this class of check is local-only. Exercise `StrictStringMapper` once in
   the native run (PLAN-11 residual: its `reflect-config.json` entry was never exercised natively).
2. **Client-side DSL-JSON truncated-escape sweep** — PLAN-10 found that a body ending inside a string escape makes
   DSL-JSON throw an unchecked `ArrayIndexOutOfBoundsException` rather than an `IOException`. PLAN-15 covers the four
   validation-module sites. The three client sites catch only `IOException`: `DiscoveryResolver`, `ParClient` and
   `UserInfoClient` (the decode block of each). For each, add a MockWebServer test that serves a truncated-escape
   body and assert the outcome is the typed, fail-closed client error and never an uncaught runtime exception. Where
   one leaks, narrow the catch to the sanctioned form (see `AGENTS.md` on broad catches) and keep the typed result.
3. **Dead code** — `ClaimValue.isNotPresentForClaimValueType()` has no production caller; only `ClaimValueTest`
   exercises it. Delete the method and its five assertions in `ClaimValueTest`, unless the outline finds a caller
   outside the swept tree (then record that and keep it).
4. **Residual test gaps** — (a) name the `params == null` branch of `SignatureVerificationUtil.checkEcdsaComponentRange`
   with a test (a stub `ECPublicKey` whose `getParams()` returns `null` must be refused with a `SignatureException`);
   (b) establish whether `PsychicSignatureAttackTest` can fail without the explicit range check (PLAN-09 code-review
   claim, never tested: the JDK may reject an all-zero signature itself): either make it able to fail by removing
   the guard in a scratch run and recording the result, or remove the row; (c) the JWE top-level JSON `null` header
   is rejected but neither logged nor counted (PLAN-05, pre-existing): add a test that pins the rejection and the
   current event type, and count or log it only if the outline finds a defined event type for it that needs no new
   `LogRecord`.
5. **Two project-level questions (decide at outline, record the answer)** — (a) `doc/developer/test-impl-mapping.adoc`
   does not exist, and the plan-time `module_mapping_validator` flagged cross-module test routing without it
   (PLAN-21): the plan asks the operator once whether the project wants the mapping document, writes it if so, and
   records "not wanted" if not; no code either way. (b) The Quarkus extension has no configuration key for the
   opt-in `maxStaleness` that `HttpJwksLoaderConfig` supports (PLAN-07): the plan asks the operator once whether to
   expose it. If yes, add the key to `JwtPropertyKeys`, the config mapping, `quarkus-config-doc.adoc` and a config
   test, default off (never widening); if not, record it as a deliberate non-goal in the PR body.
6. **Log-record sanitisation sweep (PLAN-13 lesson 006 and follow-up 1, CWE-117)** — route every log record that
   renders remote-supplied text through `ErrorTextSanitizer`, in one pass: `JWKSKeyLoader` (the `DUPLICATE_JWKS_KID`
   `kid`), `JwksParser` (`e.getMessage()` of the JSON failure) and `KeyProcessor` (the remote `kty`, `kid`, algorithm
   and `e.getMessage()` in its warn and debug calls). Add a guard test that enumerates the module's log records by
   placeholder and fails when a new record takes an unsanitised remote-text argument, in the shape of
   `LogMessageIdentifierUniquenessTest` (PLAN-13, +278 lines). Do not change a template, an identifier or an event
   type; sanitise the argument only. Fail-closed: sanitising never removes the diagnostic, it escapes control
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
   `DiscoveryResolver` checks the response `Content-Type` (require `application/json` at the token, PAR and userinfo
   responses too, or record why not); (g) PLAN-14 lesson 004: the multi-line `Content-Type` bypass was found only in the
   second review round, so enumerate header line-break variants (CR, LF, CRLF, obs-fold continuation, embedded NUL) as a
   standard attack class for every header the client parses for a security decision, using the `cui-http-testing`
   attack-database pattern. A default of "pin as observed" applies where the operator does not answer.

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
- OBSERVED: `BearerAuthSpecIT` holds the class-level cases (class-only, union, inherited, unsatisfied, no-token) against `/jwt/class-level` and `/jwt/class-level-unsatisfied` — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java` lines 320-404
- OBSERVED: `StrictStringMapper` has a `reflect-config.json` entry and `NativeImageMetadataTest` pins it with the constructors-only expectation — read at `token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/reflect-config.json` line 172 and `NativeImageMetadataTest.java` line 119; that the mapper works in a native image is not shown
- HYPOTHESIS: no native-image build runs in CI. The integration-tests workflow runs `verify -Pmulti-idp-tests` and the native build lives behind the module's `integration-tests` profile — confirm/refute at `.github/workflows/integration-tests.yml` (line 41) and `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/pom.xml` (profile `integration-tests`, line 236), and read the reusable workflow it calls (verify-at-outline)
- HYPOTHESIS: the interceptor can fail open when ArC reports an unannotated generated override (CodeRabbit note on #850, never verified) — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java` (verify-at-outline; needs a native run to settle)
- OBSERVED: `DiscoveryResolver` (catch at line 202), `ParClient` (line 147) and `UserInfoClient` (line 261) each catch only `IOException` around the JSON decode — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`, `.../flow/ParClient.java`, `.../token/UserInfoClient.java`. Whether a truncated escape leaks `ArrayIndexOutOfBoundsException` at each is untested (HYPOTHESIS, from the PLAN-10 lesson 004 on the validation-side sites); the outline writes the test first and lets it decide
- OBSERVED: `ClaimValue.isNotPresentForClaimValueType()` is declared at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` line 113 and its only references in the tree are five assertions in `ClaimValueTest.java` lines 97-101 (grep over every `*.java`, excluding `target/`)
- OBSERVED: the `params == null` branch exists in `SignatureVerificationUtil.checkEcdsaComponentRange` (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java` lines 134-137) and runs before `initVerify`; the PLAN-09 landing claim that these guards are shadowed by `initVerify` is refuted in part (recorded at the PLAN-09 landing). Whether a test names the branch is HYPOTHESIS (verify-at-outline in `SignatureVerificationUtilTest.java`)
- HYPOTHESIS: `PsychicSignatureAttackTest` passes even without the explicit range check because the JDK rejects an all-zero signature itself (PLAN-09 code-review claim, never tested) — confirm/refute at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java` (verify-at-outline)
- HYPOTHESIS: a top-level JSON `null` JWE header is rejected but neither logged nor counted (PLAN-05 landing message, pre-existing) — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (verify-at-outline)
- OBSERVED (read 2026-11-05 at `origin/main` `7145aebb`, after PLAN-13 #861): `JWKSKeyLoader` line 423 logs the remote `kid` as `LOGGER.warn(WARN.DUPLICATE_JWKS_KID, keyInfo.keyId())`, `JwksParser` line 114 logs `e.getMessage()` of the JSON failure, and `KeyProcessor` logs the remote `kty`, `kid`, algorithm and `e.getMessage()` (lines 86, 112, 131, 151, 168, 193, 201, 223 among others), and none of the three files uses `ErrorTextSanitizer` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java`, `.../jwks/parser/JwksParser.java`, `.../jwks/parser/KeyProcessor.java`; `ErrorTextSanitizer` lives at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` and `HttpJwksLoader` already uses it. Whether each `e.getMessage()` can carry remote text is HYPOTHESIS (verify-at-outline).
- OBSERVED (read at `7145aebb`): `LogMessageIdentifierUniquenessTest` exists (PLAN-13, +278 lines) at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogMessageIdentifierUniquenessTest.java` and is the model for the guard test; no test enumerates log-record arguments for remote text
- OBSERVED (read at `7145aebb`): `HttpJwksLoader` declares `UNUSABLE_URI_REASON` (line 138) and `ADVERTISED_URI_EGRESS_BLOCKED` (line 146) as fixed reason strings; the landing says they omit the unsupported-scheme and hostless-URI causes (landing message, not re-read for wording) — `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
- OBSERVED (read at `f29ca9cb`, after PLAN-14 #862): only `DiscoveryResolver` checks the response `Content-Type` (every line must be `application/json`); `TokenEndpointClient`, `ParClient`, `RevocationClient` and `UserInfoClient` define a `Content-Type` constant for the request and no response check was found by grep over `token-sheriff-client/src/main/java`; `ParResponse.expiresIn` is a `long` (the quoted-number leniency is PLAN-14's pinned observation, not re-read)
- OBSERVED (read at `f29ca9cb`): PLAN-14 changed `DiscoveryResolver.java` (+80: scheme check on eight endpoints, `Content-Type` check) and `TokenEndpointClient.java` (+104: a repeated top-level member in a token response is refused); line numbers in deliverable 2 are stale after it
- OBSERVED: `doc/developer/test-impl-mapping.adoc` does not exist and nothing in the tracked tree references it except the orchestrator ledger (grep over `*.adoc`, `*.md`, `*.json`, `*.yml`, `*.toml`)
- OBSERVED: `HttpJwksLoaderConfig` carries the opt-in `maxStaleness` and no class under `token-sheriff-quarkus-parent/*/src/main` mentions staleness — grep `staleness` over `*.java`; read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` line 205
- Verified and DROPPED at staging (2026-11-05, read in `0c56583e`), so not carried: the authorization query is appended before a `#fragment` (`AuthorizationRequestBuilder` and `EndpointQuery`; PLAN-06 residual, fixed by PLAN-10); `AccessTokenCache.put` does not cache an `iat`-less token when a maximum age is enforced (`AccessTokenCache.java` line 278; PLAN-03 residual); the `HttpJwksLoader` documents that a `LOADING` status is never left in place and `TokenValidatorProducer.destroy()` now catches only `SecurityException` (PLAN-01 residuals). Env-only multi-key JWE configuration skipped without a startup signal is documented and pinned by a test (PLAN-08), an accepted behaviour, not carried.
- Verify-first clause: before scoping deliverable 2, write the truncated-escape test for each of the three sites and read the outcome; a site that already returns the typed error becomes a pinned test with no code change, a site that leaks becomes a fix. Before scoping deliverable 1, read the reusable CI workflow to settle whether a native build runs there. Refutation of the premise loops back to re-scope.

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
