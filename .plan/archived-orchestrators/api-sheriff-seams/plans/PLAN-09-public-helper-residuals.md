# PLAN-09: Public helper residuals

epic: api-sheriff-seams
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-09-public-helper-residuals.md` and is queued as one row file, `queue/PLAN-09.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the three-layer analysis of PLAN-07's landing (#908, `landings/PLAN-07.md`), staged on the operator's word.

## Objective

PLAN-07 shipped four public helpers (`BearerCredentials`, `BearerChallenge`, `ErrorTextSanitizer.escapeNonPrintableAscii`,
`BoundedBodyHandler`) and hardened the back-channel logout validator and the unknown-`kid` refresh (#908, merged as `80bc2b73`).
The landing analysis found no blocker and no major defect, and five minor ones that the lane, its reviews and the review bot did
not settle. API Sheriff adopts the helpers once 0.9.10 is released, so what is wrong with them is cheapest to fix before then.
This plan closes the one defect with a request-path effect (an invalid `@BearerAuth` scope throws from the scope-violation path
and nothing validates it at startup), settles the two behaviours of the unknown-`kid` claim path that the documents describe
differently from the code, bounds the helpers' own work on hostile input, brings the hub documents and the decision record level
with what shipped, and gives one test the power to fail for the property it names. Nothing here widens the public API beyond
what a fix needs; the default behaviour of every PLAN-07 helper, `HttpJwksLoader` and `LogoutTokenValidator` does not change
except where a deliverable says so.

## Deliverables

1. **An invalid `@BearerAuth` / `@BearerToken` scope cannot reach the request path as an unhandled exception.**
   `BearerTokenResponseFactory.createConstraintViolationResponse` passes the annotation's required scopes to
   `BearerChallenge...build()`, which throws `IllegalArgumentException` for a name outside the RFC 6749 §3.3 `scope-token`
   grammar or for more than 64 distinct scopes. `BearerTokenInterceptor.validateBearerToken` calls `createErrorResponse()`
   without a catch, `BearerTokenResult.createErrorResponse` documents only `IllegalStateException`, and nothing validates
   annotation scope names at startup. Make the failure happen where it is actionable: at deployment or at first interceptor
   binding with a message naming the annotated method and the offending scope, or on the request path as a defined response, as
   the outline decides; either way an annotated method whose scopes the challenge cannot represent never produces an
   unhandled exception from the scope-violation path. The check reuses the builder's rule and does not copy the grammar into a
   second place. Verification, each with a valid annotation as its positive control: a scope with a space, a non-ASCII scope and
   an annotation with 65 distinct scopes each produce the decided outcome; a method-level and a class-level annotation are both
   covered; the existing `QuoteEscaping` expectations of `BearerTokenResponseFactoryTest` stay as they are.
2. **The unknown-`kid` claim path does what its documents say, or the documents say what it does.** The landing analysis read two
   differences at `HttpJwksLoader.scheduleUnknownKidRefresh`: (a) the in-flight guard is taken before the window is checked, so
   every unknown-`kid` miss, including one that loses the window, pulses the guard that `performBackgroundRefresh` also takes, and a
   periodic cycle that meets it is skipped uncounted and without its `maxStaleness` check; ADR-0030 and `openid-discovery.adoc`
   say the guard is taken when a window is claimed and that only a sustained deferral needs a multi-thread executor and a slow
   identity provider; (b) the window is advanced before `execute`, and a failed hand-off releases only the guard, so the next
   fetch waits up to the window (at most 3600 s). The guard-before-window order was the operator's choice (D7, ADR-0030 Risks) and
   was declined on the review thread with that reason. Verification, with an injected clock and a scripted executor, each with a
   positive control: either a read-only window pre-check before the guard keeps a window-losing miss from touching the guard while a
   guard held at the moment of the claim still never burns a window and a lost claim never leaks a guard, or the pulse is
   documented where the docs now say otherwise; and either a failed hand-off hands the window back, or the burn is stated in
   ADR-0030 and the config Javadoc. The existing tests that pin the guard order stay green or change with the decision.
3. **The helpers bound their own work on hostile input.** Settled at outline, each changed with a test or documented as accepted:
   - `BearerChallenge.Builder.scopes(Collection)` adds every valid scope to a sorted set and refuses only at `build()`, and `realm`
     and `errorDescription` have no length bound before then; the 64-scope and 8192-character limits should refuse early, or the
     class states that it bounds the result and not the work.
   - `BoundedBodyHandler.BoundedSubscriber` keeps one `byte[]` per delivered buffer, so the memory bound holds in bytes and not
     in objects, and the client back channel lost its `ByteArrayOutputStream`. `COMMONS-17` and the transport specification state
     the byte bound only. Any change keeps `COMMONS-17`'s ceiling-plus-one-in-flight-chunk bound (a single growable buffer broke
     it and was reverted in #908; see lesson `2026-11-16-06-001`); a coalescing step that merges small buffers up to a fixed
     block size is one option, stating the object-count factor is the other. A test delivers many one-byte buffers in one call
     and checks the assembled body.
   - `BoundedBodyHandler.subscriberFor` reads `Content-Length` with `firstValueAsLong`, which throws `NumberFormatException` for a
     non-numeric value outside any `try`. State what happens (the JDK client may refuse the header first) and add a test with the
     scripted `ResponseInfo` of the existing test support; a malformed value is a normal failure with the handler's own message,
     not an escaping `NumberFormatException`, unless the outline shows it cannot reach the handler.
   - A header-length round-trip test: a challenge the builder emits at its limit is accepted by the client's
     `BearerChallengeParams` (limit 8192, `MAX_HEADER_LENGTH`) and one character longer is refused by the builder, so lowering one
     constant fails a test. The client main code keeps no dependency on `commons.http` (the review-thread decision).
4. **The hub documents and the decision record match what shipped.** Each location below is re-read against the code and fixed;
   the final text matches the shipped behaviour.
   - `doc/commons/README.adoc` says `COMMONS-1`–`COMMONS-15`; `COMMONS-16` and `COMMONS-17` exist.
   - `doc/README.adoc` lists ADRs up to 0029; ADR-0030 and ADR-0031 (and any ADR this plan adds) are missing.
   - ADR-0031's "Changed default behaviour" omits the 100-year `LogoutTokenValidator.MAX_FRESHNESS_WINDOW`, the unchanged-JWKS
     discard moving from WARN to DEBUG, the health-check WARN no longer carrying a stack trace and carrying a sanitised issuer, and
     the client body handler's switch to a chunk list; add them or point at the ADR that holds each. The PR body's own heading
     says "itemised in ADR-0031".
   - `IssuerConfigResolver`'s Javadoc says the `initialLoadRetry` plus refresh-interval-0 combination "is validated by the
     builder", and it fails with the builder's message, while the unknown-`kid` window names the Quarkus key; make the two
     consistent (a key-naming check, or a recorded reason) with a test per property.
   - `BoundedBodyHandler`'s public nested types `OverLimitPolicy` and `BoundedBody` and `MAX_CEILING` carry no `@since`; add them
     in the form the class uses.
5. **One test can fail for the property it names.** `JwksEndpointHealthCheckIssuerStatusFailureTest` uses the benign issuer
   `failing-issuer` and a hostile message, so replacing `ErrorTextSanitizer.sanitize(issuer)` with the raw issuer leaves every
   assertion green; the spec of PLAN-07 asked the logged text including the issuer to be asserted. Give the test a hostile issuer
   (control and line-break characters), assert its escaped form in the WARN, and keep the existing message and no-thrown-exception
   assertions. Settled at outline, each either done or recorded as accepted: whether the "valid token is not blocked" control in
   `LogoutTokenValidatorNumericDateTest` is scheduling-dependent (the valid thread may finish before the hostile one takes the
   lock) and how to make it discriminate; the 16-digit-mantissa case there asserts `SIGNATURE` because the parser refuses it
   upstream (the test comment says `LogoutTokenCheck#SIGNATURE` documents that; read it, do not assume), so it adds no regression
   signal for the `numericDate` bound and the outline either replaces it with an input that reaches the bound or records why not;
   the ADR-0029 revision-history convention check PLAN-07's spec asked for has no recorded outcome.

Done when: all criteria pass, the existing PLAN-07 tests (`BearerTokenProducerTest`, `HttpJwksLoaderDefaultBehaviourUnchangedTest`,
`ObservedValidatorResolverTest`, the split classes' methods) pass without edited assertions except where a deliverable names the
decision, the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar open-issue read at the PR
head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **Where an invalid annotation scope fails** (deliverable 1): at deployment or first binding, or on the request path as a defined
  response; it is a 0.9.10 contract for the annotation.
- **The guard-before-window order** (deliverable 2): keep D7 and document the pulse, or add the read-only pre-check; and whether a
  failed hand-off refunds the window.
- **How the body handler's object count is bounded** (deliverable 3): coalesce, or state the factor; `COMMONS-17`'s byte bound must
  hold either way.
- **Whether the `error_description` grammar stays the RFC 9110 quoted string** (OD-3 of ADR-0031) — recorded as kept; the narrower
  RFC 6750 attribute grammar is a contract change that needs its own decision and is NOT in this plan unless the operator says so.

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments.
- **Default behaviour stays unchanged and is proven so**, except where a deliverable states a decision: `BearerCredentials`,
  `BearerChallenge` output for a valid input (`BearerChallengeTest` byte-identical expectations), `ErrorTextSanitizer`'s three forms,
  `BoundedBodyHandler`'s ceiling and policy behaviour, the unknown-`kid` refresh's default (off) behaviour, and
  `LogoutTokenValidator`'s results.
- **Memory bound.** `COMMONS-17` (ceiling plus one in-flight chunk while streaming) stays true; read the requirement and the transport
  specification before changing the buffer.
- **Security.** Attacker-controlled text goes through `ErrorTextSanitizer`; no token is logged; the CWE-117 rule of ADR-0022 holds for
  every new or changed log line. A new LogRecord takes the next free identifier in its range at write time and is added to
  `doc/LogMessages.adoc`.
- **Native image.** The helpers stay plain classes; no reflect-config entry and no new dependency without operator approval.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never
  sleeps), a negative control per regression test, new or grown test classes at or under 500 lines and a split a pure move.
- Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`): an open Sonar issue is fixed in a pushed commit, or
  dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`.
  A dismissal the run cannot transmit is a blocker, never a silent pass.
- **Review-fix discipline** (lessons `2026-11-16-06-001` and `2026-11-16-06-002`): before applying a review-bot suggestion that changes a
  bounded structure, read the requirement row that states the bound; after a refactor onto a helper, list the guards the old call site
  applied and test the input each existed for.

## Claim Labels

- OBSERVED: `BearerTokenResponseFactory.createConstraintViolationResponse` builds the challenge from `result.getRequiredScopes()` and
  nothing catches `IllegalArgumentException` — read at
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java`
  (:153-175); the interceptor calls `result.createErrorResponse()` unguarded — read at
  `.../quarkus/interceptor/BearerTokenInterceptor.java` (:164-167); `createErrorResponse` documents only `IllegalStateException` — read at
  `.../quarkus/producer/BearerTokenResult.java` (:259-263)
- OBSERVED: `BearerChallenge.Builder.scope` refuses a name that is not a `scope-token` and `build()` refuses more than 64 scopes or more than
  8192 characters — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/BearerChallenge.java` (:144-198)
- OBSERVED: a search of the Quarkus module's main sources for a validation of annotation scope names found none (a grep, not an ArC run)
- OBSERVED: `scheduleUnknownKidRefresh` takes `fetchInFlight` first, releases it when the window is not open, advances `nextAllowedNanos`
  before `execute`, and the hand-off catch releases only the guard; `performBackgroundRefresh` skips its cycle, uncounted, when the guard
  is held — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` (:862-889, :1476-1479)
- OBSERVED: ADR-0030 and `openid-discovery.adoc` describe the guard as taken when a window is claimed — read at
  `token-sheriff-validation/doc/configuration/openid-discovery.adoc` (:228-241) and
  `doc/adr/0030-An_opt-in_unknown-kid_refresh_claims_one_window_per_loader_and_installs_only_a_key_set_holding_the_kid.adoc` (:159-170)
- OBSERVED: `BoundedSubscriber` keeps `List<byte[]> chunks` and appends one array per delivered buffer; the deleted client handler kept a
  `ByteArrayOutputStream` — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java` (:259, :287-296) and at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BoundedContentBodyHandler.java` at `1be3867b` (:93)
- OBSERVED: `subscriberFor` reads `responseInfo.headers().firstValueAsLong("Content-Length")` outside any `try` — read at
  `BoundedBodyHandler.java` (:152-157)
- OBSERVED: `BearerChallengeParams.MAX_HEADER_LENGTH` is 8192 and `BearerChallenge.MAX_LENGTH` is 8192 — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java` (:64) and `BearerChallenge.java` (:74-77)
- OBSERVED: `doc/commons/README.adoc` says `COMMONS-1`–`COMMONS-15` (:53); `doc/README.adoc` lists ADRs to 0029 (:42);
  ADR-0031's "Changed default behaviour" holds OD-1, OD-2, OD-3 and the quoted blank cookie and not the freshness window, the DEBUG
  downgrade, the health-check log change or the chunk list — read at the three files (the ADR at :155-175)
- OBSERVED: `IssuerConfigResolver` names the Quarkus key for the unknown-`kid` window combined with a refresh interval of 0 (:584-590) and
  its Javadoc says the `initialLoadRetry` combination is validated by the builder (:600-603); the builder message is at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` (:1060-1062)
- OBSERVED: `JwksEndpointHealthCheckIssuerStatusFailureTest` uses `ISSUER = "failing-issuer"` and a hostile `RAW_MESSAGE` only (:56-57,
  :137-148) — read at
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckIssuerStatusFailureTest.java`
- OBSERVED: `LogoutTokenValidatorNumericDateTest` pins `SIGNATURE` for a 16-digit mantissa and states the parser limit in a comment (:108-120)
  — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidatorNumericDateTest.java`
- HYPOTHESIS: an annotated method with a bad scope returns an HTTP 500 and not a 401 on a valid-token-missing-scope request — the
  exception propagation is read, the status the container maps it to was not run (verify-at-outline)
- HYPOTHESIS: a forged-`kid` spray at request rate skips enough periodic cycles to matter — the duty cycle per request is tiny and the
  magnitude was not measured (verify-at-outline)
- HYPOTHESIS: the JDK HTTP client hands the handler one-byte buffers and may refuse a non-numeric `Content-Length` before the handler
  runs — neither was observed (verify-at-outline)
- HYPOTHESIS: the "valid token is not blocked" control is scheduling-dependent — the landing analysis's reading of the thread
  start order, not a mutation run (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) every line number above against HEAD (PLAN-03 and
  PLAN-08 may have moved `IssuerConfigResolver`, `HttpJwksLoaderConfig`, `doc/LogMessages.adoc` and `quarkus-config-doc.adoc`), (3) that the
  guard-order change keeps ADR-0030's two properties (a held guard never burns a window; a lost claim never leaks a guard), and (4) that the
  deliverables stay at about six tasks or fewer; if the outline scopes above that, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactory.java`:153 — `createConstraintViolationResponse`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java`:164 — `validateBearerToken`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResult.java`:259 — `createErrorResponse`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerAuth.java`:163 — `requiredScopes`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`:584 — the window and `initialLoadRetry` checks
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenResponseFactoryTest.java`:1 — `BearerTokenResponseFactoryTest` (QuoteEscaping stays as it is)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheckIssuerStatusFailureTest.java`:56 — the benign issuer
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:862 — `scheduleUnknownKidRefresh`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`:1476 — `performBackgroundRefresh`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:1060 — the `initialLoadRetry` combination message
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java`:154 — `subscriberFor` (`Content-Length`)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandler.java`:259 — `BoundedSubscriber` chunk list
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/http/BearerChallenge.java`:144 — `scope`, `scopes`, `build`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/http/BearerChallengeTest.java`:1 — `BearerChallengeTest`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/BoundedBodyHandlerTest.java`:66 — `BoundedBodyHandlerTest`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidatorNumericDateTest.java`:108 — the mantissa and the control tests
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java`:64 — `MAX_HEADER_LENGTH` (read; the round-trip test sits in the client test tree)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeRoundTripTest.java`:1 — `InsufficientScopeChallengeRoundTripTest`
- OBSERVED: `token-sheriff-validation/doc/configuration/openid-discovery.adoc`:228 — the guard and pickup paragraph
- OBSERVED: `doc/adr/0030-An_opt-in_unknown-kid_refresh_claims_one_window_per_loader_and_installs_only_a_key_set_holding_the_kid.adoc`:159 — Risks
- OBSERVED: `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc`:155 — "Changed default behaviour"
- OBSERVED: `doc/commons/README.adoc`:53 — the requirement range
- OBSERVED: `doc/README.adoc`:42 — the ADR index
- OBSERVED: `doc/commons/requirements.adoc`:127 — `COMMONS-17`
- OBSERVED: `doc/LogMessages.adoc`:1 — only if a record is added or its text changes
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/interceptor/` — a test class for the invalid-annotation-scope outcome (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/` — a new or split test class for the claim-path decisions (to be created; each at or under 500 lines) (verify-at-outline)

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that API Sheriff adopts the helpers from the first
  release that carries them with these edges closed.
- Depends on: PLAN-07, shipped (#908, `80bc2b73`); the plan edits code and docs that landing created.
- `parallelization_scope` is 2. Overlaps are decided by the `next` admission gate from the declared surface, not here. Known shared
  surface with staged plans, to be confirmed at emit: PLAN-03 (seven files by the 2026-11-17 cross-check: `doc/LogMessages.adoc`, `doc/commons/requirements.adoc`,
  `IssuerConfigResolver`, `openid-discovery.adoc`, `BoundedBodyHandler`, `HttpJwksLoaderConfig`, `BoundedBodyHandlerTest`) and PLAN-10 (`doc/LogMessages.adoc`).
  PLAN-08 has shipped (#912, `dcbdcfb6`; it edited the INFO `4` row of `doc/LogMessages.adoc` and added no record). The operator chose to emit PLAN-03 first (2026-11-17), so
  this plan waits for it and re-reads the files above at HEAD. Requirement, ADR and log ids are next free at write time (ADR numbering continues from `0031`).
- Adjacent to: API Sheriff's deletions of its own copies (a separate API Sheriff plan; not part of this spec), the client parser
  (`BearerChallengeParams`, read-only except for the new test), and the PLAN-07 lessons `2026-11-16-06-001` and `2026-11-16-06-002`.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-09-public-helper-residuals.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
