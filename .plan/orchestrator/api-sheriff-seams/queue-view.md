<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug api-sheriff-seams`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug api-sheriff-seams`, and `git add` the result. -->

# Queue view: API Sheriff separation seams (0.9.10)

## START HERE

**Resume anchor**: === 2026-11-21 (after staging PLAN-16): PLAN-01 to PLAN-15 SHIPPED; PLAN-16 (statements and fixture residuals, plans/PLAN-16-statements-and-fixture-residuals.md, four deliverables, WS-02) is STAGED and EMITTED, NOT recorded launched or running: the operator has not said so. PLAN-17 (Sonar debt to zero) was RETIRED unemitted (it rested on sonar_rest search, which lists resolved issues; the project has one open Sonar issue, java:S8714 in BackChannelHttpBodyPhaseTest, which is PLAN-16's: its merge brings the count to 0). FIRST ACTION IN A FRESH SESSION: look for a live plan implement-plan-16-statements-and-fixture-residuals (or similar) and ask the operator whether PLAN-16 is running; on the word 'PLAN-16 is running' record launched then running and stamp plan_marshall_plan_id read from the plan, not typed from memory. When PLAN-16 ships: analyze (drain the inbox, three-layer leaf, lessons), read the Sonar open count with sonar read_open_issues (not sonar_rest search), land, then close the epic. DO NOT run the marshall-steward upgrade while PLAN-16 is in flight (the operator has not said to run it; restart verdict indeterminate on registry parity only). NOT IN PLAN-16: the Playwright ten runs (operator's machine), the marketplace-side findings (no_sanction eight plans, weakened_test nine, the review gate, the skew six plans, stale marshal.json, missing lessons). Target release 0.9.10; API Sheriff PLAN-34 waits for it. ===
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-16 (WS-02)
- PLAN-01 (WS-01) — plan=client-native-image-registration — PR #898 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-01) — plan=client-authorization-request-seam — PR #901 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=plan-03-client-key-material-config-validators — PR #915 — landing=landings/PLAN-03.md — status: shipped
- PLAN-04 (WS-01) — plan=client-refresh-verification-discovery-cache — PR #907 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-02) — plan=plan-05-validation-public-seams — PR #899 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-02) — plan=jwks-loader-gateway-needs — PR #906 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-02) — plan=plan-07-public-helper-hygiene — PR #908 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-01) — plan=discovery-cache-verifier-hardening — PR #912 — landing=landings/PLAN-08.md — status: shipped
- PLAN-09 (WS-02) — plan=plan-09-public-helper-residuals — PR #916 — landing=landings/PLAN-09.md — status: shipped
- PLAN-10 (WS-01) — plan=plan-10-client-and-cache-residuals — PR #920 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-02) — plan=plan-11-final-residuals-sweep — PR #921 — landing=landings/PLAN-11.md — status: shipped
- PLAN-12 (WS-02) — plan=plan-12-open-items-sweep — PR #925 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-02) — plan=plan-13-final-finalize — PR #931 — landing=landings/PLAN-13.md — status: shipped
- PLAN-14 (WS-02) — plan=implement-plan-14-closing-sweep — PR #938 — landing=landings/PLAN-14.md — status: shipped
- PLAN-15 (WS-02) — plan=plan-15-final-fix-sweep — PR #940 — landing=landings/PLAN-15.md — status: shipped
- PLAN-17 (WS-02) — status: retired

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| 1 | PLAN-16 | WS-02 | staged | AGENTS.md; doc/commons/requirements.adoc; doc/developer/test-impl-mapping.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/CachingDiscoveryResolverStalePolicyTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/BackChannelClientExchangeFailureTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpBodyPhaseTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelHttpExchangeTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/RawHttpServer.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/RawHttpServerTest.java; token-sheriff-quarkus-parent/e-2-e-playwright/scripts/start-dev-environment.sh; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/BearerScopeValidationVetoedPackageBuildStepTest.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/JwksParserConfigBootTest.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/ParserConfig.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtilReproductionTest.java | plan-marshall |
