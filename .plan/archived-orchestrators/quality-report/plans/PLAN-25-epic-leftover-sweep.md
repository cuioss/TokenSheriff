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

**Final cleanup (2026-11-07, operator: "make a full cleanup, gather all open issues, check Sonar at main, wrap all the result
into the final cleanup plan").** This is now the epic's only remaining plan and its closing sweep. It carries (1) every
residual that a landing, a Watch or an Open Defect left and no other plan owns, (2) the one open GitHub issue the operator
chose (#763, deliverable 8; #358 and #182 are deliberate non-goals, see the Residual Register), (3) the e2e teardown hang
(deliverable 9) and the old per-plan residuals nobody owns (deliverable 10), and (4) the zero-issue Sonar target. Sonar on
`main` was read directly on 2026-11-07 and stands at **0 unresolved issues**, gate OK, 0 hotspots (analysis `d120ad0f`),
so no Sonar backlog item exists; the plan's job is to keep it at 0 (Done-when).

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
2. **DSL-JSON truncated-escape guard (defect class, not sites)** — the three client sites this item first named (`DiscoveryResolver`, `ParClient`, `UserInfoClient`) were FIXED by PLAN-15 (#867, merge `262369d6`): each now catches `IOException | ArrayIndexOutOfBoundsException`, with tests (`DiscoveryResolverTest` +71, `ParClientTest` +27, `UserInfoClientTest` +26), and `DpopProofValidator`, `JwksParser`, `NonValidatingJwtParser` and both transport converters likewise; `TokenEndpointClient` had been fixed by PLAN-10 and PLAN-14. What is left is the class-level guard PLAN-15's candidate lesson 003 asks for: a defect tied to a library call pattern is scoped by the call pattern, not by the sites a request named. Add one source-scan architecture test that finds every `dslJson.deserialize` / `dslJson.newReader` call in main sources across the modules and fails when a call is not covered by a catch of `ArrayIndexOutOfBoundsException` in the same method, with an explicit, reasoned exemption list (at 262369d6 the only uncovered call is `MapRepresentation.fromJson`, whose two callers, `DpopProofValidator` and `NonValidatingJwtParser`, catch it). RE-GROUNDED 2026-11-07 at `d1af3ebb`: that list is too short. `NonValidatingJwtParser.decodeJwtHeader` (line 535, declares `throws IOException`) and `decodePayload` (lines 558-560) are covered by their callers in the same way, and `TokenEndpointClient` (lines 555 and 601) is covered by a broad `RuntimeException` catch rather than an explicit `ArrayIndexOutOfBoundsException` one. The guard therefore needs an exemption list with a reason per entry (callers cover it / broad catch covers it) and a stated rule for the broad catch, or it fails on day one. Shape it on `UncheckedThrowBoundaryTest` (`token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/architecture/`). Test-only; no production change expected.
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
   test, default off (never widening); if not, record it as a deliberate non-goal in the PR body. (c) The 400-line test budget
   (PLAN-01 and PLAN-22 residuals, the epic Watch): the figure is not in `AGENTS.md` or any versioned document, and at
   `d1af3ebb` 52 test classes exceed 400 lines, 18 exceed 600 and four reach 1000 or more (`DpopProofValidatorTest` 1407,
   `HttpJwksLoaderFailureTest` 1161, `IssuerConfigResolverTest` 1023, `JweDecryptorTest` 1020; `AccessTokenCacheTest` is exactly
   1000). Ask the operator once whether the budget is a real rule. Default where the operator does not answer: it is not a
   rule; the plan records that in the PR body and splits nothing. If it is a rule, the plan states it in `AGENTS.md` and splits
   only the four classes at or over 1000 lines, as pure moves with no assertion changed.
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

   (j) PLAN-18 follow-up (folded 2026-11-06, from the #877 landing and its three-layer reading; every `file:line` below was
   re-read at `af19b9c2`, re-read again at outline): documentation that still contradicts the code, the residuals the lane
   named plus what the reading added. (1) SSRF allow-list scope (major): `doc/commons/requirements.adoc` lines 81-85
   (COMMONS-3), `doc/commons/specification/transport.adoc` lines 127-132 and `doc/commons/threat-model.adoc` lines 43-48
   and 125 say every IdP-advertised URL (`jwks_uri`, discovery, `userinfo`, any advertised endpoint) is host allow-listed
   by `EgressPolicy`. `EgressPolicy` is used only by `HttpWellKnownResolver` and `HttpJwksLoader`; the client back channel
   (`BackChannelHttp`: token, PAR, userinfo, revocation, discovery) checks path structure and scheme only, as
   `doc/client/architecture.adoc` and the new `doc/commons/README.adoc` lines 24-27 already say. The
   `DiscoveryResolver.java` class Javadoc lines 45-48 ("the same TLS/SSRF-hardened transport primitive ... adds no new
   transport control of its own") is wrong in the same way (the resolver goes through `BackChannelHttp`, which adds egress
   path validation, a scheme check, the origin rewrite and a bounded read). Default where the operator does not answer:
   correct the docs and the Javadoc to the code. ONE QUESTION for the operator, recorded: should the client back channel
   apply a host-address egress policy of its own (a behaviour change that could refuse a configured endpoint; a fail-closed
   direction, with a test per endpoint kind), or do the docs follow the code? Applying it only on the operator's word.
   (2) Client test claims (major): `doc/client/specification/test-strategy.adoc` lines 128-132 and 233-235 and
   `doc/client/specification/step-up-and-logout.adoc` lines 112-114 credit the client tests with the shared
   `RevocationDispatcher`, `IntrospectionDispatcher` and `EndSessionDispatcher`; no client test imports them
   (`RevocationClientTest.java` line 168 declares its own nested class of that name), and the engine makes no end-session
   HTTP call (it builds the redirect). The same overstatement for the revocation dispatcher is in the text #877 wrote at
   `doc/commons/specification/transport.adoc` lines 45-47 and 178-180 and `doc/client/requirements.adoc` line 519. Correct
   to what the tests drive (token, userinfo, PAR), or add the missing tests if the operator wants the coverage claimed.
   (3) Incomplete wording (minor): `transport.adoc` lines 53-55 and `doc/commons/requirements.adoc` lines 51-53 (COMMONS-2)
   say plain-HTTP endpoints are rejected, but cleartext is admitted on explicit opt-in on both sides (PLAN-19, #879, qualified
   this only in `doc/validation/security-reference.adoc` line 52 and `threat-model.adoc` lines 45 and 189, so the commons docs now
   contradict the validation docs; `doc/commons/threat-model.adoc` lines 63-64 and the T-TLS row at line 127 carry the same
   unqualified wording and belong to this item; `transport.adoc` lines 64, 97 and 101 already describe the opt-in, so the file
   contradicts itself);
   `token-sheriff-client/README.adoc` lines 30-31 still says "every discovery and back-channel call" where the sibling
   claim at lines 84-87 was qualified to HTTPS; the "shared transport primitives ... resilience and caching" and "shared TLS /
   SSRF / resilience primitives" wording in `doc/commons/README.adoc` lines 22-23 and `doc/README.adoc` line 55 (was 30; PLAN-19 inserted the ADR index above it) (the client
   has no retry or discovery cache, and `EgressPolicy` is validation-side only), and the client README "Delegated" bullet
   (lines 29-31: `cui-http` and `de.cuioss.http.security` belong to the upstream library, and discovery is not delegated to
   commons) and the client pom `<description>`; and the unchanged paragraph in `commons/transport/package-info.java` lines
   35-37, which should say the allow-list is not applied to the client. (4) Verify at outline, not by reading: the header
   half of the duplicate-member sentence at `doc/validation/security-reference.adoc` line 61 ("a numeric `alg` ahead of a
   string `alg` can fail deserialization") rests on the generated DSL-JSON converter for `JwtHeader` and has no test; pin it
   with a test through the production parser, or soften the sentence. NOT in this item: the `<<CLIENT-23>>` at
   `doc/client/requirements.adoc` (PLAN-19 converted it to `xref:#CLIENT-23[]`, now line 210). Because PLAN-19 edits many of these
   same docs first, re-read each line at outline. Origin: PLAN-18 (#877).

   (k) PLAN-17 follow-up (folded 2026-11-06, from the #878 landing, its eight inbox messages and its three-layer reading;
   every `file:line` below was re-read at `d0fa0c7f`, re-read again at outline). (1) JWE size mismatch (major, a defect that
   pre-dates #878 and is still fail-closed): with JWE configured, `TokenStringValidator` (lines 65-67, 96) admits a token
   string up to `maxEncryptedTokenSize` (default 32768, `JweDecryptionConfig` line 51), but `NonValidatingJwtParser.decodeInternal`
   (lines 294-295) rejects anything above the parser's own `maxTokenSize` (default 8192, `ParserConfig` line 85) with
   `IllegalArgumentException`, not `TokenValidationException`. A token string of 8193 to 32768 bytes (a 5-part JWE, an opaque
   refresh token or garbage) therefore escapes `RefreshTokenValidationPipeline` (catch at line 100) and `BearerTokenProducer`
   (catch at line 252) as an unhandled `IllegalArgumentException`, with no WARN and no security event, and every legitimate JWE above
   8 KiB is unusable although `JwtPropertyKeys` line 938 documents 32 KB. No test pairs `maxEncryptedTokenSize` with an oversized
   token. Required: a test that fails before and passes after, driven through the production entry points (`TokenValidator`
   create-access/id/refresh paths and `BearerTokenProducer`) with a 5-part token of about 10 KiB under a JWE-enabled issuer. The
   fix direction is an operator decision (ONE QUESTION, recorded): make the parser limit follow the effective limit of
   `TokenStringValidator` so a JWE up to 32 KiB works (documented intent; widens acceptance only for JWE-enabled issuers and only
   up to the documented limit), or keep 8 KiB and map the failure to a `TokenValidationException` with a WARN and a security event
   (stays rejecting; the 32 KB documentation then changes). Default where the operator does not answer: the second, because the
   standing constraint forbids widening acceptance unless the deliverable says so. Becomes a separate fix plan on the operator's
   word (recommended: it is the epic's only remaining behavioural change besides 7(i), and this plan is above six deliverables).
   (2) Javadoc that contradicts the code or claims test coverage that does not exist (minor, all comment text only):
   `NonValidatingJwtParser.java` line 107 (the size pre-check is run by all three pipelines, not "ID-token parsing", and
   "its limit can differ" is the defect in (1)); lines 208-212 and 230-234 (`decode` and `decodeOpaqueToken` declare only
   `TokenValidationException` although the class text promises `IllegalArgumentException`); line 54 ("exercised by
   `NonValidatingJwtParserTest`" does not hold for the null, empty, oversized and JSON-null cases); `DiscoverableTokenValidationRule.java`
   line 54 ("exercised by `TokenValidationRuleTest`", which never reads `getClaims()` or `tenant_id`); `BearerTokenProducer.java`
   line 376 (the Authorization header only; the cookie is the fallback); `BearerTokenStatus.java` lines 41, 50 and 91 (same);
   `JwksLoaderFactory.java` lines 30-31 (the two bullets do not match the three explicit methods and the new line 32 contradicts
   the first); `JwtPropertyKeys.java` line 176 ("preferred and allowed": there is one list plus the rejected set);
   `IssuerConfig.java` lines 749-750 and 756 ("in order of preference": membership only, the order changes nothing);
   `doc/validation/threat-model.adoc` line 66 ("Token blacklisting", a terminology leftover in a file PLAN-19 edits first).
   Either soften the claim or add the test the sentence names. (3) Not settled by reading (verify at outline with a run, or
   soften): the statement in `BearerTokenProducer` line 59 and `BearerToken` lines 37-40 and 105-110 that
   `Authorization: Bearer ` with an empty token gives `INVALID_REQUEST` and no cookie fallback; HTTP servers normally strip
   trailing whitespace, so a real request may arrive as `Bearer` and fall back to the cookie. Also: `{@snippet}` bodies are
   not compiled, so prefer examples mirrored by a test where feasible. Origin: PLAN-17 (#878).

   (l) PLAN-19 follow-up (folded 2026-11-07, from the #879 landing, its twelve inbox messages and its three-layer reading;
   every `file:line` below was re-read at `d1af3ebb` unless marked "leaf"; documentation and comment text only, no behaviour).
   (1) The commons documents contradict the new cleartext opt-in text: see 7(j)(3), which now also names
   `doc/commons/threat-model.adoc` lines 63-64 and 127. (2) The "untouched and still valid" wording that ADR-0010 retracted:
   `RefreshFailureClassification.java` lines 29-33 ("The presented token is untouched and still valid"), 77-78 ("untouched and
   still usable") and 124 (leaf), `RefreshFlow.java` lines 87 and 277-278, and, named by the leaf and not yet re-read,
   `RefreshRedemption.java` lines 35 and 44, `TokenLifecycleManager.java` lines 82 and 107, `CredentialRejectedException.java`
   lines 28-31, `ClientAuthenticationFailedException.java` line 46 and `doc/client/specification/token-handling.adoc` lines 127 and
   243. After a connection failure or a `5xx` that follows redemption the server may already have rotated the token, so say
   what the client knows (the token is kept locally; the server may have rotated it) and keep the session-preservation rule
   unchanged; check each site for the qualifier ADR-0010 added before editing. (3) `doc/adr/0010-…` line 187 (Alternatives:
   "a server-error status and a connection failure mean the server never judged the credential") contradicts the ADR's own
   rewritten Context and Risks; align it. The superseded `doc/adr/0005-…` lines 55 and 100 keep the old wording and are left
   as a historical record unless an ADR-tooling amendment note is cheaper than the drift. (4)
   `token-sheriff-quarkus-parent/README.adoc` line 14 credits `ClientRuntimeConfig` with the "configuration mapping"; it holds the
   key constants, `ClientConfigMapper` maps (the new client-quarkus README line 9 already says so). (5) Verify with a run, or
   soften: ADR-0026's statement that an application-supplied `@Alternative TokenValidator` bypasses the startup check is read
   from `TokenValidatorStartupInitializer` (leaf, lines 64 and 95), but `ObservedValidatorResolver` lines 112-113 may resolve the
   issuer-config handles lazily and throw outside startup. (6) PLAN-19's spec required AsciiDoc link verification with zero
   broken links and the landing carries no evidence of one: run the project's AsciiDoc/xref check once over `doc/` and the
   module READMEs (including the `xref:#id[]` form with empty link text) and record the result in the PR body; a broken target
   is fixed here. (7) `doc/validation/threat-model.adoc` line 66 ("Token blacklisting for revoked tokens") survived PLAN-19, which
   edited the file; it is PLAN-17's L3-9 and belongs to 7(k). Origin: PLAN-19 (#879).

8. **GitHub issue #763 — expose the OAuth error code `invalid_scope` on token-endpoint refusals (operator, 2026-11-07: "only
   #763")** — `TokenEndpointClient.requestToken` recognises only `invalid_grant` (`CredentialRejectedException`) and, since
   PLAN-16 (#868), `invalid_client` / 401 `unauthorized_client` (`ClientAuthenticationFailedException`); every other `4xx`,
   including `invalid_scope` (RFC 6749 section 5.2), is a plain `TransportException("Token endpoint returned unexpected HTTP
   status 400")` with no code and no body, and `RefreshFlow.classify` maps it to `preRedemption()`. A caller therefore cannot
   tell "the IdP refused the requested scope" (definitive, not retryable) from an outage (retryable); API Sheriff's BFF
   scope-driven refresh needs the difference (keep the session, answer 403, no back-off, no WARN). Read at `d1af3ebb`: no
   `invalid_scope` and no scope-refusal type exists in `token-sheriff-client/src/main` or `doc/client`. Shape it on PLAN-16's
   allow-list: a new typed exception for a `4xx` whose RFC 6749 section 5.2 body names exactly `invalid_scope`, raised by
   `TokenEndpointClient` for the refresh grant and, if the outline finds the same code path, for every grant; classify it in
   `RefreshFlow.classify` so the session is preserved (the refusal happened before any grant was applied) and the failure is
   not folded into the retryable `preRedemption()` bucket; carry the parsed `error` code and a sanitised `error_description`
   (`ErrorTextSanitizer`, CWE-117; never the raw body). Fail-closed: the request is still refused, nothing is newly accepted,
   and a body that is not RFC 6749 section 5.2 JSON, or names another code, keeps today's result. Test per path, fails before
   and passes after, in the `TokenResponseAdversarialTest` / `RefreshFailureClassificationTest` shape; document the new
   exception in `doc/client` (specification and threat model, with the `invalid_client` sentence 7(h) adds) and extend ADR-0010
   (or the ADR 7(h) files) with the classification, through the ADR tooling. The PR body says `Closes #763`; after the merge,
   comment on the issue through `ci issue comment` with the release that carries it. The type's name and whether it extends an
   existing exception are an outline decision; `RedeemedScopeRefusalException` already exists in the same package and is read
   first to avoid a second meaning of "scope refusal".
9. **e2e teardown hang (epic Watch, PLAN-24 #876 and PLAN-13 landings)** — the `e2e-tests` job was cancelled by its 30-minute
   job timeout after all 30 tests passed on the PLAN-24 PR (a rerun cleared it) and a hung `e2e-tests` job ejected PLAN-13 from
   the merge queue; the PLAN-24 landing says it recurs on `main`. Read the logs of the cancelled and the ejected runs through
   `ci checks` / `ci-artifacts`, find what blocks the teardown (container shutdown, a non-daemon thread, a compose stop), and
   fix it in this repository (`scripts/stop-integration-container.sh`, `lib-docker-compose.sh`, the test JVM's shutdown) with the
   smallest change, or, when the cause lies in the reusable workflow in `cuioss-organization`, record the evidence and the
   owner in the PR body and change nothing here. A stale unmerged branch `fix/docker-compose-cleanup-resilience` carries an
   earlier attempt in the same scripts (PR #580, merged; the branch is only leftover) and is read for context, not rebased.
   Done when two consecutive `e2e-tests` runs on `main` after the merge finish without the timeout, or the evidence names the
   owner. Never mark the job non-required to pass a gate.
10. **Old per-plan residuals no plan owns (verify and close)** — each is read at outline and either fixed (a comment, a label,
    a test row, a sentence), or dropped with the line that closes it. Already verified closed 2026-11-07 and so NOT work:
    PLAN-02's OpenRewrite `TODO` marker in `holdsNewerGrant` (`git grep` finds no `TODO` or `FIXME` in any Java source) and
    PLAN-03's swapped `@DisplayName` labels (`AccessTokenValidationPipelineTest` lines 108 "(V-2)" and 167 "(V-9)" are correct).
    To verify: (a) PLAN-02: the stale comment on `RefreshAdversarialTest.shouldFailClosedWhenRevocationThrowsUnchecked`;
    (b) PLAN-03: no test combines a non-zero clock skew with a maximum token age (`AccessTokenValidationPipelineTest`);
    (c) PLAN-04: `JwksEndpointHealthCheck` / `TokenValidatorHealthCheck` catch-suppression placement, the README wording for
    applications that bring their own `TokenValidator`, and whether `TokenValidatorStartupInitializerTest` pins the proxy
    dereference; (d) PLAN-05: `doc/validation/specification/token-decryption.adoc` line 200 over-generalises the `kty` rejection,
    and the test gaps (null or blank `epk` coordinate, `apu`/`apv` decryption, inner-JWS payload decode failure, the
    unsupported-compression row) in `JweDecryptorTest` or its split classes; (e) PLAN-07: staleness is measured on the wall
    clock, and the unplanned cache-hit health check in `AccessTokenValidationPipeline` (a behaviour change beyond that spec)
    has no document naming it: state it in the validation architecture document or record why not; (f) PLAN-20: whether an
    existing test pins a throwing `TokenStore.retrieve` (`TokenStoreTest`); (g) PLAN-22: the `TokenLifecycleManager` quarantine
    widening that landed under a documentation plan is not listed in `doc/client/threat-model.adoc`; add it; (h) PLAN-10:
    `doc/resources/diagrams/client-auth-code-pkce.svg` was read only as a diff stat, render it once and fix what is wrong; (i)
    PLAN-13/14: `ParserConfig` Javadoc and the response-strictness points are already in 7(a), 7(b) and 7(f). Fail-closed; no
    behaviour change is expected from any of these, and one that turns out to need one is stopped and reported, not made.

Scope guard: ten deliverables, far above the six-deliverable threshold, so a rationale is recorded (2026-11-05, retaken
2026-11-07). The operator asked twice for ONE final plan for all leftovers ("create a final followup plan for all the
leftovers", then "wrap all the result into the final cleanup plan"), and each item is small and independent of the others; this
is the epic's last plan, emitted after PLAN-01 to PLAN-24 landed, with their residuals folded in. Because the plan is large,
the plan lifecycle outlines it in the natural cuts and may split it at outline (and records that): (A) native-image (1) alone,
(B) log sanitisation (6) alone, (C) the JWE size defect 7(k)(1) with its test, (D) the client change (8) with 7(h), (E) the
documentation and comment sweeps (7(a), 7(b), 7(j), 7(k)(2), 7(l), 10), (F) the test guards and gaps (2, 3, 4, 7(c), 7(i)(3)), (G)
the e2e hang (9). Split at outline, and record it, when deliverable 1 grows (a fail-open fix plus native metadata), 5(b) adds a
config surface, or 6 touches more than the three named classes. A split is the plan's call and not a new orchestrator plan;
each cut still lands under this spec.

## Residual Register (2026-11-07 — every open item of the epic and where it goes)

Built from `epic.md` (Open Defects and Watches), `findings-disposition.md`, the open GitHub issues and the Sonar read.

| Source | Item | Disposition |
|--------|------|-------------|
| Sonar, `main` 2026-11-07 | 0 unresolved issues, gate OK, 0 hotspots, coverage 93.2 | nothing to fix; held at 0 by the Done-when below |
| Sonar tool | `sonar_rest search` has no branch and no `resolved=false` filter, so its default read shows 47 closed rows as if open | upstream report (seventh addendum); this plan uses a branch-scoped, `resolved=false` read |
| GitHub #763 | `invalid_scope` not exposed | deliverable 8 |
| GitHub #358 | replace `VertxHttpServletRequestAdapter` with a header interface (H-7, core-API boundary refactor) | deliberate non-goal (operator, 2026-11-07); stays open on GitHub |
| GitHub #182 | IDE false-positive warnings for custom properties (still names the old `sheriff.oauth.*` prefix) | deliberate non-goal (operator, 2026-11-07); stays open on GitHub |
| Open Defect | JWE 8-32 KiB `IllegalArgumentException` (PLAN-17) | 7(k)(1) |
| Open Defect | thirteen plus one unstamped `effort` landings (PLAN-01 to PLAN-19) | upstream-owned (plan-marshall); upstream report; no work here |
| Open Defect | the lesson-routing gap | filed upstream 2026-11-05; later addenda unsent; operator decides whether to send |
| Watch | PLAN-19 landing residue | 7(l); the process half (quota, merge message, Sonar skip) is upstream-owned |
| Watch | PLAN-18 landing residue | 7(j); squash-message residue accepted or upstream |
| Watch | PLAN-17 landing residue | 7(k) |
| Watch | e2e teardown hang | deliverable 9 |
| Watch | PLAN-13/14 residuals | 7(a) to 7(g), 6 |
| Watch | PLAN-23 residuals | S8786 cleared (Sonar 0); `StepUpHandler` guard narrows behaviour, recorded; native IT in CI confirmed by 1 |
| Watch | PLAN-10, PLAN-11 residuals | 2, 7(i), 10(h); `final_merge_without_asking` RESOLVED |
| Watch | PLAN-09 residuals | 4(a), 4(b) |
| Watch | PLAN-06, 07, 08, 12, 20, 21, 02, 03, 04, 05, 01 residuals | 1, 5(a), 5(b), 10 (a) to (g); the rest recorded as informational (override merges, degraded gate arms, unfinished post-merge benchmark runs) |
| Watch | PLAN-22 residuals | 5(c) (test budget), 10(g) |
| Watch | PLAN-16 residuals | 7(h) |
| Watch | PLAN-15 residuals | TV-4 stays as decided; 2, 6, 4(d) |
| Watch | TC-9 IT part (trust-all TLS in integration tests) | deliberate non-goal: a real truststore for the Keycloak container is IT-cost; re-check if IT TLS infrastructure changes |
| findings-disposition | TV-4 partial (enum tests) | not fixed by operator decision (#867) |
| findings-disposition | D-2, D-6, V-17d, V-17h, J-1..J-12 residuals | 7(j), 7(k), 7(a) |
| Repository | stale local and remote branches (`gh-readonly-queue/*`, `chore/steward-*`, `chore/orchestrator-use-worktree`, `security/cve-lessons-register-and-hardening`, `fix/docker-compose-cleanup-resilience`) | operator housekeeping; not a plan item (deleting remote branches is outward-facing) |
| Dependabot | PR #872 (eslint-plugin-unicorn 77 to 79) | left to the repository's automerge policy |
| Upstream report | seven 2026-11-06/07 addenda and earlier, unsent | operator decides; see `upstream-plan-marshall-report.md` |

## Done-when

- Every deliverable is done, dropped with a recorded reason, or (for a decision the operator did not answer) done under its
  stated default.
- **Sonar: the PR head reads 0 unresolved issues (`count_status: confirmed`, never `undecidable`; an unread count is
  recorded as `unknown`), the quality gate is OK, and a branch-scoped `resolved=false` read of `main` after the merge returns
  0.** Report both numbers in the PR body. A finding the plan cannot fix is dismissed with a stated reason as in the Standing
  Constraints, never left open.
- The whole-tree `verify -Ppre-commit` (Maven only) is green on the head and the PR checks are green.
- `main` stays at 0 Sonar issues and the `e2e-tests` job does not time out on two consecutive runs after the merge (9).

## Standing Constraints

- Every claim below was grounded at HEAD `0c56583e` (the PLAN-23 merge, 2026-11-05) unless labelled otherwise;
  re-verify each against the implementing source at outline before scoping on it (refutation loops back to re-scope; a
  residual that no longer exists is recorded and dropped). Plans PLAN-13 to PLAN-24 are expected to have merged first
  and change some of these files; line numbers are stale by then. The original report text is in
  `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- **Before emitting, the orchestrator folds in** the residuals of the landings of PLAN-13 to PLAN-24 that no plan
  owns, and re-reads this spec against them. Do not emit it unchanged. DONE 2026-11-07: all landings through PLAN-19
  (#879, `d1af3ebb`) are folded, the 28 claim verdicts below were re-grounded at `d1af3ebb`, and the Residual Register lists
  every open item of the epic with its disposition.
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
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: both ClassLevel*Endpoint.java carry @RegisterForReflection at line 41; no ClassLevelUnregistered* exists
- OBSERVED: `BearerAuthSpecIT` holds the class-level cases (class-only, union, inherited, unsatisfied, no-token) against `/jwt/class-level` and `/jwt/class-level-unsatisfied` — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java` lines 320-404
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: BearerAuthSpecIT class-level section from line 320, cases 326-407
- OBSERVED: `StrictStringMapper` has a `reflect-config.json` entry and `NativeImageMetadataTest` pins it with the constructors-only expectation — read at `token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/reflect-config.json` line 172 and `NativeImageMetadataTest.java` line 119; that the mapper works in a native image is not shown
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: reflect-config.json:172 and NativeImageMetadataTest:119 (constructors only); native working not shown
- HYPOTHESIS: no native-image build runs in CI. CONTRADICTED 2026-11-06 and re-scoped: the CI path builds native through `-Pmulti-idp-tests` (`build-native-if-needed.sh`); only the runner's container support is unconfirmed (the reusable workflow lives in `cuioss-organization`). The integration-tests workflow runs `verify -Pmulti-idp-tests` and the native build lives behind the module's `integration-tests` profile — confirm/refute at `.github/workflows/integration-tests.yml` (line 41) and `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/pom.xml` (profile `integration-tests`, line 236), and read the reusable workflow it calls (verify-at-outline)
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: integration-tests.yml:41 runs verify -Pmulti-idp-tests; pom profile multi-idp-tests (416) enables quarkus.native (423) and runs build-native-if-needed.sh (493); runner container support not readable here; re-scope holds
- HYPOTHESIS: the interceptor can fail open when ArC reports an unannotated generated override (CodeRabbit note on #850, never verified) — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java` (verify-at-outline; needs a native run to settle)
  - verdict: unverifiable | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: BearerTokenInterceptor.extractMergedAuth (191-210) fails closed with no annotation; the unannotated generated override needs a native run
- OBSERVED: `DiscoveryResolver` (catch at line 202), `ParClient` (line 147) and `UserInfoClient` (line 261) each catch only `IOException` around the JSON decode — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`, `.../flow/ParClient.java`, `.../token/UserInfoClient.java`. Whether a truncated escape leaks `ArrayIndexOutOfBoundsException` at each is untested (HYPOTHESIS, from the PLAN-10 lesson 004 on the validation-side sites); the outline writes the test first and lets it decide
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: catches are IOException | ArrayIndexOutOfBoundsException at DiscoveryResolver:223 (was 202), ParClient:147, UserInfoClient:261; re-scope holds
- OBSERVED: `ClaimValue.isNotPresentForClaimValueType()` is declared at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` line 113 and its only references in the tree are five assertions in `ClaimValueTest.java` lines 97-101 (grep over every `*.java`, excluding `target/`)
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: ClaimValue.java:113 declared; only ClaimValueTest:97-101 references it
- OBSERVED: the `params == null` branch exists in `SignatureVerificationUtil.checkEcdsaComponentRange` (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java` lines 134-137) and runs before `initVerify`; the PLAN-09 landing claim that these guards are shadowed by `initVerify` is refuted in part (recorded at the PLAN-09 landing). Whether a test names the branch is HYPOTHESIS (verify-at-outline in `SignatureVerificationUtilTest.java`)
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: params==null branch now at SignatureVerificationUtil.java:131-134; no test stubs getParams()==null
- HYPOTHESIS: `PsychicSignatureAttackTest` passes even without the explicit range check because the JDK rejects an all-zero signature itself (PLAN-09 code-review claim, never tested) — confirm/refute at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java` (verify-at-outline)
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: PsychicSignatureAttackTest:105-112 asserts the exception type and count only; explicit check at SignatureVerificationUtil:138-141 not isolated; needs a scratch run
- HYPOTHESIS (CONTRADICTED 2026-11-06 and re-scoped: it is logged WARN and counted `FAILED_TO_DECODE_JWT` via `rejectUndecodablePart`; item 4(c) dropped): a top-level JSON `null` JWE header is rejected but neither logged nor counted (PLAN-05 landing message, pre-existing) — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (verify-at-outline)
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: NonValidatingJwtParser:537-538 rejectUndecodablePart on a null header; row at NonValidatingJwtParserJweDecodeModeTest:184-187; re-scope holds
- OBSERVED (read 2026-11-05 at `origin/main` `7145aebb`, after PLAN-13 #861): `JWKSKeyLoader` line 423 logs the remote `kid` as `LOGGER.warn(WARN.DUPLICATE_JWKS_KID, keyInfo.keyId())`, `JwksParser` line 114 logs `e.getMessage()` of the JSON failure, and `KeyProcessor` logs the remote `kty`, `kid`, algorithm and `e.getMessage()` (lines 86, 112, 131, 151, 168, 193, 201, 223 among others), and none of the three files uses `ErrorTextSanitizer` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java`, `.../jwks/parser/JwksParser.java`, `.../jwks/parser/KeyProcessor.java`; `ErrorTextSanitizer` lives at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` and `HttpJwksLoader` already uses it. Whether each `e.getMessage()` can carry remote text is HYPOTHESIS (verify-at-outline).
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: JwksParser:120 now sanitises (moved from 114); JWKSKeyLoader:423 logs raw keyInfo.keyId(); KeyProcessor raw at 86,112,131,151,168,193,201,223; re-scope holds
- OBSERVED (read at `7145aebb`): `LogMessageIdentifierUniquenessTest` exists (PLAN-13, +278 lines) at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogMessageIdentifierUniquenessTest.java` and is the model for the guard test; no test enumerates log-record arguments for remote text
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: LogMessageIdentifierUniquenessTest 278 lines; no guard test for remote-text arguments exists
- OBSERVED (read at `7145aebb`): `HttpJwksLoader` declares `UNUSABLE_URI_REASON` (line 138) and `ADVERTISED_URI_EGRESS_BLOCKED` (line 146) as fixed reason strings; the landing says they omit the unsupported-scheme and hostless-URI causes (landing message, not re-read for wording) — `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: HttpJwksLoader:138 UNUSABLE_URI_REASON omits the scheme cause; :146 ADVERTISED_URI_EGRESS_BLOCKED omits the hostless cause
- OBSERVED (read at `f29ca9cb`, after PLAN-14 #862; CONTRADICTED 2026-11-06 in part and re-scoped: `UserInfoClient` also reads the response `Content-Type` to branch on `application/jwt`, without enforcing `application/json`): only `DiscoveryResolver` checks the response `Content-Type` (every line must be `application/json`); `TokenEndpointClient`, `ParClient`, `RevocationClient` and `UserInfoClient` define a `Content-Type` constant for the request and no response check was found by grep over `token-sheriff-client/src/main/java`; `ParResponse.expiresIn` is a `long` (the quoted-number leniency is PLAN-14's pinned observation, not re-read)
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: only DiscoveryResolver:184,300 checks the response Content-Type; UserInfoClient:199 reads it only to branch on application/jwt; ParResponse.expiresIn:49 and TokenResponse.expiresIn:73 are long; re-scope holds
- OBSERVED (read at `f29ca9cb`): PLAN-14 changed `DiscoveryResolver.java` (+80: scheme check on eight endpoints, `Content-Type` check) and `TokenEndpointClient.java` (+104: a repeated top-level member in a token response is refused); line numbers in deliverable 2 are stale after it
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: git show --stat f29ca9cb: DiscoveryResolver +80, TokenEndpointClient +104
- OBSERVED: `doc/developer/test-impl-mapping.adoc` does not exist and nothing in the tracked tree references it except the orchestrator ledger (grep over `*.adoc`, `*.md`, `*.json`, `*.yml`, `*.toml`)
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: doc/developer/ does not exist; references only under .plan/
- OBSERVED: `HttpJwksLoaderConfig` carries the opt-in `maxStaleness` and no class under `token-sheriff-quarkus-parent/*/src/main` mentions staleness — grep `staleness` over `*.java`; read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` line 205
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: HttpJwksLoaderConfig:205 maxStaleness; no staleness in any quarkus-parent main class
- Verified and DROPPED at staging (2026-11-05, read in `0c56583e`), so not carried: the authorization query is appended before a `#fragment` (`AuthorizationRequestBuilder` and `EndpointQuery`; PLAN-06 residual, fixed by PLAN-10); `AccessTokenCache.put` does not cache an `iat`-less token when a maximum age is enforced (`AccessTokenCache.java` line 278; PLAN-03 residual); the `HttpJwksLoader` documents that a `LOADING` status is never left in place and `TokenValidatorProducer.destroy()` now catches only `SecurityException` (PLAN-01 residuals). Env-only multi-key JWE configuration skipped without a startup signal is documented and pinned by a test (PLAN-08), an accepted behaviour, not carried.
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: AccessTokenCache ~277-281 skips an iat-less token under age validation; TokenValidatorProducer:192 catches only SecurityException; fragment and LOADING items not re-read
- Verify-first clause: before scoping deliverable 2, write the truncated-escape test for each of the three sites and read the outcome; a site that already returns the typed error becomes a pinned test with no code change, a site that leaks becomes a fix. Before scoping deliverable 1, read the reusable CI workflow to settle whether a native build runs there. Refutation of the premise loops back to re-scope.
  - verdict: unverifiable | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: procedural verify-first clause; its native-CI premise is answered by claim 3
- OBSERVED: `ErrorTextSanitizer` (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java`) gained `sanitize(String value, char replacement, int maxLength, String truncationMarker)` in PLAN-22 (#863, merge `38aefc0a`); `JweDecryptor` and `JwkKey` call it as `sanitize(value, '?', limit, "...")`. Deliverable 6 reuses that overload for its call sites instead of writing a fourth inline copy, and re-reads the file at outline — read in the merged diff
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: ErrorTextSanitizer.sanitize(String,char,int,String) at ~131; JweDecryptor:328 and JwkKey:174 call it
- OBSERVED: the 400-line test budget is not stated in `AGENTS.md` or any versioned doc; `JweDecryptorTest` (1020 lines) and `AccessTokenCacheTest` (1000) are over it after PLAN-22 and 52 test classes in the tree exceed 400 lines (17 exceed 600, three exceed 1000: `DpopProofValidatorTest` 1375, `HttpJwksLoaderFailureTest` 1161, `IssuerConfigResolverTest` 1023). CONTRADICTED 2026-11-06 in the count of larger classes (this label first said about a dozen) and re-scoped. The split decision at emit time also settles, with the operator, whether the budget is real (Watch in `epic.md`) — read at `38aefc0a`
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: HEAD: 52 test classes over 400 lines, 18 over 600, four at or over 1000 (DpopProofValidatorTest 1407 re-read, HttpJwksLoaderFailureTest 1161, IssuerConfigResolverTest 1023, JweDecryptorTest 1020; AccessTokenCacheTest is exactly 1000); no 400 budget in AGENTS.md
- OBSERVED: PLAN-16 (#868, merge `306e6053`) changed `TokenEndpointClient.java` (+62: `clientAuthenticationFailed`, allow-list of 4xx `invalid_client` or 401 `unauthorized_client`), added `ClientAuthenticationFailedException.java` and edited `CredentialRejectedException.java` Javadoc; this spec's HYPOTHESIS on `TokenEndpointClient.java` (response `Content-Type`, quoted `expires_in`) re-reads the file at outline — read in the merged diff
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: git show --stat 306e6053: TokenEndpointClient +62, ClientAuthenticationFailedException added, CredentialRejectedException +7
- OBSERVED: no weak IT assertion shape remains in `token-sheriff-quarkus-integration-tests/src/test` (grep for `assertThrows(RuntimeException|Exception|Throwable.class` and `200 || 302` style predicates returns nothing) — read at `306e6053`; PLAN-16 lesson 002 therefore needs no project sweep
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: no weak assertThrows(RuntimeException|Exception|Throwable) or 200||302 predicate in the IT sources
- OBSERVED: PLAN-15 (#867, merge `262369d6`, 23 files) added `ArrayIndexOutOfBoundsException` to the catch of `DiscoveryResolver` (also now sanitising its logged message), `ParClient`, `UserInfoClient`, `DpopProofValidator`, `JwksParser` (both reads, and the `JWKS_INVALID_JSON` message is sanitised), `NonValidatingJwtParser` (two catches) and both transport converters, with tests for the three client sites; the only `dslJson` read not covered in its own method is `MapRepresentation.fromJson`, covered by its two callers — read in the merged diff and a `git grep` at `262369d6`
  - verdict: contradicted | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: yes | evidence: AIOOBE catches present at the listed sites, but MapRepresentation.fromJson is not the only uncovered read: NonValidatingJwtParser.decodeJwtHeader:535 and decodePayload:558-560 are covered by callers, TokenEndpointClient:555,601 by a broad RuntimeException catch; the guard needs a wider exemption list and a rule for the broad catch
- OBSERVED (read 2026-11-06 at `90f8474a`, the PLAN-24 merge): `MapRepresentation.fromJson` filters null-valued entries (`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/MapRepresentation.java` lines 108-115, from #511), so an explicit JSON `null` claim never reaches `TokenBuilder.extractClaims` or a mapper; `ClaimName.CLIENT_ID` (line 179) and `AUTHORIZED_PARTY` (line 166) use `StrictStringMapper`, while `SUBJECT` (line 52), `ISSUER` (line 44) and `TOKEN_ID` (line 95) use `IdentityMapper`; `TokenLifecycleManager` (around line 1273) compares access-token and ID-token subjects as strings
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: MapRepresentation.java:109-116 filters null values; ClaimName CLIENT_ID:179 and AZP:166 use StrictStringMapper; SUBJECT:52, ISSUER:44, TOKEN_ID:95 use IdentityMapper; TokenLifecycleManager:1272-1273 compares Optional<String>
- OBSERVED: PLAN-15 left TV-4 open by operator decision (the three enum tests restored, files identical to `main`); trivial enum tests stay as a project convention, no item here
  - verdict: unverifiable | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: operator decision on TV-4; enum-test files not re-checked
- OBSERVED: at `af19b9c2` (the PLAN-18 merge) `EgressPolicy` is referenced by main code only in `HttpWellKnownResolver`, `HttpJwksLoader` (plus their config types, `TransportException`, `TransportLogMessages` and the Quarkus `IssuerConfigResolver`), never by a client class; while `doc/commons/requirements.adoc` lines 81-85, `doc/commons/specification/transport.adoc` lines 127-132 and `doc/commons/threat-model.adoc` lines 43-48 still say every IdP-advertised URL, `userinfo` included, is host allow-listed; no client test imports the shared `RevocationDispatcher`, `IntrospectionDispatcher` or `EndSessionDispatcher`, and `doc/client/specification/test-strategy.adoc` lines 128-132 and 233-235 and `step-up-and-logout.adoc` lines 112-114 still credit them (item 7(j))
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: EgressPolicy main references only in HttpWellKnownResolver, HttpJwksLoader and their config types; no client test imports the shared dispatchers; cited doc ranges unchanged by PLAN-19
- OBSERVED: at `d0fa0c7f` (the PLAN-17 merge) with JWE configured `TokenStringValidator` (lines 65-67, 96) admits a token string up to `maxEncryptedTokenSize` (default 32768) while `NonValidatingJwtParser.decodeInternal` (lines 294-295) rejects anything above the parser's `maxTokenSize` (default 8192) with `IllegalArgumentException`, which `RefreshTokenValidationPipeline` (line 100) and `BearerTokenProducer` (line 252) do not catch, and no test pairs `maxEncryptedTokenSize` with an oversized token (item 7(k))
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: re-read at d1af3ebb: TokenStringValidator:65-67,96; NonValidatingJwtParser:294-295; ParserConfig:85; JweDecryptionConfig:51; RefreshTokenValidationPipeline:100; BearerTokenProducer:252; no test names maxEncryptedTokenSize
- OBSERVED (read 2026-11-07 at `d1af3ebb`): no `invalid_scope` string and no scope-refusal exception for a token-endpoint refusal exists in `token-sheriff-client/src/main` or `doc/client` (`git grep` for `invalid_scope` and `ScopeRejected`); `TokenEndpointClient` recognises `invalid_grant` (line 87), `invalid_client` (line 90) and `unauthorized_client` (line 96); `RedeemedScopeRefusalException` exists in the `flow` package for a different case (item 8)
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: git grep at d1af3ebb finds no invalid_scope and no ScopeRejected in token-sheriff-client/src/main or doc/client; TokenEndpointClient:87,90,96 recognise invalid_grant, invalid_client, unauthorized_client
- OBSERVED (read 2026-11-07 at `d1af3ebb`): `doc/commons/requirements.adoc` lines 51-52 (COMMONS-2), `doc/commons/specification/transport.adoc` line 54 and `doc/commons/threat-model.adoc` line 127 (T-TLS) say plain HTTP is rejected without the `allowInsecureHttp` opt-in that `doc/validation/security-reference.adoc` line 52 and `transport.adoc` lines 64, 97 and 101 document (items 7(j)(3) and 7(l)(1))
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: re-read: commons/requirements.adoc:51-52, transport.adoc:54, commons/threat-model.adoc:127 state plain HTTP rejected, no opt-in; security-reference.adoc:52 documents it
- OBSERVED (read 2026-11-07 at `d1af3ebb`): `RefreshFailureClassification.java` lines 32 and 78 and `RefreshFlow.java` lines 87 and 277-278 describe a `PRE_REDEMPTION` token as "untouched and still valid / usable", which ADR-0010 now qualifies (item 7(l)(2)); `doc/adr/0010-…` line 187 says a server error or connection failure means "the server never judged the credential" (item 7(l)(3))
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: re-read: RefreshFailureClassification.java:32,78 and RefreshFlow.java:87,277-278 say untouched and still valid; ADR-0010:187 says the server never judged the credential
- OBSERVED (read 2026-11-07, Sonar REST with `branch=main` and `resolved=false`): `cuioss_TokenSheriff` on `main` has 0 unresolved issues, 0 hotspots, quality gate OK (6 of 6 conditions), coverage 93.2, analysis revision `d120ad0f`
  - verdict: corroborated | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: Sonar REST branch=main resolved=false returns 0; gate-status OK 6 of 6; hotspots 0; measures bugs 0 vulnerabilities 0 code_smells 0 coverage 93.2 at analysis d120ad0f
- HYPOTHESIS: the recurring `e2e-tests` teardown hang is caused by a step in this repository's integration scripts rather than by the reusable workflow in `cuioss-organization` — confirm/refute from the logs of the cancelled and the ejected runs (verify-at-outline, item 9)
  - verdict: unverifiable | checked_at: d1af3ebb | by: quality-report/analyze | rescoped: n/a | evidence: needs the logs of the cancelled and the ejected e2e-tests runs; not read here

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
- OBSERVED: `doc/commons/requirements.adoc` (item 7(j), PLAN-18 follow-up: COMMONS-3 lines 81-85 and COMMONS-2 lines 51-53)
- OBSERVED: `doc/commons/specification/transport.adoc` (item 7(j): lines 53-55, 127-132, plus the #877 text at 45-47 and 178-180)
- OBSERVED: `doc/commons/threat-model.adoc` (item 7(j): T-SSRF lines 43-48 and 125)
- OBSERVED: `doc/commons/README.adoc` (item 7(j): the "shared primitives" wording at lines 22-23)
- OBSERVED: `doc/README.adoc` (item 7(j): the hub line at line 30)
- OBSERVED: `doc/client/README.adoc` (item 7(j): the "Delegated" bullet at lines 29-31)
- OBSERVED: `doc/client/requirements.adoc` (item 7(j): the dispatcher list at line 519)
- OBSERVED: `doc/client/specification/test-strategy.adoc` (item 7(j): lines 128-132 and 233-235)
- OBSERVED: `doc/client/specification/step-up-and-logout.adoc` (item 7(j): lines 112-114)
- OBSERVED: `token-sheriff-client/README.adoc` (item 7(j): lines 30-31)
- OBSERVED: `token-sheriff-client/pom.xml` (item 7(j): the `<description>` text only)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/package-info.java` (item 7(j): lines 35-37, comment text only)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/RevocationClientTest.java` (verify-at-outline — only if the operator wants the shared revocation dispatcher driven by the client tests, item 7(j)(2))
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java` (verify-at-outline — only if the operator wants a host-address egress policy on the client back channel, item 7(j)(1))
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (item 7(k): lines 54, 107, 208-212, 230-234 comment text; line 294-295 the size precondition only if the operator picks a code fix)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenStringValidator.java` (item 7(k): lines 65-67 and 96, read for the effective JWE limit; edited only if the fix aligns the two limits there)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/RefreshTokenValidationPipeline.java` (item 7(k): the catch at line 100, read; edited only if the fix is a wider catch)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java` (item 7(k): comment lines 59 and 376; the catch at line 252 is read, edited only if the fix is a wider catch)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenStatus.java` (item 7(k): comment lines 41, 50, 91)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerToken.java` (item 7(k): comment lines 37-40 and 105-110, the empty `Bearer ` statement)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/validation/DiscoverableTokenValidationRule.java` (item 7(k): comment line 54, the test-coverage sentence)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderFactory.java` (item 7(k): comment lines 30-31)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java` (item 7(k): comment lines 749-750 and 756)
- OBSERVED: `doc/validation/threat-model.adoc` (item 7(k): line 66; PLAN-19 edits this file first)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/TokenStringValidatorJweSizeTest.java` (verify-at-outline — the new test class for item 7(k)(1), named by role; the file does not exist yet)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParserTest.java` (verify-at-outline — a duplicate-header-member test, or soften the sentence, item 7(j)(4))
- OBSERVED: `doc/adr/0010-A_refresh_token_the_authorization_server_declares_invalid_is_a_third_classification_state.adoc` (item 7(l)(3): line 187; also item 8 if the classification is recorded there)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFailureClassification.java` (item 7(l)(2): Javadoc lines 29-33, 77-78, 124; item 8: the classification)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java` (item 7(l)(2): Javadoc lines 87, 277-278; item 8: `classify`)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshRedemption.java` (verify-at-outline — "untouched and still valid" wording, item 7(l)(2))
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` (verify-at-outline — Javadoc lines 82 and 107, item 7(l)(2); the quarantine widening is documented elsewhere, item 10(g))
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CredentialRejectedException.java` (verify-at-outline — Javadoc lines 28-31, item 7(l)(2))
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ClientAuthenticationFailedException.java` (verify-at-outline — Javadoc line 46, item 7(l)(2))
- OBSERVED: `doc/client/specification/token-handling.adoc` (item 7(l)(2): lines 127 and 243; item 8: the new exception)
- OBSERVED: `token-sheriff-quarkus-parent/README.adoc` (item 7(l)(4): line 14)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java` (verify-at-outline — read for the lazy handle resolution, item 7(l)(5); edited only if a defect is found)
- HYPOTHESIS: `doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc` (verify-at-outline — soften the `@Alternative` sentence if a run does not bear it out, item 7(l)(5))
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ScopeRejectedException.java` (verify-at-outline — a NEW public exception for item 8; name not fixed, `RedeemedScopeRefusalException` in the same package is read first)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFailureClassificationTest.java` (verify-at-outline — classification rows for item 8)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClientTest.java` (verify-at-outline — `invalid_scope` rows for item 8)
- HYPOTHESIS: `doc/client/threat-model.adoc` (verify-at-outline — item 8 and item 10(g))
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/stop-integration-container.sh` (verify-at-outline — only if the e2e teardown hang is found here, item 9)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/lib-docker-compose.sh` (verify-at-outline — only if the e2e teardown hang is found here, item 9)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshAdversarialTest.java` (verify-at-outline — stale comment, item 10(a))
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/AccessTokenValidationPipelineTest.java` (verify-at-outline — skew-with-max-age row, item 10(b))
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorStartupInitializerTest.java` (verify-at-outline — proxy dereference, item 10(c))
- HYPOTHESIS: `doc/validation/specification/token-decryption.adoc` (verify-at-outline — line 200 `kty` rejection, item 10(d))
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenStoreTest.java` (verify-at-outline — throwing `retrieve`, item 10(f))
- HYPOTHESIS: `doc/resources/diagrams/client-auth-code-pkce.svg` (verify-at-outline — render and check, item 10(h))

## Dependencies and Sequencing

- Depends on: nothing further. PLAN-01 to PLAN-24 have all merged (PLAN-19 last: #879, `d1af3ebb`, 2026-11-06); this plan is the epic's only remaining one and runs alone. (Earlier text: PLAN-15, PLAN-16, PLAN-22 and PLAN-24 merged first (PLAN-13 and PLAN-14 have merged: #861 and #862; their landings changed `DiscoveryResolver.java`, `TokenEndpointClient.java`, `HttpJwksLoader.java`, `ParserConfig.java` and the converters, so those are re-read at outline). PLAN-14 declares `DiscoveryResolver.java` and the client tests; PLAN-15 and PLAN-22 declare validation files this plan touches (`SignatureVerificationUtilTest.java` and `JweDecryptor.java` among them, verify at outline); PLAN-16 and PLAN-17 touch the Quarkus integration tests and `BearerAuth.java`. Run it LAST, after PLAN-13 to PLAN-24, so it sees their final trees and the residuals they leave.)
- Overlaps with: nothing running (2026-11-07: no other plan is staged, launched or running; the corpus gate showed 10 shared documentation files with PLAN-19 until it merged).
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
