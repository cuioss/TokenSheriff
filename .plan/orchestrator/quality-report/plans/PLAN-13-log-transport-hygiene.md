# PLAN-13: Log-record hygiene, response-size ceiling, egress policy

epic: quality-report
workstream: WS-01

## Objective

Several LogRecords are called with arguments that do not match their templates (V-13); identifier
TokenSheriff-168 is used twice (V-14 / D-4); `doc/LogMessages.adoc` misses Quarkus Q-025 and Q-104
(D-3); `ParserConfig.maxPayloadSize` silently doubles as the ceiling for JWKS and discovery responses
(V-15); `EgressPolicy` Javadoc overclaims DNS-rebinding protection and misses CGNAT/0.0.0.0/broadcast
ranges (V-17a); `JWKS_KEYS_UPDATED` carries no issuer or key ids (V-17h). Closes V-13, V-14, V-15,
V-17a, V-17h, D-3, D-4.

## Deliverables

1. **V-13** — align `JWKS_LOAD_FAILED` and `JSON_PARSE_FAILED` templates and call sites (no nested `%s`
   in arguments); test asserting rendered messages.
2. **V-14 / D-4 / D-3** — renumber `DUPLICATE_JWKS_KID` to a free WARN identifier; add a test asserting
   identifier uniqueness across `TransportLogMessages` and `JWTValidationLogMessages`; update
   `doc/LogMessages.adoc` (168 row, Q-025, Q-104, range headers).
3. **V-15** — separate `maxHttpResponseSize` for transport converters (sane default), independent of
   token payload size; tests.
4. **V-17a** — `EgressPolicy`: block CGNAT 100.64/10, 0.0.0.0/8, broadcast; correct the "same fresh
   resolution" Javadoc to the actual guarantee; tests.
5. **V-17h** — `JWKS_KEYS_UPDATED` logs issuer and key ids (`getKeyIds()` from #803) at the right moment
   (not `LOADING` during init).

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

- OBSERVED: `LOGGER.error(ERROR.JWKS_LOAD_FAILED, errorMessage, getResolvedIssuer())` vs placeholder-less template — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` and `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java` § `JWKS_LOAD_FAILED`
- OBSERVED: `JSON_PARSE_FAILED` ("Failed to parse JSON from %s: %s") called with one arg / nested `%s` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverter.java`
- OBSERVED: `.identifier(168)` in both `TransportLogMessages` (`SSRF_EGRESS_BLOCKED_DISCOVERY`) and `JWTValidationLogMessages` (`DUPLICATE_JWKS_KID`) — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/TransportLogMessages.java`
- OBSERVED: `TokenSheriffQuarkusLogMessages` defines identifiers 25 and 104, absent from `doc/LogMessages.adoc` — read at `doc/LogMessages.adoc` § Quarkus INFO/WARN
- OBSERVED: `JwksHttpContentConverter` and `HttpWellKnownResolver` use `parserConfig.getMaxPayloadSize()` as content ceiling — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverter.java`
- HYPOTHESIS: identifier 169 is taken and 170+ free in the WARN range — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java` § WARN identifiers (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/TransportLogMessages.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverter.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/ParserConfig.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverter.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpWellKnownResolver.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressPolicy.java`
- OBSERVED: `doc/LogMessages.adoc`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigurationConverterTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/ParserConfigTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/JwksHttpContentConverterTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/EgressPolicyTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoaderTest.java`

## Dependencies and Sequencing

- Depends on: PLAN-01 and PLAN-07 (`HttpJwksLoader.java`)
- Overlaps with: PLAN-15 (`JWKSKeyLoaderTest.java`, single file)
- Adjacent to: `token-sheriff-quarkus-parent` log classes — read-only reference for D-3

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-13-log-transport-hygiene.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
