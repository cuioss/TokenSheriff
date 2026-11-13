# PLAN-07: Public Helper Hygiene

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-07-public-helper-hygiene.md` and is queued as one row file, `queue/PLAN-07.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Turn four private or duplicated TokenSheriff helpers into one public, framework-neutral implementation each,
so that API Sheriff can delete its own copies and TokenSheriff's own copies stop disagreeing: an RFC 6750
`Bearer` token extractor and an `insufficient_scope` challenge builder (today private to the Quarkus
producer classes, and the extractor and API Sheriff's copy disagree on scheme case); an "escape everything
outside printable ASCII" mode of `ErrorTextSanitizer`; and one bounded response-body handler that can return
bytes and either fail or truncate (removing the duplicate in the client module). This is a low-priority
hygiene plan: every deliverable is additive, any change to TokenSheriff's own default behaviour is named
and decided at outline, and the bounded-body deliverable may be dropped at outline. Target release 0.9.10.
API Sheriff's matching deletions are a separate API Sheriff plan and are not part of this plan.
A sixth deliverable, folded in after PLAN-05 shipped, hardens the back-channel logout validator PLAN-05 added
(a resource-exhaustion defect in `numericDate` and four minor residuals).

## Deliverables

Consumer context: API Sheriff's `AuthenticationStage` builds the `insufficient_scope` challenge by string
concatenation and extracts the bearer token with a case-insensitive scheme match and a `strip()`; its
`RuleSupport.renderForMessage` escapes every character outside printable ASCII to `\uXXXX` and caps at 60
characters; its `UpstreamAssetSource` carries a byte-array body subscriber that truncates and flags at a
cap. Each deliverable below is judged by whether API Sheriff could delete its copy without a behaviour
change it does not want.

1. **Public `Bearer` token extractor.** A framework-neutral public helper that reads the credentials of an
   `Authorization` header value (and, optionally, a header map) per RFC 6750 §2.1 and RFC 9110 §11.1, and is
   used by `BearerTokenProducer` in place of its private `extractBearerTokenFromHeaderMap`. It reports three
   distinct states (no `Bearer` credentials, `Bearer` with an empty token, `Bearer` with a token), because
   the producer maps the second to `INVALID_REQUEST` and the first to "no token". Consumer-visible inputs
   and edges, one verification criterion each:
   - Absent header, empty header value, `null`: no credentials.
   - Another scheme (`Basic abc`), a scheme that merely starts with `Bearer` (`Bearerx abc`), and `Bearer`
     with no following space: no credentials (and with `Cookie` configured the producer's cookie fallback
     still applies, as documented in the `BearerTokenProducer` class text).
   - `Bearer ` and `Bearer` followed by only spaces: present with an empty token (producer: `INVALID_REQUEST`).
   - Scheme case: `Bearer`, `bearer`, `BEARER`, `bEaReR` are the same scheme under RFC 9110; today the
     producer accepts only `Bearer `. Whether the producer adopts the RFC rule is an operator decision
     (OD-1); the helper implements the rule chosen and the criterion asserts all four spellings accordingly.
   - Separator and whitespace: one space, several spaces, a tab, trailing spaces, leading spaces before the
     scheme; the rule chosen (OD-2) is asserted for each; the returned token never carries surrounding
     whitespace unless the rule says so, and a token containing an internal space or any control character
     (CR, LF, NUL, DEL) is refused as invalid rather than passed on.
   - Multiple `Authorization` header values: the first is used (current producer behaviour, asserted).
   - Header-map keys are looked up the way the producer's resolver delivers them (lower-case, per the
     existing comment); a case-variant key is asserted per the chosen rule.
   - Bound: an over-long value is handled in constant extra memory and is not copied twice; the helper does
     not impose the token size limit (that stays with `TokenStringValidator`), and a test shows an input of
     the configured maximum token size plus the scheme passes through unchanged.
   - Default behaviour of the producer: `BearerTokenProducerTest` passes without modification where OD-1 and
     OD-2 keep today's rule; where they change it, every changed expectation is listed in the PR body and
     in the ADR of deliverable 5.

2. **Public `insufficient_scope` challenge builder.** A framework-neutral public builder returning the
   `WWW-Authenticate` header value string for an RFC 6750 §3.1 `insufficient_scope` challenge (it returns a
   string, never a JAX-RS `Response`; the status code and body stay with the caller), used by
   `BearerTokenResponseFactory` in place of its private `buildInsufficientScopeHeader`. Consumer-visible
   fields and edges, one criterion each:
   - Output shape: `Bearer`, optional `realm`, `error="insufficient_scope"`, optional `error_description`,
     optional `scope="..."`; the producer's existing output (`Bearer realm="protected-resource",
     error="insufficient_scope", error_description="...", scope="..."`) is reproduced exactly for the same
     inputs, so adopting the builder is byte-identical for TokenSheriff's own responses; API Sheriff's form
     (no realm, no description) is reachable by omitting the optional parts.
   - Scope set: empty set (no `scope` attribute), single scope, many scopes (deterministic sorted order, so
     the header is stable; the producer's current order for a `Set` is unspecified and its test accepts both
     orders), duplicates collapsed.
   - Quoting: today's `escapeQuotes` replaces only `"`; a value ending in a backslash, or containing
     `\"`, produces a header whose quoted-string ends early (RFC 9110 §5.6.4 requires escaping both `"` and
     `\`). The builder escapes both, and refuses (`IllegalArgumentException`) a value containing CR, LF, NUL,
     any other control character or DEL, so a caller-supplied value can never split the header or add a
     parameter. Whether scope names that do not match the RFC 6749 §3.3 `scope-token` grammar are refused or
     escaped is an operator decision (OD-3); the criterion asserts the chosen rule with a quote, a
     backslash, a space inside a name, a CR/LF, a non-ASCII character and an empty string.
   - Bounds: a maximum number of scopes and a maximum header length are enforced (the client parser
     `InsufficientScopeChallengeParser` refuses more than 64 scope tokens and more than 8192 characters; the
     builder never emits what the sibling parser would refuse), asserted at the boundary and one past it.
   - Round trip: for every accepted input, `InsufficientScopeChallengeParser.parse(builder output)` returns
     the same scope set, realm and description (property-style test over generated inputs); the test lives
     where both classes are on the test classpath.
   - Status code is not part of the helper: the producer returns 401 for a scope violation (documented there
     as an RFC 9470 step-up design choice) while API Sheriff answers 403; the builder does not decide, and
     the criterion is that no status appears in its API.
   - Default behaviour: `BearerTokenResponseFactoryTest` passes without modification (including the quote
     escaping cases) except where OD-3 changes backslash handling, in which case the changed expectations are
     itemised.

3. **`ErrorTextSanitizer` printable-ASCII escape mode.** Add a mode to `ErrorTextSanitizer` that escapes every
   UTF-16 character outside printable ASCII (U+0020 to U+007E) as a backslash-`u` four-digit escape, caps the
   rendered source at a caller-chosen length and appends a caller-chosen marker, for rendering an
   operator-supplied value into an error message (API Sheriff's `renderForMessage`). Existing methods are
   untouched. Consumer-visible edges, one criterion each:
   - Every control character, DEL, C1 control, U+2028, U+2029, bidi control, a lone surrogate, a surrogate
     pair, a combining mark, a zero-width character and an ordinary non-ASCII letter is escaped; printable
     ASCII passes unchanged.
   - Length: the cap counts source characters before escaping (each escape expands one character to six), is
     exactly `maxLength` retained characters, appends the marker only when truncated, and handles
     `maxLength` zero and a value of exactly `maxLength` and one more.
   - `null` is handled as the existing default form handles it (`null` in, `null` out) or refused, per the
     chosen signature; the criterion asserts whichever is chosen.
   - Open points the operator decides (OD-4): hex case (the existing default form writes lower-case,
     API Sheriff's helper writes upper-case); whether the backslash itself is escaped (today neither form
     escapes it, so a typed `A` and an escaped `A` are indistinguishable in the output); the method name.
   - Consistency with ADR-0022: the mode is added to the one commons-layer utility, never as a second
     sanitiser class; the existing client `LogSanitizer`, which delegates to it, is unchanged.
   - Note for the consumer plan (no TokenSheriff change needed): API Sheriff's `sanitizeForLog` in its
     signature-only verifier strips ISO controls and misses U+2028, U+2029 and the bidi controls; the existing
     four-argument `sanitize(String, char, int, String)` already covers them, and that verifier is also
     removed by PLAN-05's seam.
   - Default behaviour: `ErrorTextSanitizerTest` passes without modification; the new mode adds its own
     test class or nested group.

4. **One public bounded body handler (droppable at outline).** Replace the two near-duplicate handlers with a
   single public handler in the validation module's `commons.transport` package that returns `byte[]`
   (callers needing a `String` decode it) and takes a policy: fail with `IOException` when the ceiling is
   exceeded, or truncate and report that it truncated. The decision to do this at all is OD-5, because the
   value is low. If done, consumer-visible fields and edges, one criterion each:
   - Both existing behaviours are preserved: the validation handler's `Content-Length` pre-check (a body
     advertising more than the ceiling is rejected without reading a byte, the subscription cancelled in
     `onSubscribe`) and its capped streaming read; the client handler's capped streaming read.
   - The two existing handlers differ today, and the criterion is to pin each difference before removing it:
     the validation one takes a `long` ceiling, the client one an `int`; the validation one pre-checks
     `Content-Length`, the client one does not (so adopting the shared handler gives the client the
     pre-check, a stricter default that is named in the ADR); the validation one ignores chunks after the
     failure, the client one resets its buffer.
   - Truncate policy: delivers at most `maxBytes` plus a stated bounded overshoot (API Sheriff's subscriber
     may deliver one in-flight chunk past the cap), reports the truncation as an explicit signal, cancels the
     subscription at the first crossing, and never holds more than the ceiling plus one chunk in memory.
   - Zero ceiling, ceiling equal to the body length, ceiling one below, a body arriving in one chunk and in
     many chunks, a lying `Content-Length` (smaller than the body), no `Content-Length`, a stream that errors
     mid-way, and a cancelled subscription are each asserted.
   - The provenance string (`boundOrigin`) stays in the failure message exactly as today.
   - Callers migrated: `JwksHttpContentConverter`, `WellKnownConfigurationConverter` (validation module) and
     `BackChannelHttp.bodyHandler()` (client module); the client's `BoundedContentBodyHandler` and the
     package-private `BoundedBodyHandlers` are deleted (pre-1.0: removed, not deprecated); the existing
     tests of the converters and of `BackChannelHttp` pass without modification.
   - Test debt closed: `BoundedContentBodyHandler` has no test class of its own today (asserted absence,
     see Claim Labels); the new handler has one.
   - If the operator drops this deliverable, the reasoning is recorded in the ADR and the two handlers stay
     as they are.

5. **Documentation, traceability and decision record.** Requirement entries for the new public helpers in the
   requirement document of the module that owns them (`doc/validation/requirements.adoc` for the Bearer
   helpers, `doc/commons/requirements.adoc` for the sanitiser mode and the body handler; ids next free at
   write time, not assumed), the transport and error specifications
   (`doc/commons/specification/transport.adoc`, `doc/commons/specification/error-model.adoc`), the
   architecture mention of `BoundedBodyHandlers` in `doc/commons/architecture.adoc` and of
   `BoundedContentBodyHandler` in `doc/client/architecture.adoc`, the Quarkus README for the producer
   behaviour, `doc/developer/test-impl-mapping.adoc` rows for every new test class, and an ADR in `doc/adr/`
   (number next free at write time) recording OD-1 to OD-5 with rationale; it amends or cross-references
   ADR-0022 for the new sanitiser mode. Verification: Javadoc on every new public member with `@since`,
   thread-safety note and example; a `package-info.java` for any new package; `check-javadocs-pre-commit`
   and `DocumentedPackagePatternTest` pass; every new test class is listed in the mapping document.

**Operator decisions at outline** (recorded in the ADR of deliverable 5 before scoping on them):

- OD-1: the `Bearer` scheme case rule for TokenSheriff's own producer. RFC 9110 §11.1 says the auth-scheme is
  case-insensitive; the producer is case-sensitive today, API Sheriff's copy is case-insensitive. Options:
  adopt the RFC rule in the producer (a visible, more permissive change), keep the producer's rule and give the
  helper a parameter, or make the helper case-insensitive and keep the producer on a strict wrapper.
- OD-2: whitespace rule after the scheme (RFC 6750 §2.1 says `1*SP`; API Sheriff strips, the producer does
  not) and the treatment of a whitespace-only token.
- OD-3: scope names outside the RFC 6749 §3.3 `scope-token` grammar: refuse (fail closed) or escape; and
  whether backslash escaping is fixed in the producer's output (a changed header for a name containing a
  backslash).
- OD-4: the sanitiser mode's name, hex case, backslash escaping, and `null` handling.
- OD-5: proceed with deliverable 4 or drop it. Reasons to drop: low value, two other modules' tests touched,
  the client module is under change in PLAN-01 to PLAN-04. Reasons to do: one fewer duplicated security
  control, a `Content-Length` pre-check for the client, a shared truncate mode.
- OD-6: the home package of the Bearer helpers (the validation module is the module `token-sheriff-validation-quarkus`
  already depends on; whether they sit in `commons` or `validation`, and whether the challenge builder sits
  beside the client's `InsufficientScopeChallengeParser`, which is in the client module that API Sheriff also uses).

6. **Back-channel logout validator hardening (folded from the PLAN-05 landing analysis; code and tests only, its
   documentation rides deliverable 5).** PLAN-05 shipped `LogoutTokenValidator` (`#899`, merge `fff5befe`); the
   landing's three-layer read found a resource-exhaustion defect and four minor ones in it. The operator decided to
   fold them here rather than stage a separate plan. The facts below are OBSERVED at `fff5befe` from a reading; nothing
   was run, so each is re-verified at outline (a refutation loops back to re-scope this deliverable). Consumer-visible
   criteria, one each:
   - `numericDate` (`LogoutTokenValidator:306-320`) scales an attacker-sized exponent: `new BigDecimal("1e99999999")`
     then `setScale(0, RoundingMode.FLOOR)` multiplies by 10^99999999, and it is called inside
     `synchronized (decisionLock)` (`:170-186`, via `isFresh:245`, `isUnexpired:252` and `:178`), so one validly signed
     token with a ten-character `iat` or `exp` stalls every other logout validation on that validator. The fix bounds
     the magnitude BEFORE any scaling, **for both signs of the exponent** (`1e99999999` and `1e-99999999` take the
     same path; the lane's proposed `precision()-scale()` bound covers only the positive one), and refuses an
     out-of-range value as the check that owns the claim (`ISSUED_AT` or `EXPIRATION`), never as a thrown exception.
     Criteria: both extreme exponents are refused with the owning check and return promptly, asserted by a time bound
     loose enough not to flake and by a second validation on another thread that is not blocked while the first runs;
     each positive control (`exp = N + 0.9`, the fractional `iat` window edges, a large but representable value) still
     passes. Adversarial inputs also include a 16-digit mantissa with a large exponent and an exponent at the
     `Integer` boundary.
   - `freshnessWindow` is validated only as positive (`:138-140`); an overflowing `Duration` makes `now.minus` /
     `now.plus` (`:247-248`) or `iat.plus` (`:178`) throw `DateTimeException` or `ArithmeticException` from `validate`
     inside the lock, against the documented contract that `validate` returns a `LogoutTokenCheck`. Criterion: the
     constructor refuses a window the arithmetic cannot represent, or `validate` refuses the token with the owning
     check; whichever is chosen is asserted with a window at the limit and one just inside it.
   - A backward wall-clock step after a purge re-admits a purged `jti` whose `iat` is fresh again (`:171` with
     `LogoutTokenReplayGuard:111`); the lane called it accepted but no document says so. Criterion: the limit is
     stated once in the class Javadoc, `doc/validation/security-reference.adoc` and ADR-0029 Risks (or ADR-0029 is
     amended), with no code change unless the operator decides otherwise at outline.
   - `SignatureVerifiedToken` is `@ToString` with `typHeader` unexcluded (`:51,77`), so a caller that logs the result
     prints a header value without `ErrorTextSanitizer`. Criterion: the value is excluded from `toString` or sanitised,
     and `LogRecordSanitizationGuardTest` (or a new guard) covers the type.
   - Tests: the out-of-range `numericDate` branch gets its own test class (`LogoutTokenValidatorTest` is 1012 lines
     against the 500-line guideline, so no further growth there).
   - The remaining PLAN-05 lane advisories, folded on the same word. Each is OBSERVED from the landing and re-verified at
     outline: (a) the `fromIssuerStatus` catch (`JwksEndpointHealthCheck:222`-, an `IllegalStateException` or
     `IllegalArgumentException` from a view) has no test that makes a view throw, and its WARN logs the issuer
     identifier and the loader message without `ErrorTextSanitizer`, as `fromIssuerConfig:208` does; criterion: a test
     per exception type asserting the endpoint status and the logged text, in a new class (`JwksEndpointHealthCheckTest`
     is 793 lines); (b) test classes over the 500-line guideline: `LogoutTokenValidatorTest` 1012,
     `JwksEndpointHealthCheckTest` 793, `TokenValidatorVerifySignatureTest` 553, and `ObservedValidatorResolverTest` at
     exactly 500; criterion: the three over the line are split along their existing seams with no test lost (the count of
     test methods is asserted equal before and after) and `ObservedValidatorResolverTest` does not grow; (c) the lane
     named an "ADR-0029 revision-history style" advisory without saying what; the outline reads ADR-0029 against the
     sibling ADRs' revision-history convention and either aligns it or records that none was found.
   Scope-bloat note: this brings the plan to six deliverables, which the guard presumes to split. It is kept whole on a
   recorded rationale: the item is one class and one package, shares no file with deliverables 1 to 4 except
   `doc/validation/security-reference.adoc` and ADR-0029 (both already touched by deliverable 5), and the operator chose
   the fold. The outline may split it back out if it grows.

## Standing Constraints

- Build and gate: Maven through the wrapper only (`./mvnw`). Before any commit that touches build inputs:
  `./mvnw -Ppre-commit clean verify` (it auto-fixes files in place; read `git status` afterwards and commit
  what it changed), then `./mvnw clean install`, then
  `./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am`.
  Never gate on `test-compile` or `test`: the `generators` classifier jar exists only from `package`.
- Pre-1.0 rules (AGENTS.md): no `@Deprecated`, no transitional comments, break APIs freely; a duplicate that
  is replaced is deleted, not kept as a shim.
- Everything new is additive. A change to TokenSheriff's own observable default behaviour (producer scheme
  case, whitespace handling, header scope order, backslash escaping, the client's `Content-Length` pre-check)
  happens only where an operator decision above allows it, and is then itemised in the PR body, the ADR and
  the release notes; where no decision allows it, the existing tests of the touched class pass without
  modification, which is the proof of unchanged behaviour.
- Security: these helpers sit on untrusted input paths (an `Authorization` header, a `WWW-Authenticate`
  value built from names, an operator-supplied string rendered into a message, a remote response body). Every
  new helper is fail-closed, bounded in time and memory, and has adversarial tests: CR/LF and control
  characters, header and parameter injection, oversize input, Unicode line separators and bidi controls,
  surrogate edge cases, a lying or absent `Content-Length`. No helper logs the value it refuses.
- Logging: `de.cuioss.tools.logging.CuiLogger`, `%s` only, `LogRecord` for INFO/WARN/ERROR with ranges INFO
  001-099, WARN 100-199, ERROR 200-299, every record documented in `doc/LogMessages.adoc`; identifiers are
  the next free value at write time in the owning message class, none assumed here. Exception parameter
  first. Remote text reaches a log line only through `ErrorTextSanitizer` (ADR-0022,
  `LogRecordSanitizationGuardTest`); the new helpers add no log record unless a deliverable needs one.
- OpenRewrite markers are actual defects: never commit one. Narrow catches against declared throwers.
- Javadoc: every public and protected type and method, `@since`, thread-safety note, example,
  `package-info.java` for any new package.
- Tests: JUnit 5, AAA, CUI Test Generator, parameterised tests for three or more similar variants (the edge
  lists above are parameterised), test classes at or under 500 lines (guideline). Signal integrity per
  `doc/client/specification/test-strategy.adoc` § Signal integrity: assert the identity of the refusal, pair
  every negative test with a positive control. Coverage: 80 percent instruction and branch on new code under
  the opt-in `coverage` profile.
- Documentation: AsciiDoc, `xref:` syntax, requirement ids and ADR number are next free at write time; every
  new test class is added to `doc/developer/test-impl-mapping.adoc`.
- Native image: `NativeImageMetadataTest` pins the shipped `reflect-config.json` exactly; the new helpers are
  plain classes and need no registration; any exception to that is registered deliberately and the pinned
  list updated in the same change.
- Dependencies: none added without explicit approval.
- Sonar: AGENTS.md names the SonarCloud new-code gate in CI as the coverage authority. An open Sonar issue on
  this plan's code is fixed in a pushed commit or suppressed in code with a stated rationale; none is
  dismissed silently. The plan template's Sonar transition procedure is not documented in this repository and
  is not restated.
- Git: feature branch, PR against `main` via `gh`, merge through the queue only after CI is green and every
  reviewer comment is answered and resolved (CLAUDE.md § Git Workflow).

## Claim Labels

- OBSERVED: `BearerTokenProducer.extractBearerTokenFromHeaderMap` is private, looks up the lower-case key `authorization`, takes the first value, and requires it to start with the case-sensitive constant `BEARER_PREFIX = "Bearer "`; it returns the remainder unstripped, including an empty string for `Bearer ` (finding `~:298`, actual `:298`; constant at `:135`) — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java` § `extractBearerTokenFromHeaderMap`, `BEARER_PREFIX`
- OBSERVED: the producer's class text documents the three-state behaviour: a value starting with `Bearer ` is a presented token; an empty or blank token after the prefix is `INVALID_REQUEST` and the cookie is not consulted; a bare `Bearer` carries no token and the cookie fallback applies — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java` § class documentation (lines 55-70)
- OBSERVED: API Sheriff's extractor matches the scheme case-insensitively with `regionMatches(true, ...)`, then `strip()`s and drops an empty token, so it has no empty-token state; the two copies therefore disagree on scheme case and on whitespace and empty-token handling (finding `~:232`, actual `:232`) — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/AuthenticationStage.java` § `extractBearerToken`
- OBSERVED: `BearerTokenResponseFactory.buildInsufficientScopeHeader` is private static, returns a string (finding said it returns a JAX-RS `Response`; that is `createConstraintViolationResponse`, which wraps the string), builds `Bearer realm="protected-resource", error="insufficient_scope", error_description="...", scope="..."` and passes the full required-scope collection (finding `~:213`, actual `:213`) — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java` § `buildInsufficientScopeHeader`, `createConstraintViolationResponse`
- OBSERVED: the producer answers a scope violation with HTTP 401 (`Response.Status.UNAUTHORIZED`) and documents it as an RFC 9470 step-up design choice, while API Sheriff's `insufficientScope` documents `403` with the header carrying only the missing scopes, sorted — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java` § `createConstraintViolationResponse`, and `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/AuthenticationStage.java` § `insufficientScope`
- OBSERVED: `escapeQuotes` replaces only `"` with `\"` and does not escape a backslash or refuse control characters; the existing test pins quote escaping (`read:\"special\"`) and accepts either scope order because the input is a `Set`; the scope names reaching it come from `@BearerAuth(requiredScopes = ...)` annotations (code, not request input), so the defect is not request-reachable in TokenSheriff's own use but is in any public helper fed other input — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java` § `escapeQuotes`, and `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactoryTest.java` § `QuoteEscaping`
- OBSERVED: the client's `InsufficientScopeChallengeParser` accepts only a case-insensitive `Bearer` scheme with `error=insufficient_scope`, refuses a value over 8192 characters, a stray backslash, C0 controls and DEL, more than 64 scope tokens, and any scope token outside the RFC 6749 §3.3 grammar `%x21 / %x23-5B / %x5D-7E` (which excludes both `"` and `\`) — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java` § class documentation
- OBSERVED: `ErrorTextSanitizer` is `public final`; `sanitize(String)` (`:101`) escapes control characters, DEL, C1, U+2028, U+2029 and bidi controls (lower-case hex, CR/LF/tab as `\r`, `\n`, `\t`), leaves other non-ASCII untouched, caps at 256 with the marker `...[truncated]`, and returns `null` for `null`; `sanitize(String, char, int, String)` (`:149`) replaces the same set with a caller-chosen character, cap and marker; both cut surrogate-pair-safely; neither escapes a backslash — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` § `sanitize`
- OBSERVED: API Sheriff's `RuleSupport.renderForMessage` (finding `~:127`, actual `:127`) escapes every `char` outside U+0020 to U+007E as upper-case `\uXXXX`, caps the source at 60 characters before escaping, appends `...` when longer, and leaves backslash unescaped; referenced about 40 times across the rule classes (a count of `renderForMessage` lines, definition and documentation included) — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/config/validation/rule/RuleSupport.java` § `renderForMessage`
- OBSERVED: API Sheriff's `SignatureOnlyTokenVerifier.sanitizeForLog` strips `Character.isISOControl` characters and caps at 128, so it passes U+2028, U+2029 and the bidi controls through (finding `~:181`) — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/auth/SignatureOnlyTokenVerifier.java` § `sanitizeForLog`
- OBSERVED: TokenSheriff has two bounded body handlers that differ: `commons.transport.BoundedBodyHandlers` is package-private, `long` ceiling, `String` result, pre-checks `Content-Length` (cancels the subscription in `onSubscribe` and fails with `IOException`) and ignores chunks once done; `client.internal.BoundedContentBodyHandler` is public in an internal package, `int` ceiling, `String` result, reads no `Content-Length`, resets its buffer on failure; both fail with `IOException` carrying the `boundOrigin` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlers.java` § `ofBoundedString`, and `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BoundedContentBodyHandler.java` § `apply`
- OBSERVED: callers are `JwksHttpContentConverter` (`:81`) and `WellKnownConfigurationConverter` (`:174`) for the validation handler and `BackChannelHttp.bodyHandler()` (`:329`) for the client handler; `doc/commons/architecture.adoc:38` and `doc/client/architecture.adoc:190` mention them — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java` § `bodyHandler`
- OBSERVED: asserted absence, `BoundedContentBodyHandler` has no test class: a search of all test sources for `BoundedContent` finds nothing, and `BoundedBodyHandlers` is tested through `JwksHttpContentConverterTest`, `WellKnownConfigurationConverterTest`, `WellKnownResultConverterTest` and the support class `BoundedBodyHandlerTestSupport` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTestSupport.java` § class documentation
- OBSERVED: API Sheriff's `CappedByteArrayBodySubscriber` (finding `~:409`, actual `:409`) is a `byte[]` subscriber that stops pulling and cancels the subscription once the cap is crossed, so the delivered body can be up to one in-flight chunk past the cap, and the caller trims it and derives a `truncated` flag by length — read at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/asset/UpstreamAssetSource.java` § `CappedByteArrayBodySubscriber`
- OBSERVED: the client's `LogSanitizer` already delegates to `ErrorTextSanitizer.sanitize(String)`, and ADR-0022 fixes `ErrorTextSanitizer` as the single sanitiser (default form escapes, parameterised form replaces) — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/LogSanitizer.java` § `sanitize`, and `doc/adr/0022-A_flow_context_is_single-use_and_untrusted_text_is_sanitised_by_one_commons-layer_utility.adoc` § Decision
- HYPOTHESIS: the Bearer helpers can live in the `token-sheriff-validation` module without a Quarkus or JAX-RS dependency, because `token-sheriff-validation-quarkus` already depends on it, and neither helper needs more than `java.util` — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/pom.xml` § dependencies and `token-sheriff-validation/pom.xml` § dependencies (verify-at-outline)
- HYPOTHESIS: the parser's rule on a backslash ("a stray backslash is refused") still allows a correctly escaped `\\` pair, so a builder that escapes a backslash round-trips through the parser; if the parser refuses every backslash, the builder must refuse a backslash instead (OD-3) — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java` § `parse`, and `.../InsufficientScopeChallengeAttackDatabaseTest.java` (verify-at-outline)
- HYPOTHESIS: the round-trip test between builder and parser cannot live in `token-sheriff-validation` (the client module depends on it, not the reverse) and belongs either in the client module's tests (a file shared with PLAN-01 to PLAN-04) or in the integration-test module — confirm/refute at `token-sheriff-client/pom.xml` § dependencies and `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/InsufficientScopeChallengeSpecIT.java` § class (verify-at-outline)
- HYPOTHESIS: adopting a shared handler gives the client a `Content-Length` pre-check, which can only reject earlier a body that would have been rejected anyway during streaming, so no valid response is newly refused; the client's `BackChannelHttp` tests confirm it — confirm/refute at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpTlsTrustTest.java` and the other `BackChannel*Test` classes (verify-at-outline)
- Verify-first clause: before scoping, the consuming phase reads the two producer classes, `ErrorTextSanitizer`, both body handlers, the client parser and the tests named in Expected Surface at HEAD of the 0.9.10 line, and confirms or refutes each HYPOTHESIS above. Because PLAN-01 to PLAN-04 change `token-sheriff-client`, it re-reads `BackChannelHttp` and `internal/` at that state before planning deliverable 4. OD-1 to OD-6 are asked at outline and recorded before scoping on them; deliverable 4 is not started before OD-5 is decided. A refutation loops back to re-scope this spec.

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java`:298 — `extractBearerTokenFromHeaderMap`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java`:135 — `BEARER_PREFIX`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java`:213 — `buildInsufficientScopeHeader`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java`:281 — `escapeQuotes`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducerTest.java` — `default behaviour pinned`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactoryTest.java`:264 — `QuoteEscaping`, default behaviour pinned
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`:1 — `module README (producer behaviour)`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java`:80 — `sibling parser, read-only reference for the round trip`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeAttackDatabaseTest.java` — `parser attack database, read-only reference`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java`:101 — `sanitize(String)`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java`:149 — `sanitize(String, char, int, String)`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizerTest.java` — `default behaviour pinned`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/LogSanitizer.java`:49 — `delegating wrapper, unchanged`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlers.java`:68 — `ofBoundedString`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverter.java`:81 — `caller`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverter.java`:174 — `caller`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BoundedContentBodyHandler.java`:74 — `of`, to be removed if OD-5 proceeds
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java`:329 — `bodyHandler`, caller
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/package-info.java`:21 — `mentions BoundedContentBodyHandler`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTestSupport.java` — `test support for the handler`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverterTest.java` — `must stay unchanged`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverterTest.java` — `must stay unchanged`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpTlsTrustTest.java` — `client back-channel tests, must stay unchanged`
- OBSERVED: `doc/commons/architecture.adoc`:38 — `mentions BoundedBodyHandlers`
- OBSERVED: `doc/client/architecture.adoc`:190 — `mentions BoundedContentBodyHandler`
- OBSERVED: `doc/commons/requirements.adoc`:36 — `commons requirements (new entries, ids next free at write time)`
- OBSERVED: `doc/commons/specification/transport.adoc`:1 — `transport specification`
- OBSERVED: `doc/commons/specification/error-model.adoc`:1 — `error model specification`
- OBSERVED: `doc/validation/requirements.adoc`:53 — `functional requirements (Bearer helpers)`
- OBSERVED: `doc/adr/0022-A_flow_context_is_single-use_and_untrusted_text_is_sanitised_by_one_commons-layer_utility.adoc`:1 — `ADR to cross-reference or amend`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`:1 — `test-to-implementation mapping`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/BearerCredentials.java` — `new Bearer extractor, package and name per OD-6, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/BearerChallenge.java` — `new challenge builder, package and name per OD-6, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/package-info.java` — `new package documentation, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/http/BearerCredentialsTest.java` — `new test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/http/BearerChallengeTest.java` — `new test class, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizerPrintableAsciiTest.java` — `new test class for the sanitiser mode, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java` — `new public bounded handler, only if OD-5 proceeds, to be created` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTest.java` — `new test class, only if OD-5 proceeds, to be created` (verify-at-outline)
- HYPOTHESIS: `doc/adr/` — `one new ADR file, number next free at write time` (verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidator.java`:306 — `numericDate` and the `decisionLock` section at :170 (deliverable 6; folded from inbox/landing plan-05-validation-public-seams, PLAN-05 shipped as #899)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/SignatureVerifiedToken.java`:51 — `@ToString` with `typHeader` (deliverable 6)
- OBSERVED: `doc/validation/security-reference.adoc`:121 — `logout-token section (backward-clock limit, deliverable 6)`
- OBSERVED: `doc/adr/0029-TokenValidator_publishes_signature-only_verification_native_JSON_claims_and_a_non-fetching_issuer_status_view.adoc`:1 — `Risks section, amended for the backward-clock limit (deliverable 6)`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheck.java`:222 — `fromIssuerStatus` catch and its unsanitised WARN (deliverable 6)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckTest.java` — `793 lines, split (deliverable 6)`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidatorTest.java` — `1012 lines, split (deliverable 6)`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorVerifySignatureTest.java` — `553 lines, split (deliverable 6)`
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckIssuerStatusFailureTest.java` — `new test class for the fromIssuerStatus catch, to be created (deliverable 6)` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidatorNumericDateTest.java` — `new test class for the out-of-range numericDate branch and the freshnessWindow limit, to be created (deliverable 6)` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none.
- Overlaps with: PLAN-03 (touches `token-sheriff-validation` commons transport `EgressPolicy`; this plan touches `commons/transport` too, only if OD-5 proceeds, in `BoundedBodyHandlers.java`, `JwksHttpContentConverter.java` and `WellKnownConfigurationConverter.java`, which PLAN-03 does not edit; the `commons/transport` package is shared, so the second plan re-reads it before editing). PLAN-01 to PLAN-04 (all in `token-sheriff-client`; this plan touches `token-sheriff-client` only in deliverable 4: `internal/BoundedContentBodyHandler.java`, `internal/BackChannelHttp.java`, `internal/package-info.java`; PLAN-01 to PLAN-04 may edit `BackChannelHttp.java`, so deliverable 4 re-reads it at that state or is dropped at outline). PLAN-05 (both touch `token-sheriff-validation` and its shared documents `doc/validation/requirements.adoc`, `doc/developer/test-impl-mapping.adoc`; no code file in common). PLAN-06 (shares `doc/developer/test-impl-mapping.adoc` and `doc/LogMessages.adoc` only).
- Adjacent to: the producer's HTTP status choice for a scope violation (401 versus 403), which this plan deliberately leaves alone; the client `LogSanitizer`, `FormEncoder` and `JsonEscaper` in `client/internal`, which are separate concerns; `InboundErrorNormalizer`, a second user of `ErrorTextSanitizer`, which the new mode does not touch. API Sheriff's matching deletions (`extractBearerToken`, the string-built challenge, `renderForMessage`, `CappedByteArrayBodySubscriber`) are a separate API Sheriff plan (AS PLAN-34) that waits for a released 0.9.10 and is not part of this spec.
- Release and sequencing: target release 0.9.10 (repository at 0.9.10-SNAPSHOT). `parallelization_scope` is 1: plans in this epic run sequentially. This is the lowest-priority plan; it runs last in WS-02 (PLAN-05, PLAN-06, PLAN-07), and deliverable 4 may be dropped at outline.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-07-public-helper-hygiene.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
