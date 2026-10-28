# PLAN-17: Javadoc usage examples that compile and run

epic: quality-report
workstream: WS-05

## Objective

Seven Javadoc usage examples in validation and Quarkus classes would not compile or would throw as
written (J-1…J-7) — several were edited by #817/#818 and are still broken. Prose overclaims or omits
(EdDSA, "blacklisting", cookie token extraction: J-9, J-10), `@since` tags are missing (J-11), and small
nits remain (J-12). Rewrite every example against the API at HEAD (after PLAN-01/05/07 have landed).
Closes J-1…J-7, J-9…J-12.

## Deliverables

1. **J-1 / J-4 / J-6 / J-9** — `IssuerConfig` and `TokenValidator` examples: drop the non-existent
   `issuerConfig.initJWKSLoader(..)`, replace `SignatureAlgorithmPreferences.builder()` with the real
   constructor, add `issuerIdentifier` where `build()` requires it, link `JwsAlgorithm` instead of a
   stale algorithm list and drop "blacklisting".
2. **J-2 / J-7** — `JwksLoaderFactory` example arity; rewrite `jwks/http/package-info.java` around the real
   `HttpJwksLoader` + cui-http adapters (no `JwksHttpClient`/`JwksCacheManager`/`BackgroundRefreshManager`),
   example calls `initJWKSLoader` before use.
3. **J-3** — `NonValidatingJwtParser` examples use `DecodedJwt decode(String)` with try/catch and `rawToken()`.
4. **J-5 / J-10** — `DiscoverableTokenValidationRule` claim access; bearer-token docs mention
   `sheriff.token.header=Cookie` and the cookie name (`BearerTokenProducer`, `BearerToken`, `BearerAuth`).
5. **J-11 / J-12** — `@since` in the five listed classes; `StringSplitterMapper` parameter name;
   "Portal environment" wording in `validation/package-info.java`.

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
- Javadoc-only plan: no behaviour change. Prefer `{@snippet}` or examples mirrored by an existing test
  where feasible so they cannot rot again; verify each example against the current signature.

## Claim Labels

- OBSERVED: `IssuerConfig` example calls `issuerConfig.initJWKSLoader(new SecurityEventCounter())`; no such method — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java` § class Javadoc
- OBSERVED: `createHttpLoader(config, securityEventCounter)` vs signature `createHttpLoader(HttpJwksLoaderConfig)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderFactory.java`
- OBSERVED: examples use `Optional<DecodedJwt>` / `getRawToken()`; real API `DecodedJwt decode(String)` throws, record accessor `rawToken()` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java`
- OBSERVED: `SignatureAlgorithmPreferences` has two constructors, no builder — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/security/SignatureAlgorithmPreferences.java`
- OBSERVED: `ClaimName.of` does not exist (`fromString` returns `Optional`) — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/validation/DiscoverableTokenValidationRule.java`
- OBSERVED: `HttpJwksLoaderConfig` examples without `issuerIdentifier` throw on `build()` in `TokenValidator`, `jwks/http/package-info`, `JwksLoaderFactory` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` § `build`
- OBSERVED: `jwks/http/package-info.java` describes `JwksHttpClient`, `JwksCacheManager`, `BackgroundRefreshManager`; none exist — swept the repository
- HYPOTHESIS: after PLAN-01/07 the `HttpJwksLoader` public surface used in examples is unchanged — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` public methods (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfig.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderFactory.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/package-info.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/StringSplitterMapper.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/package-info.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/validation/DiscoverableTokenValidationRule.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/BearerTokenProducer.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerToken.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerAuth.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/TestApplication.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/JwtValidationEndpoint.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/MockJwtValidationEndpoint.java`

## Dependencies and Sequencing

- Depends on: PLAN-01, PLAN-05, PLAN-07 (examples must reflect final APIs); PLAN-04 and PLAN-08 (processor / `BearerAuth` files)
- Overlaps with: many code plans via Javadoc-only edits — run late in the queue, never beside a plan touching the same files
- Adjacent to: AsciiDoc examples — PLAN-18/19

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-17-javadoc-examples.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
