# Landing Analysis: PLAN-20 — Resolve the committed OpenRewrite marker in TokenLifecycleManager

epic: quality-report
workstream: WS-02
pr: #847 (https://github.com/cuioss/TokenSheriff/pull/847)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-20-rewrite-marker-cleanup-005.md` against the merged PR and
> the merge commit. Corroborated by this analysis: PR state `merged`, merge commit `5349a3bc`, the PR
> title/body, the merged file footprint (`git show --name-only 5349a3bc`), the Sonar quality gate, a
> direct unresolved-issue query and a project-wide query for the `java:S1135` rule. NOT corroborated:
> code-level behaviour (no build or test was run here) and every figure that comes only from the landing
> message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Marker answered | shipped-as-specified (the recommended suppression arm) | PR body and landing: the `/*TODO …*/` marker in `holdsNewerGrant` was replaced by `// cui-rewrite:disable InvalidExceptionUsageRecipe` before the `catch`, with the existing comment extended to say why the broad catch is deliberate (`TokenStore` is an SPI whose `retrieve` declares no `throws`). `AGENTS.md` was reworded: the tree holds several sanctioned instances, `TokenBuilder` being the reference. The landing says the `-Ppre-commit` run did not re-inject the marker |
| 2. Behaviour pinned by a test | not evidenced at file level | No test file is in the merged footprint. The PR states "no behaviour change: an unreadable store still reports `false`". Whether an existing test already covers a throwing `retrieve` (the spec's HYPOTHESIS) is not visible here; the landing counts 2 of 2 deliverables done and the functional review met both |
| 3. No marker left in the tree | corroborated | Sonar: 0 open on PR 847, and a project-wide `java:S1135` query returns 0 open issues, so the issue left by #832 is gone. A source sweep of `token-sheriff-*` is not repeated here |
| Added, unplanned | none | Footprint is exactly `TokenLifecycleManager.java` and `AGENTS.md` |
| Declared but not touched | n/a | `TokenLifecycleManagerFamilyEvictionTest.java` (a HYPOTHESIS path) |

Surface delta: 2 merged paths against 3 declared. Both touched paths were declared; the untouched one is
the HYPOTHESIS test file. No path outside the declaration.

## Metrics and Anomalies

- Tokens: 16,668,570 (landing message; not independently verifiable here).
- Duration: 91,245 s wall (landing message), dominated by waiting on CodeRabbit's one-review-per-hour quota.
- Effort: `medium`; model `claude-sonnet-5-5` (landing message). Landing complete.
- Anomalies:
  - A docs-only follow-up commit (the `AGENTS.md` wording fix for a CodeRabbit comment) after the pre-push gate re-ran every head-dependent finalize step, about an hour of waiting (candidate lesson 004).
  - Executor 0.1.2027 against skill docs 0.1.2046: `review_completeness roster`, `pre_merge_gate record-merge`, `manage-solution-outline get-deliverable --number` and the `generate_trigger` selector result are missing or differ. `branch-cleanup` was recorded with `mark-step-done`.
  - The functional review's closing round for the second head ran the full round directly, skipping the delta round. The code-review author was dispatched, not run inline.

## Routing and Merge Behavior

- Review: CodeRabbit refused twice with quota notices; `review_rate_window_await` was switched on for this plan after the first refusal. The `AGENTS.md` wording fix for its inline comment was pushed and the whole head-dependent chain re-run on the operator's choice. The landing carries no `barrier-ask-override` here. Landing message; not independently verified.
- Operator rulings: merge through the merge queue was explicitly authorised, overriding the CLAUDE.md no-auto-merge default for this PR.
- Code review: 0 blocking, 2 advisory, both on `AGENTS.md` and left open: (1) the carve-out clause "or an equivalent boundary whose thrower declares nothing to narrow against" is wider than the verified sites and conflicts with `TokenValidatorProducer`'s own comment that its catch is not covered by the carve-out; (2) the placement paragraph omits the two live non-catch-position markers in `RefreshConcurrentRedemptionSpecIT` (method level) and `RefreshProductionPathSpecIT` (class level), so "class-level and method-level suppression were never tested" is imprecise.
- CI/merge: merged through the merge queue as `5349a3bc` (read from the PR: `state: merged`).
- Sonar: gate `OK` on PR 847, 0 open issues (direct query).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-20 --status shipped`
- [x] row `pr` stamped — value 847
- [x] row `landing` stamped — value `landings/PLAN-20.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-20-rewrite-marker-cleanup`
- [x] `findings-disposition.md`: no finding ID (a PLAN-02 residual); nothing to mark
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened; the open Sonar INFO on #832 closed
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-21 declares `TokenLifecycleManager.java` (the planned sequencing, now satisfied; re-ground against the new comment) and PLAN-19 declares `AGENTS.md` (re-verify at outline). PLAN-02 also matches but already shipped.
- The two open `AGENTS.md` advisories contradict the standing fix-immediately policy; they were added to PLAN-22 (wording deliverable).
- Four `candidate-lesson` messages were queued: executor older than the docs (a recurrence), a docs-claim advisory not acted on before the PR went up, a self-contradicting outline fallback, and the late-commit rerun cost (a recurrence). They were dispositioned, see `epic.md`.
