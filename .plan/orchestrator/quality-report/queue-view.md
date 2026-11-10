<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report`, and `git add` the result. -->

# Queue view: Quality report remediation

## START HERE

**Resume anchor**: RESTART HERE. 2026-11-10: PLAN-01 to PLAN-27 SHIPPED. PLAN-28 epic-cleanup-sweep is RUNNING in the plan lifecycle (operator-confirmed 2026-11-10; item 7 is (a) to (f); the lifecycle may cut it at outline along (1), (2)+(4), (3), (5), (6), (7)). Ledger landed on main through cleanup (see decision log). NEXT: wait for the operator's word that PLAN-28 has a PR / is merged, then run analyze (landing-check, three-layer analysis, reconcile epic.md leftovers it carried), then close (freeze into history.md). Cleanup ran: corpus clean (27 shipped, 1 running), invariants ok, settled-narrative relocation deferred, archive drain refused (no quiescence signal), restart verdict not_ready only because PLAN-28 runs and registry_parity is behind (plan-marshall meta-repo repin, ADR-020). NOT in PLAN-28 (see its Not carried): upstream-owned items, the API Sheriff reply and release note (operator, later if released; the parser, contract test and StepUp tightening are on main and in no release), nine unsent addenda, stale branches (incl. local plan-25-full-state), Dependabot PR #872, GitHub #358 and #182 non-goals, the docs-only Sonar skip (cuioss-organization), TC-9 (add only on the operator's word). Standing policy: decide alone above 90 percent confidence, else ask one question at a time with 4 alternatives; use the absolute script path; auto_emit is false; land only when asked.
**Phase**: orchestrating
**Running**:
- PLAN-28 (WS-05)
**Queue** (staged, in order):
- (empty)
- PLAN-01 (WS-01) — plan=plan-01-jwks-loader-lifecycle — PR 830 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-02) — plan=implement-plan-02-refresh-family-rotation — PR 832 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=implement-plan-03-cache-hit-policy — PR 833 — landing=landings/PLAN-03.md — status: shipped
- PLAN-04 (WS-03) — plan=plan-04-quarkus-startup-health — PR 834 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-01) — plan=plan-05-jwe-ecdh-es — PR 835 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-02) — plan=plan-06-callback-logout-hardening — PR 840 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-01) — plan=plan-07-jwks-staleness-timeouts — PR 844 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-03) — plan=plan-08-quarkus-config-cdi — PR 850 — landing=landings/PLAN-08.md — status: shipped
- PLAN-09 (WS-01) — plan=plan-09-dpop-parser-hardening — PR 851 — landing=landings/PLAN-09.md — status: shipped
- PLAN-10 (WS-02) — plan=plan-10-dpop-binding-stepup — PR 856 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-01) — plan=plan-11-audience-semantics — PR 853 — landing=landings/PLAN-11.md — status: shipped
- PLAN-12 (WS-04) — plan=plan-12-benchmark-build — PR 846 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-01) — plan=plan-13-log-transport-hygiene — PR 861 — landing=landings/PLAN-13.md — status: shipped
- PLAN-14 (WS-02) — plan=plan-14-client-adversarial-tests — PR 862 — landing=landings/PLAN-14.md — status: shipped
- PLAN-15 (WS-01) — plan=plan-15-validation-test-gaps — PR 867 — landing=landings/PLAN-15.md — status: shipped
- PLAN-16 (WS-03) — plan=plan-16-keycloak-it-assertions — PR 868 — landing=landings/PLAN-16.md — status: shipped
- PLAN-17 (WS-05) — plan=plan-17-javadoc-examples — PR 878 — landing=landings/PLAN-17.md — status: shipped
- PLAN-18 (WS-05) — plan=plan-18-doc-accuracy — PR 877 — landing=landings/PLAN-18.md — status: shipped
- PLAN-19 (WS-05) — plan=plan-19-doc-structure-hygiene — PR 879 — landing=landings/PLAN-19.md — status: shipped
- PLAN-20 (WS-02) — plan=plan-20-rewrite-marker-cleanup — PR 847 — landing=landings/PLAN-20.md — status: shipped
- PLAN-21 (WS-01) — plan=plan-21-residual-behaviour-fixes — PR 849 — landing=landings/PLAN-21.md — status: shipped
- PLAN-22 (WS-05) — plan=plan-22-residual-docs-tests-adrs — PR 863 — landing=landings/PLAN-22.md — status: shipped
- PLAN-23 (WS-05) — plan=implement-plan-23-sonar-backlog-cleanup — PR 855 — landing=landings/PLAN-23.md — status: shipped
- PLAN-24 (WS-01) — plan=plan-24-client-id-strict-typing — PR 876 — landing=landings/PLAN-24.md — status: shipped
- PLAN-25 (WS-05) — plan=plan-25-epic-leftover-sweep — PR 883,884 — landing=landings/PLAN-25.md — status: shipped
- PLAN-26 (WS-01) — plan=jwks-key-ids-contract-pin — PR 888 — landing=landings/PLAN-26.md — status: shipped
- PLAN-27 (WS-05) — plan=plan-27-test-size-guideline-restore — PR 887 — landing=landings/PLAN-27.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| 1 | PLAN-28 | WS-05 | running | AGENTS.md; CLAUDE.md; doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc; doc/client/requirements.adoc; doc/client/threat-model.adoc; doc/resources/diagrams/client-auth-code-pkce.svg; doc/validation/security-reference.adoc; doc/validation/specification/token-decryption.adoc; doc/validation/threat-model.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeAttackDatabaseTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParserTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParserTest.java; token-sheriff-quarkus-parent/e-2-e-playwright/scripts/stop-dev-environment.sh; token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/lib-docker-compose.sh; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/stop-integration-container.sh; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/; token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheck.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheck.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java; token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorStartupInitializerTest.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopConfig.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidator.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopReplayProtection.java; token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java; token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/reflect-config.json; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizerTest.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogRecordSanitizationGuardTest.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/dpop/; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/IdentifierClaimNullEquivalenceTest.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java | plan-marshall |
