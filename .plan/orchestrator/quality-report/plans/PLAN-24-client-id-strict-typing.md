# PLAN-24: Strict typing for the `client_id` fallback and the other identifier claims

epic: quality-report
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Staged 2026-11-04 from the PLAN-11 landing residual (`landings/PLAN-11.md` § Verified residual), at the operator's
> instruction. The orchestrator EMITS the command below; it never launches the plan inline.

## Objective

PLAN-11 made `azp` strict (`StrictStringMapper`), but `client_id` is still mapped by `IdentityMapper`, which
stringifies any JSON value (`value.toString()`). `AuthorizedPartyValidator` falls back to `client_id` when `azp` is
absent and the RFC 9068 fallback is on, so a numeric `client_id` such as `12345` on an `azp`-less token becomes the
string `"12345"` and satisfies a configured client id of the same text. Close that the way PLAN-11 closed `azp`:
map `client_id` strictly, test every non-string JSON type in both fallback settings, and settle whether the other
identifier claims that still use `IdentityMapper` (`iss`, `sub`, `jti`) have the same exposure. Fail-closed only: this
plan may narrow acceptance, never widen it.

## Deliverables

1. **`client_id` strict mapping** — map `ClaimName.CLIENT_ID` through the existing `StrictStringMapper` (no new
   mapper, no new log identifier, no new event type: a non-string value throws the mapper's `MISSING_CLAIM`
   exception exactly as `azp` does). Tests in `AuthorizedPartyValidatorTest` over {number, boolean, array, object} x
   {fallback on, fallback off} with `azp` absent, asserting the exception, its event type and the
   `SecurityEventCounter` counts; a string `client_id` that matches still passes, and an `azp`-present token is
   unchanged. Reuse `JsonTypedAzpFixture` (extend it for `client_id` or generalise its name in the same change).
2. **Identifier-claim sweep (decide at outline)** — for `iss`, `sub` and `jti`, which still use `IdentityMapper`,
   read where each mapped value is compared or used as an identifier and settle whether a non-string JSON value can
   satisfy a comparison today. Where one can, map it strictly with a test; where none can (for example `iss`, whose
   selection reads the body with `getString` before mapping), record that in one sentence in `security-reference.adoc`
   and add no code. Never change `name`, `email`, `preferred_username`, `typ` or `upn`: they are display values, not
   compared identifiers, and this plan does not widen into them.
3. **Tighten the PLAN-11 `azp` assertions** — the non-string `azp` tests assert only the exception supertype (a PLAN-11
   code-review advisory). Assert the specific event type and the counter, in `AuthorizedPartyValidatorTest`.

## Standing Constraints

- Every claim below was grounded at HEAD `a9ce05df` (the PLAN-11 merge); re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report text is in
  `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes. This work has no finding ID of its own (it is the
  `client_id` follow-up PLAN-11 named); the PR body names PLAN-11 #853 as its origin. Do NOT edit `source-report/` or
  `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a configuration that is
  rejected today. State in the PR body the acceptance that narrows (a numeric/boolean/array/object `client_id`).
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test conventions,
  generators where variation matters); build and verify via Maven only.
- No new `LogRecord` or event type. If outline finds one is unavoidable, the footprint declares both
  `JWTValidationLogMessages.java` and `doc/LogMessages.adoc` up front; a conditional "add it if needed" clause is not
  allowed (PLAN-11-001).
- No new class is expected (the mapper exists and is already registered in the native-image metadata). If one becomes
  necessary, `reflect-config.json` and `NativeImageMetadataTest` go into the footprint at the same time.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted via
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count
  at the head — `0` confirmed or every remaining issue dismissed; an `undecidable` read blocks. The Done-when lists the
  PR's open-issue count and the rules left open (`unknown` for a failed read, never `0`).
- The pre-push gate's stand-alone `test-compile` arm cannot succeed in this reactor (the validation test-jar exists
  only after `package`); the whole-tree `verify -Ppre-commit` is the cover. Do not file the failed stand-alone arm as
  findings (PLAN-11-002).

## Claim Labels

- OBSERVED: `ClaimName.CLIENT_ID` is mapped by `new IdentityMapper()` and `ClaimName.AUTHORIZED_PARTY` by `new StrictStringMapper()` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimName.java` § `CLIENT_ID`, `AUTHORIZED_PARTY`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: ClaimName.java:175 CLIENT_ID uses IdentityMapper; AUTHORIZED_PARTY (166) uses StrictStringMapper
- OBSERVED: `IdentityMapper` stringifies a non-`String` value with `value.toString()` and says so in a design-decision comment — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/IdentityMapper.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: IdentityMapper.map stringifies a non-String value with toString, by design
- OBSERVED: `AuthorizedPartyValidator` reads `client_id` through `getOriginalString()` and compares it with the configured ids only when `azp` is absent and the fallback is enabled — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/AuthorizedPartyValidator.java` § the "RFC 9068 fallback" block
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: AuthorizedPartyValidator client_id branch only when azp absent and fallback enabled, reads getOriginalString
- OBSERVED: no main-source consumer of `ClaimName.CLIENT_ID` other than that validator (the log record `AZP_CLIENT_ID_FALLBACK` only names it) — swept `token-sheriff-validation/src/main/java`; the client and Quarkus modules use a `client_id` string literal for requests and config, not the claim
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: CLIENT_ID is used in main only by the enum, AuthorizedPartyValidator:100 and one log record
- OBSERVED: `StrictStringMapper` throws `TokenValidationException` with `MISSING_CLAIM` for any non-string JSON value and returns `null` for an absent claim — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/StrictStringMapper.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: StrictStringMapper returns null if absent, forPlainString for String, else throws TokenValidationException MISSING_CLAIM
- OBSERVED (re-read 2026-11-05 at HEAD `0c56583e`, after PLAN-23 #855): `TokenBuilder.mapClaim` now catches a mapper-thrown `TokenValidationException`, increments `SecurityEventCounter` under the exception's own event type, and rethrows it unchanged (a runtime exception from a custom mapper is still counted as `MISSING_CLAIM` and wrapped). So a `StrictStringMapper` rejection IS counted today, as `MISSING_CLAIM` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java` § `mapClaim`. This supersedes the PLAN-11 advisory "a `StrictStringMapper` rejection is neither counted nor logged"; deliverable 1 and 3 counter assertions should expect that count, and the outline re-reads `mapClaim` before pinning the exact number (one increment per rejected claim, asserted against the path the test actually drives). `TokenBuilder.java` and `TokenBuilderTest.java` are not in the Expected Surface and need no edit unless the outline finds the count differs.
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: TokenBuilder.mapClaim counts the TokenValidationException event type and rethrows unchanged
- OBSERVED: `AuthorizedPartyValidatorTest` has no test with a non-string `client_id`; the PLAN-11 `azp` rows live in the same class and in `AudienceValidatorTest` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AuthorizedPartyValidatorTest.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: AuthorizedPartyValidatorTest has no non-string client_id test
- OBSERVED: `JsonTypedAzpFixture` builds tokens whose `azp` carries an array, number, boolean or object — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/JsonTypedAzpFixture.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: JsonTypedAzpFixture.nonStringAzpValues yields array, number, boolean, object
- HYPOTHESIS: `iss` selection reads the body with `DecodedJwt.getIssuer()` (a `getString`) before the claim is mapped, so a non-string `iss` is already rejected or ignored before `IdentityMapper` runs — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/DecodedJwt.java` § `getIssuer` and `TokenContent.getIssuer` (verify-at-outline)
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: non-string iss is rejected by orElseThrow in both pipelines before IdentityMapper runs
- HYPOTHESIS: `sub` and `jti` are not compared against a configured value, so their stringification is not a defect; only the mandatory-claim and blank checks read them — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/MandatoryClaimsValidator.java` and `ExpirationValidator.java` § `rejectBlankClaim` (verify-at-outline)
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: no main site compares sub or jti to a configured value; client TokenLifecycleManager compares token to token only
- HYPOTHESIS: `ClaimNameTest` pins the mapper per claim and needs a row for `client_id` — confirm/refute at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimNameTest.java` (verify-at-outline). CONTRADICTED 2026-11-06 and re-scoped: the test already has a string `client_id` row (`provideClaimNameMapTestData`, about line 206) and name/type asserts (lines 312 and 313); it does not pin the mapper class and has no non-string `client_id` row. What is missing is the non-string row and a mapper pin for `client_id`, not a first row
  - verdict: contradicted | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: yes | evidence: ClaimNameTest already has a string client_id row (~line 206) and name/type asserts; no non-string row and no mapper pin; spec claim reworded
- Verify-first clause: before scoping deliverable 2, read every site that compares or uses the mapped `iss`, `sub` and `jti` values; a site where a non-string value can satisfy a comparison turns that claim into a fix, a site where none can turns it into a documented non-issue. Refutation of the premise (a stringified `client_id` cannot reach the comparison) loops back to re-scope.
  - verdict: unverifiable | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: verify-first clause, an instruction; its premise holds for iss (rejected earlier) and sub/jti (no configured-value comparison)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimName.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AuthorizedPartyValidatorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/JsonTypedAzpFixture.java`
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimNameTest.java` (verify-at-outline)
- HYPOTHESIS: `doc/validation/security-reference.adoc` (verify-at-outline — one sentence on the identifier-claim typing; also declared by PLAN-18 and PLAN-19)

## Dependencies and Sequencing

- Depends on: none (PLAN-11 is merged).
- Overlaps with: none in flight. `doc/validation/security-reference.adoc` is also declared by PLAN-18 and PLAN-19;
  a single shared file does not block, and this plan runs before both.
- Adjacent to: `ClaimName.java` and the `IdentityMapper` design-decision comment — if deliverable 2 maps another claim
  strictly, update that comment in the same change.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-24-client-id-strict-typing.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
