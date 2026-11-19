<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug api-sheriff-seams`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug api-sheriff-seams`, and `git add` the result. -->

# Queue view: API Sheriff separation seams (0.9.10)

## START HERE

**Resume anchor**: === 2026-11-19: PLAN-01 to PLAN-12 SHIPPED (PLAN-12 = #925, ae493e00, landings/PLAN-12.md). The queue is EMPTY: nothing staged, nothing running, nothing emittable. The inbox is drained (empty, no sender closed). NEXT ACTION: the operator decides what is left; no plan is staged without the operator's word. Open after PLAN-12 (epic.md Open Defects, 'PLAN-12 landed'): (1) the Playwright flake accessibility-wcag-compliance.spec.js:60 is neither reproduced nor shown fixed, the ten runs and the new start-dev-environment.sh were never run (operator decision), needs Keycloak and dev mode on the operator machine; (2) layer 3, minor, reported not staged: isMalformedFraming accepts any IllegalArgumentException of the exchange future, CancellationException unmapped, package-level @Vetoed unread; (3) the plan's own rules: three framed test names, TokenEndpointClientTest and BearerTokenProducerTest over 500 lines, a second top-level test class, ADR-0031:196 and the PAR exception's compatibility framing; (4) doc lag: COMMONS-17 and ADR-0031 omit the JDK 27 ProtocolException form; (5) two review items declined (generated mapping inventory, hard-coded DPoP minimum of four); (6) eleven unfixed code-review advisories. Not in any plan (marketplace side or the lessons store): the no_sanction refusal (seven plans), the weakened_test ask (six plans), the code-review validator and the closing-round reuse rule (lesson 2026-11-19-07-008), the scope-creep guard, the review_completeness flag families, the docs-versus-executor skew (lesson 2026-11-18-12-002; executor 0.1.2133, registry 0.1.2139/0.1.2155; steward upgrade clears the indeterminate restart verdict), the second phase_handshake drift, and the lessons missing from the store (2026-11-16-06-001, 2026-11-02-14-001 with its review overdue, 2026-11-11-12-002, -003, 2026-11-15-18-002 and others). Settled narrative for PLAN-10, PLAN-11 and PLAN-12 can be relocated by cleanup. Options: stage a further plan, run cleanup, or close the epic. Target release 0.9.10; API Sheriff PLAN-34 waits for it. ===
**Phase**: orchestrating
**Queue** (staged, in order):
- (empty)
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

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| — | (empty) | — | — | — | — |
