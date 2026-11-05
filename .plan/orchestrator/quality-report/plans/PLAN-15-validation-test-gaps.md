# PLAN-15: Validation test gaps — tamper outcomes, boundaries, confusion, dup kid

epic: quality-report
workstream: WS-01

## Objective

Make the validation test suite assert what it claims. Tampered-payload tests build 2-segment tokens and
pass at the format check without ever reaching signature verification, asserting no event type (TV-6,
gap 7). Expiry/`nbf` boundaries are only tested at `now()±1h` (gaps 2, 5). RS/HS algorithm confusion
is only covered end-to-end (gap 3). The duplicate-`kid` winner and counter are unasserted (gap 6).
Enum tests exercise JDK machinery (TV-4); `TestTokenHolder`'s fixed generators deserve a note (TV-7).
Closes TV-4, TV-6, TV-7, validation gaps 2, 3, 5, 6, 7.

## Deliverables

1. **TV-6** — tampered tokens keep 3 segments; tests assert `SIGNATURE_VALIDATION_FAILED` (or the precise
   event per `TamperingStrategy` via an expected-event map) in `TokenValidationSecurityTest` and
   `JwtTokenTamperingUtilTest`. Folded 2026-11-05 from the PLAN-23 landing (operator standing rule: tool findings
   are fixed immediately): SonarCloud `java:S8786` (key `AZ7xhrgAXGjoVdkwztNZ`, MAJOR, "super-linear regex") is
   still open on `main` at `JwtTokenTamperingUtil.java` line 450, the `removeKeyId` regex, although PLAN-23 (#855)
   rewrote it with possessive quantifiers (`,?+\s*+"kid"\s*+:\s*+"[^"]*+"`); the analyser still flags the
   possessive form at the merge commit `0c56583e`. Re-query the key at outline. If it is still open, rewrite the
   removal without the flagged shape (for example, parse the header JSON, drop the `kid` member and re-serialise, or
   split the match into linear steps), keeping the tampered token at 3 segments, or dismiss it with a stated
   reason transmitted via `sonar_rest transition`. This is the same helper TV-6 already edits.
2. **gaps 2/5** — fixed-clock `ExpirationValidator` tests at exactly `exp`, `exp+skew`, `nbf-skew`,
   `nbf-skew-1` (consistent with the V-17c boundary fixed by PLAN-09).
3. **gap 3** — unit-level RS256→HS256 confusion test in `TokenSignatureValidatorTest`.
4. **gap 6** — duplicate-kid test asserts which key wins (via `getKeyInfo`) and the counter.
5. **TV-4 / TV-7** — delete valueOf/ordinal-only enum tests (keep `MeasurementTypeTest`'s ordering
   test); Javadoc note on `TestTokenHolder` that alg/kid are intentionally fixed.
6. **DSL-JSON truncated-escape sweep (validation module)** — folded 2026-11-04 from the PLAN-10 landing (candidate
   lesson 004, operator choice "fold into PLAN-15"). PLAN-10 (#856) found that DSL-JSON throws an unchecked
   `ArrayIndexOutOfBoundsException` on a body that ends inside a string escape (for example `{"kid":"abc\`), which
   escaped the client's `IOException` contract; it fixed only `TokenEndpointClient`. In the validation module these
   `dslJson.deserialize` sites catch only `IOException` (read at HEAD `02c0fd3a`): `JwksHttpContentConverter`,
   `WellKnownConfigurationConverter`, `JwksParser` (the `Jwks` and the single-`JwkKey` reads) and
   `NonValidatingJwtParser` (the `JwtHeader` read). For each site, feed the truncated-escape input and assert the
   outcome is the module's checked parse failure (the existing rejection path and its event type), never an unchecked
   exception. Reachability was NOT tested at fold time: a site where the exception cannot arise, or is already caught
   upstream, is recorded in one line and left alone. Under the "test-only plan" constraint, a site that does leak gets
   the minimal fix of folding the runtime exception into the existing `IOException` handling, named in the PR, with the
   suppression marker and rationale written up front (PLAN-10-005: an OpenRewrite recipe flags a deliberate broad
   catch), and no new `LogRecord`. The client-side sites (`DiscoveryResolver`, `ParClient`, `UserInfoClient`) are NOT
   in this plan; they are a Watch in `epic.md`. This brings the plan to six deliverables, the split threshold: the sixth
   is one test seam repeated four times, so it is kept unsplit (rationale recorded 2026-11-04).

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Test-only plan: no production change unless a test exposes a defect (then minimal fix, named in PR).

## Claim Labels

- OBSERVED: `shouldRejectTokensWithTamperedPayloads` builds `parts[0]+"."+payload+"."` and asserts only `assertThrows` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/TokenValidationSecurityTest.java`
- OBSERVED: `JwtTokenTamperingUtilTest` asserts only `assertNotNull(exception.getEventType())` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtilTest.java`
- OBSERVED: `ExpirationValidatorTest` uses `now()±1h` / `now()+120s` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/ExpirationValidatorTest.java`
- OBSERVED: RS/HS confusion covered only by `WiredNegativePathAttackTest#shouldRejectHmacRsaAlgorithmConfusion` — swept `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/`
- OBSERVED: `JWKSKeyLoaderTest#shouldReportDuplicateKeyIdOnce` pins the warning only; `processKeys` is last-wins — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java` § `processKeys`
- OBSERVED: valueOf/ordinal tests remain in `LoaderStatusTest`, `EventCategoryTest`, `MeasurementTypeTest` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/LoaderStatusTest.java`
- OBSERVED (re-read 2026-11-05 at `origin/main` `7145aebb`, after PLAN-13 #861): PLAN-13 changed `WellKnownConfigurationConverter.java` (+28) and `JwksHttpContentConverter.java` (+16), two of the deliverable 6 sites, and moved `DUPLICATE_JWKS_KID` from identifier 168 to 171; it did NOT edit `JWKSKeyLoaderTest.java`. Line numbers for those converters are stale; re-read both at outline before writing the truncated-escape tests. No surface change.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/TokenValidationSecurityTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtilTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtil.java` (PLAN-23 fold: the open `S8786` at line 450; the helper TV-6 edits anyway)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/ExpirationValidatorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenSignatureValidatorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoaderTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/LoaderStatusTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/EventCategoryTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/metrics/MeasurementTypeTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/TestTokenHolder.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverter.java` and its test (deliverable 6, only if the site leaks — verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverter.java` and its test (deliverable 6 — verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/parser/JwksParser.java` and its test (deliverable 6; PLAN-13 may touch it first — verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` and its test (deliverable 6; also declared by PLAN-17 — verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-09 (exp boundary semantics), PLAN-13 (`JWKSKeyLoaderTest.java` id renumber)
- Overlaps with: PLAN-03 (`ExpirationValidatorTest.java`, single file)
- Adjacent to: `TokenSignatureValidatorAlgorithmTest` — unchanged

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-15-validation-test-gaps.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
