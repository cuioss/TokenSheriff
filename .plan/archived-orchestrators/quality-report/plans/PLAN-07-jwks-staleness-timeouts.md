# PLAN-07: JWKS staleness bound, re-discovery, and well-known timeouts

epic: quality-report
workstream: WS-01

## Objective

Once healthy, a JWKS loader serves its keys forever even if every refresh fails, and never re-runs
discovery, so a moved `jwks_uri` is never followed (V-10). In well-known mode, configured connect/read
timeouts are still silently dropped (V-7 remainder — TLS and retry were fixed in #748/#795). Closes V-7 (rem.), V-10.

## Deliverables

1. **V-7 remainder** — `HttpJwksLoaderConfig` records connect/read timeouts and forwards them to the
   well-known builder exactly like `sslContext`/`tlsVersions`; tests assert both the discovery handler
   and the derived JWKS handler carry them.
2. **V-10a** — track last successful refresh; after a configurable max staleness (or N consecutive
   failures) status degrades from `OK`, logged once with a LogRecord. New config knob with a safe default
   that preserves today's availability (document the trade-off).
3. **V-10b** — in well-known mode, repeated refresh failures trigger a re-discovery of `jwks_uri`.
   Test: discovery document changes `jwks_uri` → loader follows.

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
- Default behaviour must not drop all keys during a transient IdP outage; degradation is status/
  observability first, acceptance policy only when explicitly configured.

## Claim Labels

- OBSERVED: `connectTimeoutSeconds`/`readTimeoutSeconds` write only into `httpHandlerBuilder`, used only in the direct branch — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java` § `connectTimeoutSeconds`
- OBSERVED: `WellKnownConfigBuilder` already offers `connectTimeoutSeconds`/`readTimeoutSeconds` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfig.java` § builder
- OBSERVED: existing timeout tests cover `jwksUrl` (direct) only — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigTest.java`
- OBSERVED: `performBackgroundRefresh` only logs `BACKGROUND_REFRESH_FAILED`; discovery runs once from init via `resolveJWKSAdapter` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `performBackgroundRefresh`
- HYPOTHESIS: re-running `resolveJWKSAdapter` after init is safe w.r.t. the issuer-identity gate added in #818 — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `resolveIssuer` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java` (new staleness LogRecord — verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigWellKnownTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderSchedulerTest.java`
- HYPOTHESIS: `doc/LogMessages.adoc` (if a LogRecord is added — verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-01 (same class, status-ownership refactor first)
- Overlaps with: PLAN-13 (`HttpJwksLoader.java`, `JWTValidationLogMessages.java`, `doc/LogMessages.adoc`) — PLAN-13 runs after
- Adjacent to: Quarkus config keys for the new knob — expose in Quarkus only if trivial, else record as follow-up

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-07-jwks-staleness-timeouts.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
