<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug quality-report`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug quality-report`, and `git add` the result. -->

# Queue view: Quality report remediation

## START HERE

**Resume anchor**: RESTART HERE. 2026-11-11: ALL 28 PLANS SHIPPED (PLAN-28 = #892 c7b2675b, analyzed: landings/PLAN-28.md; PLAN-26 = #888, PLAN-27 = #887). NOTHING RUNNING, NOTHING STAGED, inbox empty (PLAN-28 messages 21 of 21 archived: 6 lessons promoted 2026-11-11-12-001 to -006, 14 folded, the landing reconciled). The ledger is landed on main up to this analysis (#889, #890); the analysis itself lands in the land that follows this anchor. Restart check: every signal ready except registry_parity (the registry pins behind the executor; the repin command exists only in the plan-marshall meta-repository, ADR-020; not fixable here). NEXT, on the operator word only: (1) the close verb (freeze epic.md into history.md), then the archive verb; or (2) first a doc-and-test plan for the minor residue (one Open Defect, Minor residue after PLAN-28: unquote Javadoc, JweDecryptor Javadoc, uncovered format characters, the pre-clean stop wording, the global eviction monitor, test pins, stale PR body); or (3) first the settled-narrative relocation of epic.md (needs confirmation, deferred twice). HELD on the operator word, in no plan: the API Sheriff reply and release note (parser, contract test, StepUp tightening, STRICT unquoted-scope refusal and the sanitiser change are on main and in no release); nine unsent upstream addenda; the e2e teardown hang Watch (recurred on the merge queue; owner cuioss-organization per the lane); TC-9 (add only on the operator word); upstream-owned items (effort stamp, incomplete landing facts, lesson routing: plan-marshall-owned lessons filed with the foreign-store override had vanished from the store by 2026-11-11, cause unknown); stale branches (incl. local plan-25-full-state); Dependabot PR #872; GitHub #358 and #182 non-goals; the docs-only Sonar skip (cuioss-organization). Standing policy: decide alone above 90 percent confidence, else ask one question at a time with 4 alternatives; absolute script path (the Bash cwd moves); auto_emit false; land only when asked.
**Phase**: orchestrating
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
- PLAN-28 (WS-05) — plan=plan-28-epic-cleanup-sweep — PR 892 — landing=landings/PLAN-28.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| — | (empty) | — | — | — | — |
