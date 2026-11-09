<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report`, and `git add` the result. -->

# Queue view: Quality report remediation

## START HERE

**Resume anchor**: RESTART HERE. 2026-11-09: PLAN-01 to PLAN-25 SHIPPED (PLAN-25 as #883 + #884, landing in landings/PLAN-25.md); inbox drained (60 messages, 15 lessons promoted). OPEN, asks the operator: PLAN-25 inverted all four mailbox decisions (JWE limit widened to 32 KiB, explicit null refused, default-on client egress policy, AGENTS.md says should-400/must-split-1000) - do the in-run answers stand? Also reported: DPoP replay cache unbounded and CPU-amplifying under a jti flood, DPoP jti/htm/htu logged unsanitised; fix plan only on the operator's word. STAGED: PLAN-26 (key-id contract test plus insufficient_scope parser): overlap with PLAN-25 is gone, but its claims were stamped at f85c5ab3 and must be re-grounded at aeb3fa49 (PLAN-25 touched StepUpChallengeParser, HeaderValues, client docs) before next emits it; emit only on the operator's word. Then analyze after PLAN-26 merges and close. OPERATOR-OWNED: the API Sheriff reply (later, if released, after the decision above), eight unsent upstream addenda, registry_parity not_ready, stale branches, Dependabot PR #872, GitHub #358 and #182 non-goals. Standing policy: tool findings fixed immediately; decide alone above 90 percent confidence, else ask one question at a time with 4 alternatives; land only when asked; use the absolute script path; auto_emit is false.
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-26 (WS-01)
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

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| 1 | PLAN-26 | WS-01 | staged | doc/client/README.adoc; doc/client/requirements.adoc; doc/client/specification/step-up-and-logout.adoc; doc/client/threat-model.adoc; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java; token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParser.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeAttackTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParserTest.java; token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParserTest.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractSupport.java; token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java | plan-marshall |
