<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug api-sheriff-seams`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug api-sheriff-seams`, and `git add` the result. -->

# Queue view: API Sheriff separation seams (0.9.10)

## START HERE

**Resume anchor**: === 2026-11-18: ALL ELEVEN PLANS SHIPPED (PLAN-10 is #920, merge dd68517b, landings/PLAN-10.md; PLAN-11 is #921, d8aba512). The queue is EMPTY: nothing staged, nothing running, inbox empty. Cleanup ran: settled narrative (PLAN-01 to PLAN-09 landing and residual Open Defects, one retired Watch) is in settled.md with pointers in epic.md. NEXT ACTION: settle the open items with the operator, then close. OPEN ITEMS NEEDING THE OPERATOR'S WORD (Open Defects 'PLAN-10 landed' and 'PLAN-11 landed'): the JwtPropertyKeys reflective DPOP_SUB_PROPERTIES list has no empty-result guard (a major risk by reading, native behaviour not shown; also a NullPointerException for a non-constant field declared after it, and a changed sort order); stale TokenSheriff-010 in openid-discovery.adoc; the ClientConfigurationTest split is not a pure move; an untested IllegalStateException branch and a recursion hazard in AuthorizationRequestBuilder; the interrupt-edge text; five #921 review items the lane left unchanged; no per-call-site Content-Length tests (ParClient, UserInfoClient, DiscoveryResolver); five client test classes unmapped in test-impl-mapping.adoc; the Playwright flake not run (E2E resolves a stale ~/.m2 snapshot, lesson 2026-11-18-12-001); COMMONS-17's first-allocation term unquantified; ADR-0031 history framing and a missing itemisation; the wide IllegalArgumentException catch; the PAR-endpoint decision and the PLAN-04 nitpicks not visible in the diff; the second phase_handshake drift (script_failure Watch). NOT IN ANY PLAN (marketplace side or the lessons store): the no_sanction refusal (six plans), the weakened_test carry-forward ask (five plans), the code-review validator, the scope-creep guard, the review_completeness flag families, the docs-versus-executor skew (lesson 2026-11-18-12-002; the executor is 0.1.2133, the registry pins 0.1.2139/0.1.2155, which also makes the restart verdict indeterminate), and the lessons missing from the store (2026-11-16-06-001, 2026-11-02-14-001 with its review overdue, 2026-11-11-12-002, -003, 2026-11-15-18-002 and others). Target release 0.9.10; API Sheriff PLAN-34 waits for it. ===
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

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| — | (empty) | — | — | — | — |
