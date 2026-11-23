<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug client-framework-aspects`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug client-framework-aspects`, and `git add` the result. -->

# Queue view: Client framework aspects: session and BFF layers above the engine

## START HERE

**Resume anchor**: PLAN-12 (documentation review, consolidation, ADR reduction; operator approves the target structure) emitted and awaiting operator-confirmed launch; record launched via queue --transition PLAN-12 --status launched once started. WS-05 runs FIRST: then PLAN-13 (COMMONS+VALIDATION renumber + guard), then PLAN-14 (CLIENT renumber); every other spec depends on PLAN-14. After PLAN-12 and after PLAN-14: /plan-orchestrator cleanup to re-ground PLAN-01..11 (doc paths move, requirement ids change). Then PLAN-01 + PLAN-02. Ledger landed on main via #951.
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-01 (WS-01)
2. PLAN-02 (WS-02)
3. PLAN-03 (WS-02)
4. PLAN-04 (WS-02)
5. PLAN-05 (WS-03)
6. PLAN-06 (WS-03)
7. PLAN-07 (WS-03)
8. PLAN-08 (WS-04)
9. PLAN-09 (WS-04)
10. PLAN-10 (WS-04)
11. PLAN-11 (WS-02)
12. PLAN-12 (WS-05)
13. PLAN-13 (WS-05)
14. PLAN-14 (WS-05)

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| 1 | PLAN-01 | WS-01 | staged | doc/README.adoc; doc/adr/; doc/client/README.adoc; doc/client/architecture.adoc; doc/client/threat-model.adoc | plan-marshall |
| 2 | PLAN-02 | WS-02 | staged | doc/client/requirements.adoc; doc/client/specification/step-up-and-logout.adoc; doc/developer/test-impl-mapping.adoc; doc/validation/requirements.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/BackChannelLogoutHandler.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/IdTokenContent.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenReplayGuard.java | plan-marshall |
| 3 | PLAN-03 | WS-02 | staged | doc/LogMessages.adoc; doc/client/requirements.adoc; doc/client/specification/retrieval-flow.adoc; doc/client/threat-model.adoc; doc/developer/test-impl-mapping.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/pending/; token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/native-image.properties; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/pending/ | plan-marshall |
| 4 | PLAN-04 | WS-02 | staged | doc/LogMessages.adoc; doc/client/requirements.adoc; doc/client/specification/step-up-and-logout.adoc; doc/client/specification/token-handling.adoc; doc/developer/test-impl-mapping.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/RefreshDisposition.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/LogoutStep.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/ | plan-marshall |
| 5 | PLAN-05 | WS-03 | staged | bom/pom.xml; doc/README.adoc; doc/developer/test-impl-mapping.adoc; doc/session/; pom.xml; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java | plan-marshall |
| 6 | PLAN-06 | WS-03 | staged | doc/LogMessages.adoc; doc/developer/test-impl-mapping.adoc; doc/session/ | plan-marshall |
| 7 | PLAN-07 | WS-03 | staged | doc/developer/test-impl-mapping.adoc; doc/session/ | plan-marshall |
| 8 | PLAN-08 | WS-04 | staged | bom/pom.xml; token-sheriff-quarkus-parent/pom.xml; token-sheriff-quarkus-parent/token-sheriff-bff-quarkus-deployment/; token-sheriff-quarkus-parent/token-sheriff-bff-quarkus/ | plan-marshall |
| 9 | PLAN-09 | WS-04 | staged | doc/bff/; doc/developer/test-impl-mapping.adoc; token-sheriff-quarkus-parent/token-sheriff-bff-quarkus-deployment/; token-sheriff-quarkus-parent/token-sheriff-bff-quarkus/ | plan-marshall |
| 10 | PLAN-10 | WS-04 | staged | doc/README.adoc; doc/bff/; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/ | plan-marshall |
| 11 | PLAN-11 | WS-02 | staged | doc/client/requirements.adoc; doc/client/specification/retrieval-flow.adoc; doc/client/specification/token-handling.adoc; doc/developer/test-impl-mapping.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestOptions.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ClientCredentialsFlow.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/; token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java; token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientRuntimeConfig.java | plan-marshall |
| 12 | PLAN-12 | WS-05 | staged | AGENTS.md; README.md; benchmarking/README.adoc; benchmarking/benchmark-core/README.adoc; benchmarking/benchmark-integration-wrk/README.adoc; benchmarking/benchmarking-common/README.adoc; benchmarking/doc/; doc/; token-sheriff-client/README.adoc; token-sheriff-client/doc/; token-sheriff-quarkus-parent/README.adoc; token-sheriff-quarkus-parent/doc/; token-sheriff-quarkus-parent/token-sheriff-client-quarkus/README.adoc; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/README.adoc; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/README.adoc; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc; token-sheriff-validation/README.adoc; token-sheriff-validation/doc/ | plan-marshall |
| 13 | PLAN-13 | WS-05 | staged | benchmarking/README.adoc; benchmarking/doc/; doc/; token-sheriff-quarkus-parent/doc/; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/; token-sheriff-validation/ | plan-marshall |
| 14 | PLAN-14 | WS-05 | staged | doc/; token-sheriff-client/; token-sheriff-quarkus-parent/doc/; token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/; token-sheriff-quarkus-parent/token-sheriff-client-quarkus/; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/ | plan-marshall |
