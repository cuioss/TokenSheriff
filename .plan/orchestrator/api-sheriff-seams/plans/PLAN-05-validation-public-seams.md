# PLAN-05: Validation Public Seams

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-05-validation-public-seams.md` and is queued as one row file, `queue/PLAN-05.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Publish four seams in `token-sheriff-validation` (and, for one of them, `token-sheriff-validation-quarkus`)
that API Sheriff currently works around by re-assembling TokenSheriff internals or re-parsing tokens: a
public signature-only verification entry on `TokenValidator`; a native-JSON claim accessor on token
content; a way for an observability probe to see the issuers of a caller-built `TokenValidator`; and,
conditional on an operator decision taken at outline, a back-channel logout-token validator with a replay
guard (or a recorded declaration that TokenSheriff leaves it to the consuming application). Everything is
additive and opt-in; default behaviour of `createAccessToken`, `createIdToken`, `createRefreshToken` and of
the shipped health probes is proven unchanged. Target release 0.9.10. The matching API Sheriff deletions are
a separate API Sheriff plan and are not part of this plan.

## Deliverables

Consumer context (what API Sheriff needs, so the TokenSheriff design can be judged against it): API Sheriff
verifies a back-channel logout token with its own `SignatureOnlyTokenVerifier`, which hand-assembles the
internal `NonValidatingJwtParser`, `TokenHeaderValidator`, `TokenSignatureValidator`,
`SignatureTemplateManager` and `TokenBuilder` over its own issuer map; it re-parses `getRawToken()` of a
validated ID token to obtain native JSON claim types; and it carries its own per-issuer key-set status type
and readiness check because TokenSheriff's probes report nothing for a validator the application built
itself. The consumer's observable needs are listed per deliverable below.

1. **Public signature-only verification on `TokenValidator`.** Add a public method (working name
   `verifySignature(String rawToken)`; final name and result shape are an operator decision) that runs the
   engine's own pre-pipeline and pipeline stages up to and including signature verification and token
   mapping, and stops before the claim-validation stage. It returns the mapped claims and the `typ` value of
   the protected header, and carries no claim verdict. Consumer-visible fields and edges, one verification
   criterion each:
   - Reuses `TokenStringValidator` (null, blank, size limits): a null, blank and over-size input are each
     refused with the existing event types, as on the three `create*` methods.
   - Reuses `IssuerConfigCache.resolveConfig` health gating: an unknown issuer, a disabled issuer and an
     issuer whose JWKS loader is not `OK` are each refused with `NO_ISSUER_CONFIG`; a loader that left `OK`
     after the initial load (opt-in max staleness) is refused.
   - Header posture is that of the ID-token path: unsupported `alg`, missing `alg`, missing `kid`, embedded
     `jwk` are refused. Whether the per-issuer `expectedTokenType` check applies to this entry is an
     operator decision (OD-2); the criterion is whichever way it is decided, asserted with an issuer that
     configures `expectedTokenType` and a token whose `typ` differs.
   - Signature: a tampered payload, a tampered signature, an `alg` the key does not support and an unknown
     `kid` are each refused (`KEY_NOT_FOUND`, `UNSUPPORTED_ALGORITHM`, signature-invalid events).
   - No claim verdict: an expired token, a token with the wrong `aud`, a token with no `sub` and a token with
     no `azp` all verify when the signature is valid, and the result does not claim otherwise (negative
     control: the same tokens are refused by `createIdToken`).
   - Result carries `typ`: present, absent, and mixed-case values are returned verbatim; the value is covered
     by the signature.
   - Result type cannot be mistaken for a validated token: `TokenContent` is sealed to `BaseTokenContent`,
     which is sealed to `AccessTokenContent` and `IdTokenContent`, so returning an `IdTokenContent` would
     present an unvalidated token as a validated ID token. The result type and its Javadoc must make the
     missing claim verdict unmistakable (OD-1).
   - JWE: with `JweDecryptionConfig` set, a 5-part token is handled exactly as on `createIdToken`
     (decrypt, then verify the inner JWS); without it a 5-part token is refused.
   - Accounting: which security events and metrics the entry records (the ID-token path records only
     `ID_TOKEN_CREATED` and no timing; the access-token path records `COMPLETE_VALIDATION`) is an operator
     decision (OD-3); whichever is chosen is asserted, and a refusal increments exactly one event as on the
     other entries.
   - Cache: the access-token cache is neither read nor written (assert cache size unchanged).
   Security tests: `alg=none`, HMAC `alg`, `alg` confusion against an RSA key, embedded `jwk`, `jku`/`x5u`
   headers ignored, a 5-part token without JWE configured, a token naming an issuer that is configured but
   whose loader is `UNDEFINED`, oversized token, and a flood of distinct unknown issuers (no unbounded growth
   of any per-issuer structure).

2. **Native-JSON claim accessor.** Give callers the claims of a validated (and of a signature-verified)
   token with their native JSON types: object, array, number, boolean, string; today an object or array
   claim reaches `ClaimValue` as `Object#toString()` output. Two shapes are candidates (OD-4): an accessor
   such as `TokenContent#getRawClaims()` returning an immutable `Map<String, Object>`, or an additional
   claim value type. Consumer-visible fields and edges, one criterion each:
   - Nested object and array claims keep their JSON type and element order of arrays; nested objects are
     immutable all the way down (a mutation attempt throws).
   - A top-level JSON `null` is omitted exactly as `MapRepresentation.fromJson` omits it; a `null` nested in
     an object or array is kept as parsed (the documented `security-reference.adoc` behaviour is preserved).
   - Large numbers, negative numbers, fractional numbers and booleans keep a distinguishable type; a number
     that exceeds the `ParserConfig` limits is refused at parse time as today.
   - Bounds: the accessor never exceeds the `ParserConfig` limits (payload size, string length, nesting
     depth, array size); a hostile nesting-depth payload is refused, never partially returned.
   - Memory: the choice between retaining the parsed map in the token and re-parsing `getRawToken()` on
     demand is decided on the cached-token memory cost (`AccessTokenCache` is bounded by entry count, not
     bytes); the criterion is a test showing the access-token cache entry footprint does not grow when the
     accessor is not used.
   - JWE: for a token that arrived as a JWE the accessor reads the inner JWS payload, not the encrypted
     form.
   - Serialization: `MinimalTokenContent` is `Serializable`; any retained structure round-trips through
     Java serialization or is excluded and re-derivable.
   - Default behaviour: `ClaimValue`, `ClaimValueType` consumers and the unknown-claim `toString()` fallback
     in `TokenBuilder` are unchanged (existing tests in `TokenBuilderTest` and `TokenBuilderExplicitNullTest`
     pass unmodified).

3. **Issuer visibility for a caller-built `TokenValidator`.** Today the validator exposes neither its issuer
   configurations nor their loader status, so `ObservedValidatorResolver` returns an empty issuer list for an
   externally produced validator and `JwksEndpointHealthCheck` short-circuits to `UP` with
   `checkedEndpoints=0`. Add a read-only accessor on `TokenValidator` (OD-5: `getIssuerConfigs()` returning
   the enabled `IssuerConfig` list, or a narrower immutable status view with issuer identifier, JWKS type and
   `LoaderStatus` only) and let the observability resolver use it for the external-validator outcome.
   Consumer-visible fields and edges, one criterion each:
   - The accessor returns the enabled issuers in configuration order, immutable, and an empty list never
     arises (the constructor refuses zero enabled issuers).
   - Ownership: the accessor does not widen ownership or lifecycle: callers cannot close a loader through it
     (ADR-0016 keeps `TokenValidator.close()` as the only owner of loader shutdown); asserted by test.
   - Status is read non-fetching: reading the status performs no JWKS request (assert with a counting
     mock server).
   - Disclosure: the existing probe writes the issuer identifier into the health payload under
     `issuer.N.url`; API Sheriff's own readiness deliberately reports counts and a verdict only. Whether the
     probe gains a non-disclosing mode is an operator decision (OD-6); the criterion is whichever way it is
     decided, asserted on the payload.
   - Default behaviour proof: for the `PROPERTY_CONFIGURED` outcome the probe output is byte-identical to
     today. For the `EXTERNAL_VALIDATOR` outcome the probe today reports `UP`; reporting real status can turn
     a readiness probe `DOWN` for an embedder. So the new external-validator reporting is opt-in (a
     configuration key or a CDI-level switch; the name is chosen at outline) and the default is asserted
     unchanged by `JwksEndpointHealthCheckTest` and `ObservedValidatorResolverTest` cases that exist today
     (OD-7 records whether the operator would rather make it the default).
   - Multiple foreign validators and `NOT_CONFIGURED` still yield `checkedEndpoints=0` as today.

4. **Back-channel logout token (conditional; gated on operator decision OD-8 at outline).** Exactly one
   branch is implemented; the plan does not start this deliverable before the decision is recorded.
   - **Branch A, TokenSheriff publishes it.** A logout-token validator in `token-sheriff-validation`
     (OpenID Connect Back-Channel Logout 1.0 §2.4 and §2.6): signature through deliverable 1; `typ` header
     `logout+jwt` when present (case-insensitive, with or without the `application/` prefix, refused when
     it is some other type; whether an absent `typ` is accepted is part of OD-8); `iss` equals the expected
     issuer; `aud` contains the expected audience; `iat` present and inside a symmetric freshness window;
     `exp` present and not past; `events` is a JSON object holding the key
     `http://schemas.openid.net/event/backchannel-logout` (read through deliverable 2, never by string
     scan); `nonce` absent; `sub` and/or `sid` present; `jti` present. The result names the failing check as
     a bounded enum, never as free text. Plus a bounded replay guard keyed by `jti` that fails closed when
     full (a value inside its window is never evicted to make room), stores a digest rather than the
     value, and is thread-safe. Verification criteria: one test per check above including the boundary
     (`iat` exactly at the window edge, `exp` exactly now), a `sid`-only token accepted, a `sub`-only token
     accepted, a token with neither refused, an `events` value that is a string, an array, or an object
     lacking the key refused, an `events` string that merely contains the URI refused, a `jti` replayed
     inside its window refused, a replay guard at capacity refusing a new `jti` and never evicting a live
     one, and concurrency tests on the guard. New log records, if any, follow the Standing Constraints.
   - **Branch B, out of scope.** TokenSheriff records that back-channel logout-token validation and replay
     guarding stay with the consuming application. Deliverable is documentation only: the boundary is
     stated in the validation requirements and in an ADR, `doc/client/requirements.adoc` (CLIENT-13) and
     `doc/client/specification/step-up-and-logout.adoc` (§ Boundary: receivers are the BFF's concern) are
     cross-referenced, and deliverables 1 and 2 are documented as the seams such a receiver composes. No
     code. Verification: documentation links resolve; `DocumentedPackagePatternTest` and the doc checks in
     the pre-commit build pass.

5. **Documentation, traceability and release notes for deliverables 1 to 4.** Requirement entries in
   `doc/validation/requirements.adoc` (ids: next free at write time, not assumed), architecture text in
   `doc/validation/architecture.adoc`, the TokenValidator signature block in
   `token-sheriff-validation/doc/api-reference.adoc`, the security notes in
   `doc/validation/security-reference.adoc` (the signature-only entry deliberately skips claim checks; the
   warning is stated once and linked), the threat-model rows in `doc/validation/threat-model.adoc` for the
   misuse of a signature-only result as an authentication decision, `doc/developer/test-impl-mapping.adoc`
   rows for every new test class, and an ADR in `doc/adr/` (number: next free at write time) recording the
   decisions of OD-1 to OD-8 with rationale. Verification: every new public type and method has Javadoc with
   `@since`, thread-safety note and an example; `check-javadocs-pre-commit` passes; every new test class is
   listed in `doc/developer/test-impl-mapping.adoc`.

**Operator decisions at outline** (the plan records each decision in the ADR of deliverable 5 before
scoping on it):

- OD-1: result type of the signature-only entry: a new type outside the sealed `TokenContent` hierarchy
  (a record of mapped claims, raw token and `typ`), a new permitted subtype added to the sealed hierarchy, or
  returning `IdTokenContent`. Constraint from the code: the hierarchy is sealed.
- OD-2: whether the signature-only entry applies the per-issuer `expectedTokenType` header check (an issuer
  configuring `at+jwt` would otherwise refuse every `logout+jwt` token) or skips it and returns `typ` for
  the caller to judge.
- OD-3: security events and metrics recorded by the signature-only entry (a new `EventType`, reuse of an
  existing one, and whether a `MeasurementType` is used).
- OD-4: native-JSON claim shape: `TokenContent#getRawClaims()` versus an additional claim value type versus
  re-parse helper; and retained-versus-lazy re-parse (OD driven by cache memory).
- OD-5: issuer visibility shape: `getIssuerConfigs()` versus a narrower immutable status view, versus letting
  an external validator publish its issuer configurations to the observability resolver.
- OD-6: whether the JWKS probe gains a non-disclosing mode (counts and verdict only) for the issuer
  identifier it prints.
- OD-7: whether external-validator reporting is opt-in (this spec's default) or becomes the default
  behaviour at the cost of a visible change for embedders.
- OD-8: logout-token scope: Branch A (publish a validator and replay guard) or Branch B (declare out of
  scope), and for Branch A whether a logout token without a `typ` header is accepted.

## Standing Constraints

- Build and gate: Maven through the wrapper only (`./mvnw`). Before any commit that touches build inputs:
  `./mvnw -Ppre-commit clean verify` (it auto-fixes files in place; read `git status` afterwards and commit
  what it changed), then `./mvnw clean install`, then
  `./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am`.
  Never gate on `test-compile` or `test`: the `generators` classifier jar exists only from `package`.
- Pre-1.0 rules (AGENTS.md): no `@Deprecated`, no transitional comments, break APIs freely, design for the
  final post-1.0 shape. These plans are nonetheless additive: new members only, and the default behaviour
  of every existing public entry is asserted unchanged by tests that exist today and pass unmodified.
- Everything new is opt-in or additive. A change that alters default behaviour of an existing public
  method, probe or configuration key is a defect of this plan unless an operator decision above says
  otherwise, and then it is named in the ADR and in the release notes.
- Logging: `de.cuioss.tools.logging.CuiLogger`, `%s` only, `LogRecord` for INFO/WARN/ERROR with ranges INFO
  001-099, WARN 100-199, ERROR 200-299, every record documented in `doc/LogMessages.adoc`. New identifiers
  are the next free value at write time in the target message class (`JWTValidationLogMessages`), checked
  against `LogMessageIdentifierUniquenessTest`; no identifier is assumed in this spec. The exception
  parameter comes first. Remote text (a `kid`, an `iss`, a `typ`, a claim value, an exception message) reaches
  a log line or a caller-visible message only through `ErrorTextSanitizer` (ADR-0022), enforced by
  `LogRecordSanitizationGuardTest`.
- OpenRewrite markers are actual defects: never commit one. Narrow catches against declared throwers
  (AGENTS.md § `InvalidExceptionUsageRecipe`); do not suppress.
- Javadoc: every public and protected type and method, `@since`, thread-safety note, usage example for
  complex types, `package-info.java` for any new package, `{@link}` references.
- Tests: JUnit 5, AAA, CUI Test Generator for data, parameterised tests for three or more similar variants,
  test classes at or under 500 lines (guideline). Signal integrity per
  `doc/client/specification/test-strategy.adoc` § Signal integrity: every new negative test is paired with a
  positive control, and a verdict test asserts identity (which check refused) rather than a proxy.
  Coverage: the opt-in `coverage` profile rule (80 percent instruction and branch) must hold for new code.
- Documentation: AsciiDoc, `xref:` syntax, blank line before lists, requirement ids and the ADR number are
  the next free value at write time; every new test class is added to `doc/developer/test-impl-mapping.adoc`.
- Native image: `NativeImageMetadataTest` pins the shipped `reflect-config.json` exactly. A new type that
  needs reflection is registered deliberately and the pinned list updated in the same change; a new static
  holding a thread pool or random source is initialised at run time, not at image build time.
- Dependencies: none added without explicit approval.
- Sonar: AGENTS.md names the SonarCloud new-code gate in CI as the coverage authority and the repository
  carries `java:S...` suppressions with rationale in code. An open Sonar issue on this plan's code is fixed
  in a pushed commit, or suppressed in code with a stated rationale; none is dismissed silently. The Sonar
  transition procedure of the plan template is not documented in this repository, so it is not restated.
- Git: feature branch, PR against `main` via `gh`, merge through the queue only after CI is green and every
  reviewer comment is answered and resolved (CLAUDE.md § Git Workflow).

## Claim Labels

- OBSERVED: `TokenValidator` exposes `createAccessToken`, `createIdToken`, `createRefreshToken`, `close()` and Lombok getters for `securityEventCounter` and `performanceMonitor` only; there is no public accessor for issuer configurations, loader status or a signature-only entry — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § `createAccessToken`, `securityEventCounter`, `performanceMonitor`
- OBSERVED: asserted absence, TokenSheriff has no public `getIssuerConfigs`; a search of the whole tree for `getIssuerConfigs` and `issuerConfigs()` finds only `ObservedValidatorResolver.observedIssuerConfigs` and CDI `Instance<List<IssuerConfig>>` handles — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java` § `observedIssuerConfigs`
- OBSERVED: `IdTokenValidationPipeline.validate` runs seven steps: parse, extract `iss`, `issuerConfigCache.resolveConfig`, header validate, signature validate, token build, claim validate; the finding's `~:116-160` range is correct — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/IdTokenValidationPipeline.java` § `validate`
- OBSERVED: the pipeline classes API Sheriff assembles are documented as internal: `pipeline/package-info.java:19` and `TokenSignatureValidator:61` carry the "internal ... not part of the public API" text (also `IdTokenValidationPipeline:62`, `TokenBuilder:52`, `NonValidatingJwtParser:129`, `SignatureTemplateManager:52`, `TokenHeaderValidator:44`) — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/package-info.java` § package description
- OBSERVED: `IssuerConfigCache.resolveConfig` admits only a config whose loader reports `LoaderStatus.OK` at call time on every tier, waits up to 5 seconds for an in-flight initial load, and re-admits a recovered issuer; its constructor is package-private so a caller outside the package cannot build one — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java` § `resolveConfig`, constructor
- OBSERVED: API Sheriff's `SignatureOnlyTokenVerifier` builds its own per-issuer map of `TokenHeaderValidator`, `TokenSignatureValidator`, `SignatureTemplateManager`, `TokenBuilder`, does not call `TokenStringValidator` or `IssuerConfigCache`, returns a `VerifiedToken(TokenContent, @Nullable String headerType)` and drives `createIdToken` on the builder; its only production consumer is the back-channel logout seam wired in `BffRuntimeProducer` — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/SignatureOnlyTokenVerifier.java` § `verify`, and `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/quarkus/BffRuntimeProducer.java` § back-channel receiver wiring (line ~758)
- OBSERVED: the finding says the AS copy skips "TokenStringValidator and metrics"; metrics are only partly relevant: `createIdToken` records no timing and only `ID_TOKEN_CREATED`, while `createAccessToken` records `COMPLETE_VALIDATION`; the existing `MeasurementType` has no signature-only member — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § `createIdToken`, `createAccessToken`, and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/metrics/MeasurementType.java` § enum
- OBSERVED: `TokenContent` is `sealed ... permits BaseTokenContent`; `BaseTokenContent` is `abstract sealed ... permits AccessTokenContent, IdTokenContent`; `TokenType` has ACCESS_TOKEN, ID_TOKEN, REFRESH_TOKEN with mandatory-claim sets — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/TokenContent.java` § interface, and `.../domain/token/BaseTokenContent.java` § class
- OBSERVED: `TokenHeaderValidator.validate` also applies the per-issuer `expectedTokenType` check (`validateTokenType`), refusing a `typ` that differs case-insensitively; API Sheriff main code configures no `expectedTokenType`, so its copy is not affected today — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenHeaderValidator.java` § `validateTokenType`, and an absent-match search of `api-sheriff/src/main/java` for `expectedTokenType`
- OBSERVED: `ClaimValueType` has STRING, STRING_LIST and DATETIME only (the finding said "STRING and STRING_LIST (+datetime)", which is the same set); an unknown claim becomes a plain string via `o.toString()` in `TokenBuilder.mapUnknownClaim` (finding `~:210-219`, actual `:212`); `ClaimName` has no `sid`, `events` or `nonce` entry — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java` § `mapUnknownClaim`, and `.../domain/claim/ClaimValueType.java` § enum, `.../domain/claim/ClaimName.java` § enum
- OBSERVED: `BaseTokenContent` keeps `claims`, `rawToken`, `tokenType` and nothing of the parsed `MapRepresentation`; `MapRepresentation` is a public `Serializable` record that drops top-level `null` members when parsing — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/BaseTokenContent.java` § fields, and `.../json/MapRepresentation.java` § `fromJson`
- OBSERVED: API Sheriff re-parses `getRawToken()` in `IdTokenClaimProjection.project` (used at `BffRuntimeProducer` line ~727) and its logout validator recovers the `events` object by scanning its Java `Map.toString()` or JSON text because TokenSheriff stringifies object claims — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/IdTokenClaimProjection.java` § `project`, and `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/logout/LogoutTokenValidator.java` § `hasBackchannelLogoutEventMember`
- OBSERVED: `ObservedValidatorResolver` returns `List.of()` issuer configs and no parser config for `Outcome.EXTERNAL_VALIDATOR`, and its class text says the core `TokenValidator` exposes neither (finding `~:171-181` and `~:67-69`: the doc is at `:67-69`, the method `observedIssuerConfigs` at `:173`, the external outcome at `:216`) — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java` § `observedIssuerConfigs`, `doResolve`
- OBSERVED: `JwksEndpointHealthCheck.performHealthCheck` returns `UP` with `checkedEndpoints=0` when the observed issuer list is empty, and otherwise writes the issuer identifier under `issuer.N.url` plus `jwksType` and a `UP`/`DOWN` status per issuer; finding `~:97-117` is approximate (`performHealthCheck` is at `:94`) — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheck.java` § `performHealthCheck`, `EndpointResult.addToResponse`
- OBSERVED: `IssuerConfig` already implements `LoadingStatusProvider` (`getLoaderStatus()` delegates to its JWKS loader) and exposes `getJwksLoader()`; API Sheriff's `IssuerKeySetStatus` is a non-fetching view that deliberately omits issuer URLs and hostnames, and API Sheriff excludes the TokenSheriff probes by `quarkus.arc.exclude-types=de.cuioss.sheriff.token.quarkus.health.*` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java` § `getLoaderStatus`, `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/IssuerKeySetStatus.java` § class text, `api-sheriff/src/main/resources/application.properties` § `quarkus.arc.exclude-types` (line 343; the finding said `~:341`)
- OBSERVED: asserted absence, TokenSheriff has no logout-token handling in production code: a case-insensitive search for `logout.?token`, `backchannel`, `logout+jwt` over all `*.java` and `*.adoc` finds only the RP-initiated-logout client code (`LOGOUT_TOKENS_CLEARED`, which clears held tokens), the unrelated `backChannelOrigin` mapping (a DPoP back-channel URL origin, not logout), and documentation that assigns receivers to the BFF — read at `doc/client/requirements.adoc` § `CLIENT-13`, `doc/client/specification/step-up-and-logout.adoc` § Boundary, `doc/client/threat-model.adoc`
- OBSERVED: the finding cites `doc/client/requirements.adoc ~:245-246 CLIENT-19` for the receiver boundary; the sentence is in CLIENT-13 (lines 245-246), and CLIENT-19 is the "BFF groundwork" requirement that the step-up doc cites as owner — read at `doc/client/requirements.adoc` § `CLIENT-13`, `CLIENT-19`
- OBSERVED: API Sheriff's `LogoutTokenReplayGuard` is a bounded map keyed by the SHA-256 digest of `jti`, closed when full, thread-safe by monitor; TokenSheriff has a different replay structure for DPoP (`DpopReplayProtection`, TTL plus size, `Closeable`) that a logout guard could reuse or sit beside — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/logout/LogoutTokenReplayGuard.java` § `admit`, and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopReplayProtection.java` § `checkAndStore`
- HYPOTHESIS: a signature-only entry can be built inside `TokenValidator`'s constructor from the same per-issuer maps the ID-token pipeline already receives (no new construction path), by adding a pipeline stage or a flag-free sibling method on `IdTokenValidationPipeline` — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § constructor and `.../pipeline/IdTokenValidationPipeline.java` § `validate` (verify-at-outline)
- HYPOTHESIS: retaining a parsed claim map in `AccessTokenContent` raises the per-entry footprint of the access-token cache, which is bounded by entry count and not by bytes, so the lazy re-parse of `getRawToken()` is the safer default — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` § class and `.../cache/CachedToken.java` § class (verify-at-outline)
- HYPOTHESIS: with `JweDecryptionConfig` set, `DecodedJwt.rawToken()` is the decrypted inner JWS while `BaseTokenContent.getRawToken()` may hold the original JWE, so a raw-claims accessor must read the decoded payload and not decode `getRawToken()` blindly — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` § `decode` and `.../pipeline/TokenBuilder.java` § `createIdToken` (verify-at-outline)
- HYPOTHESIS: reporting real loader status for the `EXTERNAL_VALIDATOR` outcome can flip an embedder's readiness from `UP` to `DOWN`, hence the opt-in; no existing test pins the current `UP`/`checkedEndpoints=0` result for the external outcome except through `JwksEndpointHealthCheckTest` — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckTest.java` § external-validator cases (verify-at-outline)
- HYPOTHESIS: ADR-0002 (observability beans observe the resolved validator) and ADR-0016 (validator owns and closes its loaders) constrain deliverable 3 and are the ADRs to cite and, if the accessor exposes `IssuerConfig`, to amend — confirm/refute at `doc/adr/0002-Observability_beans_observe_the_resolved_TokenValidator_not_the_issuer_property_namespace.adoc` § Decision and `doc/adr/0016-TokenValidator_owns_and_closes_the_JWKS_loaders_of_its_issuer_configs.adoc` § Decision (verify-at-outline)
- HYPOTHESIS: a logout-token claim set needs a new `TokenType` or a token-type-free validator, because the existing `TokenType` constants carry mandatory-claim sets that a logout token does not satisfy (`sub` optional, `azp` absent) — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenType.java` § constants (verify-at-outline; only relevant to Branch A)
- Verify-first clause: before scoping, the consuming phase reads the current `TokenValidator`, `IdTokenValidationPipeline`, `IssuerConfigCache`, `ObservedValidatorResolver`, `JwksEndpointHealthCheck` and the tests named in Expected Surface at HEAD of the 0.9.10 line and confirms or refutes every HYPOTHESIS above, in particular the sealed-hierarchy consequence for the result type, the `expectedTokenType` interaction, the cache footprint and the JWE raw-token question; a refutation loops back to re-scope this spec rather than being absorbed silently. The operator decisions OD-1 to OD-8 are asked at outline and recorded before any deliverable that depends on them is scoped; Branch A and Branch B of deliverable 4 are never both planned.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`:149 — `TokenValidator` (new public methods)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/IdTokenValidationPipeline.java`:116 — `validate` (shared stages)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java`:174 — `resolveConfig`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenHeaderValidator.java`:213 — `validateTokenType`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenStringValidator.java`:101 — `validate`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java`:212 — `mapUnknownClaim`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/TokenContent.java`:55 — `TokenContent`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/BaseTokenContent.java`:53 — `BaseTokenContent`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/MapRepresentation.java`:54 — `MapRepresentation`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/metrics/MeasurementType.java`:56 — `MeasurementType`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/events/SecurityEventCounter.java`:66 — `EventType`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java`:41 — `PREFIX` (new records only if a deliverable needs them)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java`:390 — `getLoaderStatus`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java`:216 — `doResolve` external outcome
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheck.java`:94 — `performHealthCheck`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorTest.java` — `extended or paired with a new sibling test class`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/IdTokenValidationPipelineTest.java` — `default behaviour must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilderTest.java` — `default behaviour must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/NativeImageMetadataTest.java` — `pinned reflect-config, updated only if a new reflective type is added`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckTest.java` — `default behaviour pinned, external case added`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolverTest.java` — `default behaviour pinned, external case added`
- OBSERVED: `doc/validation/requirements.adoc`:53 — `functional requirements (new entries, ids next free at write time)`
- OBSERVED: `doc/validation/architecture.adoc`:45 — `TokenValidator` section
- OBSERVED: `doc/validation/security-reference.adoc`:1 — `security controls (signature-only warning)`
- OBSERVED: `doc/validation/threat-model.adoc`:1 — `threat rows (misuse of a signature-only result)`
- OBSERVED: `token-sheriff-validation/doc/api-reference.adoc`:11 — `TokenValidator` signature block
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:1 — `test-to-implementation mapping`
- OBSERVED: `doc/LogMessages.adoc`:1 — `log reference (only if new records are added)`
- OBSERVED: `doc/client/requirements.adoc`:239 — `CLIENT-13 (cross-reference, Branch B)`
- OBSERVED: `doc/client/specification/step-up-and-logout.adoc`:150 — `receiver boundary text (cross-reference, Branch B)`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureOnlyValidationPipeline.java` — `new pipeline class, to be created if the entry is not a method on IdTokenValidationPipeline` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/SignatureVerifiedToken.java` — `new result type, name and hierarchy per OD-1, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorVerifySignatureTest.java` — `new test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorVerifySignatureAttackTest.java` — `new adversarial test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/token/RawClaimsTest.java` — `new test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidator.java` — `new, only if OD-8 selects Branch A` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenReplayGuard.java` — `new, only if OD-8 selects Branch A` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/package-info.java` — `new package, only if OD-8 selects Branch A` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidatorTest.java` — `new, only if OD-8 selects Branch A` (verify-at-outline)
- HYPOTHESIS: `doc/adr/` — `one new ADR file recording OD-1 to OD-8, number next free at write time` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none. Within the plan, deliverable 4 Branch A depends on deliverables 1 and 2 (signature verification and the object-valued `events` claim); deliverable 5 closes the plan.
- Overlaps with: PLAN-06 (both touch `token-sheriff-validation`; the file overlap is the shared documents `doc/validation/architecture.adoc`, `doc/validation/requirements.adoc`, `doc/validation/threat-model.adoc`, `token-sheriff-validation/doc/api-reference.adoc`, `doc/developer/test-impl-mapping.adoc`, `doc/LogMessages.adoc`, and `JWTValidationLogMessages` and `SecurityEventCounter` when either plan adds records or event types: the second plan to run takes the next free identifier at its own write time). PLAN-03 (touches `token-sheriff-validation` commons transport, `EgressPolicy`; no file overlap with this plan). PLAN-01 to PLAN-04 are in `token-sheriff-client`; no overlap except documents under `doc/client/` that Branch B cross-references.
- Adjacent to: `token-sheriff-client` token lifecycle and ID-token verification (PLAN-04) which consume `TokenValidator.createIdToken` unchanged and stay untouched; the JWKS loader internals (PLAN-06) which this plan only reads through the existing `LoaderStatus`. API Sheriff's matching deletions (its `SignatureOnlyTokenVerifier`, `IdTokenClaimProjection`, `IssuerKeySetStatus` and readiness parts) are a separate API Sheriff plan (AS PLAN-34) that waits for a released 0.9.10 and is not part of this spec.
- Release and sequencing: target release 0.9.10 (repository at 0.9.10-SNAPSHOT). `parallelization_scope` is 1: plans in this epic run sequentially, so no concurrent plan edits the files above while this one runs. Recommended order within WS-02 is PLAN-05, PLAN-06, PLAN-07; no hard ordering constraint exists between them.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-05-validation-public-seams.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
