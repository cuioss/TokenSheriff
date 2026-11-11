# Landing Analysis: PLAN-02 — Refresh-token family rotation correctness

epic: quality-report
workstream: WS-02
pr: #832 (https://github.com/cuioss/TokenSheriff/pull/832)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `implement-plan-02-refresh-family-rotation-006.md` against
> the merged PR and the merge commit. Corroborated by this analysis: PR state `merged`, merge
> commit `31fb2212`, the PR title/body, and the merged file footprint (`git show --name-only
> 31fb2212`). NOT corroborated: code-level behaviour (no build or test was run here) and every
> figure that comes only from the landing message.

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| C-1 reseed the family on a new grant; `applyRefresh` advances it | shipped-as-specified | PR body "C-1 / C-2"; `TokenLifecycleManager.java`, `RefreshTokenFamily.java`, new `RefreshFamilyReseedTest.java`, `RotationReuseDetectionTest.java` changed |
| C-2 carry the capped `expires_in` | shipped-as-specified (mislabelled) | `RefreshFlow.java`, `RotationResult.java`, `RefreshFlowTest.java` changed. The PR body files this under "C-8b" and files the family reseed under "C-1 / C-2": the finding IDs in the body do not map one-to-one to the spec's |
| C-8b / TC-6 injectable caps, no reflection, post-eviction replay pinned | shipped-as-specified | `DpopProofGenerator.java` (package-private constructor with `maxTrackedJtis`), `TokenLifecycleManagerFamilyEvictionTest.java`, `DpopProofGeneratorTest.java` changed; the residual risk is documented in `doc/client/specification/token-handling.adoc` |
| Added, unplanned | added-unplanned | Per-session striped lock and compare-and-set of the pending refresh; best-effort RFC 7009 revocation of an orphaned successor; new INFO record `TokenSheriffClient-003` (`ClientLogMessages.java`, `doc/LogMessages.adoc`) |
| Declared but not touched | n/a | `RefreshAdversarialTest.java` — not in the merged footprint |

Surface delta: 13 merged paths against 9 declared. The spec's HYPOTHESIS on `DpopProofGenerator.java`
is confirmed (touched). `ClientLogMessages.java`, `doc/LogMessages.adoc`, `token-handling.adoc` and
two test files were not declared.

## Metrics and Anomalies

- Tokens: 37,817,533 (landing message; not independently verifiable here)
- Duration: 40,345 s wall (landing message)
- Anomalies:
  - Five code-review fix rounds (loop-back budget on `default:code-review` spent) plus one `wait-region-unified-triage` loop-back; all driven by successive races in the token-store/family two-slot update.
  - A mid-run rebase onto a moved `main` voided the head-bound verdicts (audit, two reviews, builds re-ran).
  - `module-tests DEGRADED` recorded by the pre-push quality gate (landing message; the PR body reports the whole tree green at `fdf183df`).
  - `effort` is `unknown` in the landing facts (incomplete at that key); the message also ends before `emit-landing` and `archive-plan`.

## Routing and Merge Behavior

- Review: CodeRabbit's hourly limit stalled re-review twice; a "No files to review" reply after a content-identical rebase needed the `--escalated` full review. Eight code-review advisories are open, none blocking (listed in the PR body).
- CI/merge: merged through the merge queue as `31fb2212` (read from the PR: `pr_state: merged`).
- Sonar: the landing reports `count_status=confirmed` and `new_code_issue_count=1` with 0 filed for triage, and no open-issue figure. The spec requires the open-issue count at the head to be 0 or every remaining issue dismissed; nothing in the landing shows the one new-code issue was fixed or dismissed. Recorded as a Watch (unverified); not asserted as a violation.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-02 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-02 --field pr --value 832`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-02 --field landing --value landings/PLAN-02.md`
- [x] row `plan_marshall_plan_id` stamped — value `implement-plan-02-refresh-family-rotation`
- [x] epic.md narrative reconciled against the queue rows
- [x] Open Defect opened for the incomplete landing (`effort`); Watches opened (Sonar, residuals)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-07 and PLAN-13 (`doc/LogMessages.adoc`) and PLAN-19 (`doc/client/specification/token-handling.adoc`) declare a path this plan changed. Both LogMessages-editing plans must re-check the registry row numbering against `TokenSheriffClient-003`.
- Held by the maintainer, not fixed: the public `applyRefresh` overloads take no base-token argument, so a late external result can still replace a newer re-login grant (needs a public signature change; pre-existing at the merge base). Recorded as a Watch.
- Stale comment on `RefreshAdversarialTest.shouldFailClosedWhenRevocationThrowsUnchecked` after reseeding; the file is in this plan's declared surface but was not touched. Recorded as a Watch.
- Open advisory: an OpenRewrite `TODO` marker in `holdsNewerGrant`; ADR-0005 and ADR-0010 still say the post-redemption clear is unconditional. Recorded as a Watch.
