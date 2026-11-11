# PLAN-05: Make ECDH-ES JWE work end-to-end (or stop advertising it)

epic: quality-report
workstream: WS-01

## Objective

ECDH-ES is advertised as a supported JWE key-management algorithm, but the production parser types
the `epk` header as a String while RFC 7518 defines it as a JSON object, so every ECDH-ES token is
rejected before decryption; unit tests construct the header by hand and never noticed (V-1, gap VAL 8).
Fix the header model so ECDH-ES works through the production parser, prove it with an end-to-end
test, and add the RSA-OAEP random-CEK (Bleichenbacher-style) mitigation (V-17g). Closes V-1, V-17g, gap VAL 8.

## Deliverables

1. **V-1** — `JwtHeader.epk` becomes an object (JWK map / typed key) in the compiled-JSON header;
   `JweDecryptor` consumes it directly. If the fix proves infeasible at outline, the fallback is to
   remove ECDH-ES from the default `JweAlgorithmPreferences` and document it — decide and record.
2. **gap VAL 8** — end-to-end test: `JweTestTokenFactory.createEcdhEsJweWrappedAccessToken` →
   `NonValidatingJwtParser` / `TokenValidator` → accepted; and `JweDecryptorTest` stops bypassing
   the production parser for header construction.
3. **V-17g** — RSA-OAEP: on CEK unwrap failure, continue with a random CEK so failure timing/behaviour
   is uniform (RFC 7516 §11.5); test asserts identical rejection event for bad-unwrap vs bad-tag.

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
- JWE decryption failures must keep producing the same typed rejection; no new oracle.

## Claim Labels

- OBSERVED: `JwtHeader` declares `String epk` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/JwtHeader.java` § `epk`
- OBSERVED: `JweDecryptor` reads `header.getEpk()` as a string and re-deserializes it — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` § `parseEcPublicKeyFromJwk`
- OBSERVED: `JweAlgorithmPreferences` advertises `ECDH-ES` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweAlgorithmPreferences.java`
- OBSERVED: `JweDecryptorTest.parseHeader` extracts header fields by hand; no test routes an ECDH-ES token through `NonValidatingJwtParser` — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptorTest.java` § `parseHeader`
- HYPOTHESIS: DSL-JSON compiled converters can bind a nested object field on `JwtHeader` without a custom converter — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/JwtHeader.java` § compiled-json annotations (verify-at-outline)
- HYPOTHESIS: the RSA-OAEP unwrap path currently throws distinguishably before tag verification — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java` § RSA-OAEP unwrap (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/JwtHeader.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweAlgorithmPreferences.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/json/JwtHeaderJweTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorJweTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParserJweTest.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-09 (`NonValidatingJwtParser.java` trailing-dot fix) — PLAN-09 runs after this plan
- Adjacent to: Quarkus JWE config (Q-6, PLAN-08) — unchanged here

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-05-jwe-ecdh-es.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
