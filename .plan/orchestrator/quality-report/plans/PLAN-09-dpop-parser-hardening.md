# PLAN-09: DPoP and token-parser hardening edges

epic: quality-report
workstream: WS-01

## Objective

Close five validation hardening edges: the DPoP path lacks the ECDSA psychic-signature range check
(CVE-2022-21449) that the access-token path has (V-11); the DPoP replay cache stores the `jti` before
the proof is bound to the token, so unauthenticated proofs can fill it (V-12); `split("\\.")` accepts
trailing-dot tokens (V-17b); `exp` is accepted at exactly `exp + skew` (V-17c); EC JWK `alg` is not
cross-checked against the curve (V-17e). Closes V-11, V-12, V-17b, V-17c, V-17e.

## Deliverables

1. **V-11** — move the ECDSA component range check into a shared place
   (`SignatureVerificationUtil` or `EcdsaSignatureFormatConverter`) used by both token and DPoP paths;
   all-zero ES256 DPoP proof test.
2. **V-12** — `DpopProofValidator` performs `ath`/`htu`/`htm` and `cnf.jkt` checks first and
   `checkAndStore(jti)` last; test: thumbprint-mismatch proof leaves no replay entry.
3. **V-17b** — `split("\\.", -1)` in `NonValidatingJwtParser` and `DpopProofValidator`; tests for
   trailing-dot and empty-segment tokens.
4. **V-17c** — `ValidationContext.isExpired` treats `now == exp + skew` as expired; fixed-clock boundary test.
5. **V-17e** — `KeyProcessor` rejects/ignores an EC JWK whose `alg` does not match its `crv`; test.

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

## Claim Labels

- OBSERVED: `TokenSignatureValidator` calls private `checkEcdsaComponentRange`; `DpopProofValidator.verifyDpopSignature` calls `SignatureVerificationUtil.verifySignature` directly — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidator.java` § `verifyDpopSignature`
- OBSERVED: `validateDpopClaims` runs `replayProtection.checkAndStore(jti)` before the `cnf.jkt` comparison — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidator.java` § `validateDpopProofHeaderAndSignature`
- OBSERVED: `token.split("\\.")` in `NonValidatingJwtParser` and `DpopProofValidator` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java`
- OBSERVED: `isExpired` uses `exp.plusSeconds(skew).isBefore(now)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/context/ValidationContext.java` § `isExpired`
- OBSERVED: no `KeyProcessorTest` exists; closest are `JwksParserTest`, `KeyProcessorRsaDefaultAlgorithmTest` — swept `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/parser/`
- HYPOTHESIS: the `exp` boundary change does not alter the cache-hit expiry semantics owned by PLAN-03 — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` § hit check (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidator.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/TokenSignatureValidator.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/context/ValidationContext.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/parser/KeyProcessor.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidatorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParserTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/context/ValidationContextTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/parser/JwksParserTest.java`

## Dependencies and Sequencing

- Depends on: PLAN-05 (`NonValidatingJwtParser.java`)
- Overlaps with: PLAN-15 (test-gap plan adds fixed-clock tests near the same validators) — PLAN-15 after
- Adjacent to: `DpopReplayProtection.java` — reordering suffices; size-cap change not required

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-09-dpop-parser-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
