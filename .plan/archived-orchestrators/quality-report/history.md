# History: Quality report remediation (closed 2026-11-11)

slug: quality-report

> Frozen record of the epic at close. Written by the `close` verb from the rendered ledger blocks (never a hand-written queue). `epic.md`, `settled.md`, `landings/`, `plans/`,
> `workstreams/`, `logs/` and the archived inbox stay on disk untouched: close freezes, never deletes, and that tree is the audit record. Where this file and `epic.md` differ on a
> detail, `epic.md` and the landing reports carry the evidence.

## Vision as pursued

Remediate the still-valid findings of the full-repository quality review (snapshot of revision `d6b927c`, 2026-08-15, 82 findings across validation, client, Quarkus/benchmarking/build,
tests, AsciiDoc and Javadoc). The report was moved verbatim into this epic's `source-report/`, where it is frozen source material. Because the snapshot predates later work, every finding
was re-grounded against HEAD before staging; only findings that still hold were staged as plans. Done at the epic level means: every finding has a recorded disposition (fixed by a plan
of this epic, already fixed at HEAD, or refuted), and each fixing PR names the finding IDs it closes.

The epic ran past its first 19 plans: PLAN-20 to PLAN-25 swept residuals and landing leftovers, PLAN-26 answered two downstream requests (the `getKeyIds()` contract test and the RFC 6750
`insufficient_scope` parser), PLAN-27 reverted four decisions PLAN-25 had inverted (the explicit-null refusal and the test-size rule), and PLAN-28 swept the epic's own Open Defects and Watches
into one cleanup plan.

## Terminal queue state (rendered by `resume-summary` at close)

**Summary block, verbatim:**

```text
**Resume anchor**: CLOSING 2026-11-11, on the operator request (is the epic ready to archive, if so cleanup and archive): all 28 plans shipped, nothing running, parked or staged, inbox empty, compaction invariants ok. The plan-marshall-owned lessons were consumed (operator), so their absence from the lesson store is expected. Carried forward as leads in history.md: the minor residue after PLAN-28, the API Sheriff reply and release note, nine unsent upstream addenda, the e2e teardown hang, upstream-owned items, TC-9, stale branches, Dependabot PR 872, GitHub 358 and 182 as non-goals, the docs-only Sonar skip. Restart check: registry_parity not_ready (upstream, plan-marshall meta-repository only).
**Phase**: orchestrating
**Inbox (derived)**: 0 queued, 261 archived
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
```

**Ordered Queue block, verbatim (the live queue is empty by construction: every row is terminal):**

```text
| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| — | (empty) | — | — | — | — |
```

## Queue outcome per plan

- **Shipped: 28** (PLAN-01 to PLAN-28; PR numbers and landing reports as listed above).
- **Closed unshipped: 0.** **Parked: 0.** No row is `launched`, `running` or `staged`; the 28 queue rows and the 28 spec files reconcile in both directions (`corpus enumerate`).
- Inbox: 0 queued, 261 archived, 0 invalid; the last drain (PLAN-28) archived 21 of 21 messages (6 lessons promoted, 14 folded, the landing reconciled).
- Compaction invariants at close: `queue_spec_bidirectional` ok, `relocated_pointer_reachable` ok (8 pointers into `settled.md`).

## Decision record

The decisions are in `epic.md` § Decisions (dated, per plan: the re-groundings, the operator's choices on the PLAN-25 mailbox, the 500-line test guideline, the DPoP soft cap, the STRICT unquoted-scope
refusal, each plan's analysis) and in `logs/decision.log`; settled narrative is in `settled.md`. Last decisions: 2026-11-10 PLAN-28 emitted and started on the operator's word after the ledger was
landed first; 2026-11-11 PLAN-28 analyzed (`landings/PLAN-28.md`); 2026-11-11 the cleanup pass found the epic ready (all rows shipped, inbox empty, invariants ok), and the epic was closed and archived on
the operator's request.

## Carried-forward leads (not silently dropped)

Retired by plans or analyses of this epic and so NOT carried: the PLAN-25 inverted-mailbox defect, the DPoP replay-cache and logging defects, the PLAN-27 documentation defects, the PLAN-26 looseness
defect, the PLAN-25 no-evidence Watch (closed by PLAN-28, partly by evidence only: the lane's claims were not re-run), the PLAN-27 null-pin Watch and the PLAN-26 re-grounding Watch.

Still open, with the owner:

1. **Minor residue after PLAN-28** (Open Defect in `epic.md`, `landings/PLAN-28.md` L2-1 to L2-7, L3-1 to L3-5): the LENIENT `unquote` Javadoc, the `JweDecryptor` Javadoc, the format characters
   `ErrorTextSanitizer` leaves un-neutralised, the pre-clean stop wording in `AGENTS.md` and the integration-tests README, the global `evictionLock` held across the replay-cache sweep, several
   weak or missing test pins, the stale PR body. Project-owned; a doc-and-test plan only on the operator's word.
2. **API Sheriff reply and release note** (Watch, operator-owned): the loader status contract, the `getKeyIds()` pin, the `insufficient_scope` parser and the StepUp tightening, the STRICT unquoted-scope
   refusal, the sanitiser change, the default-on client egress policy, the JWE size and non-string `sub` changes. All on `main` and in no release (last tag `0.9.8`). Held until a release and the operator's word.
3. **StepUp multi-scheme availability effect** (Watch): a multi-scheme `WWW-Authenticate` carrying a step-up challenge no longer triggers step-up. Watch whether a consumer reports it.
4. **e2e teardown hang** (Watch): recurred on PLAN-13 (merge-queue ejection), PLAN-24 (PR run), PLAN-26 (PR run, after the hardened stop script) and PLAN-28 (`merge_group` run 42990351708); the re-runs pass. The lane records the cause as the reusable
   workflow in `cuioss-organization` (a lane claim, not verified here). Retire when a clean merge-group run is seen twice or a cause is found.
5. **Upstream-owned (plan-marshall)**: the unstamped `effort` on fourteen landings, the incomplete landing facts (`review_body_findings_*` and `sonar_open_new_issues` on PLAN-27 and PLAN-28,
   `unresolved_review_threads` on PLAN-27), the lesson-routing gap (reported as `quality-report-001.md` in the plan-marshall repository's `live-blockers` inbox; plan-marshall-owned lessons were consumed, so none are
   missing), nine unsent addenda (the ninth: operator answers given inside a running plan never reach the orchestrator), CodeRabbit quota and squash-message residue, the `registry_parity`
   restart-check signal (the repin command exists only in the plan-marshall meta-repository, ADR-020).
6. **Docs-only Sonar skip** (HYPOTHESIS: a reusable workflow in `cuioss-organization`): docs-only PRs skip the Sonar build, so the open-issue count at the head is undecidable.
7. **TC-9 (IT part)** (Watch): integration tests use relaxed HTTPS validation and trust-all managers; a real truststore for the Keycloak container is IT-cost and was deliberately never staged. Add only
   on the operator's word.
8. **Housekeeping, not epic work**: stale branches (including the local `plan-25-full-state`), Dependabot PR #872, GitHub issues #358 and #182 (non-goals, the operator said so).

## Closing rationale

Every plan the epic staged has shipped and been analyzed; nothing is running, parked or staged; the inbox is drained; the ledger and the spec corpus reconcile. The remaining items are minor, owned by
someone other than a plan of this epic, or held on the operator's word, and each is named above with its owner. The operator asked for the close and the archive on 2026-11-11 after the PLAN-28
analysis, and the cleanup pass found the epic ready.
