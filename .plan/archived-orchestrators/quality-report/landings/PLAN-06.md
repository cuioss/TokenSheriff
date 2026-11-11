# Landing Analysis: PLAN-06 — Callback and logout hardening

epic: quality-report
workstream: WS-02
pr: #840 (https://github.com/cuioss/TokenSheriff/pull/840)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-06-callback-logout-hardening-006.md` against the merged PR
> and the merge commit. Corroborated by this analysis: PR state `merged`, merge commit `a56b1b59`, the
> PR title/body, the merged file footprint (`git show --name-only a56b1b59`), the Sonar quality gate
> and a direct unresolved-issue query. NOT corroborated: code-level behaviour (no build or test was
> run here) and every figure that comes only from the landing message (tokens, wall time, review-step
> counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| C-6 `state` verified before an error callback is acted on | shipped-as-specified | PR body "C-6"; `CallbackHandler.java` and `CallbackHandlerTest.java` changed. A forged error callback is refused as a state mismatch |
| C-5 / C-8a `CallbackParameters.toString()` sanitised; sanitisers neutralise U+0085, U+2028, U+2029 | shipped-modified | PR body "C-5 / C-8a"; `CallbackParameters.java`, `LogSanitizer.java`, `InboundErrorNormalizer.java` and their tests changed. The spec asked for the rule in both modules; the PR instead extracts one shared implementation, the new `ErrorTextSanitizer` in the validation module, which both now use, and it also covers the C1 control range |
| C-4 endpoint scheme enforced for `EndSessionFlow`; query placed before a fragment | shipped-as-specified | PR body "C-4"; new `EndpointSchemePolicy.java` and `EndpointQuery.java`; `AuthorizationRequestBuilder.java` and `EndSessionFlow.java` changed. The check also refuses an endpoint with no host. A non-TLS end-session endpoint is refused by default; a new two-argument constructor is the explicit `allowInsecureHttp` opt-in |
| C-7 / TC-5 single-use `FlowContext` decided and pinned | shipped-as-specified (the recommended "enforce" arm) | PR body "C-7 / TC-5"; `FlowContext.java` changed; `CallbackHandler` claims the context atomically before any other check; `AuthorizationCodeFlowTest.java` pins a second `handle()` through `AuthorizationCodeFlow.exchange`. A rejected first `handle()` deliberately uses the context up (fail-closed, stated as intended) |
| Added, unplanned | added-unplanned | `EndpointQuery`, `EndpointSchemePolicy`, `ErrorTextSanitizer` (all new) and their three test classes; `AuthorizationRequestBuilderTest.java` and `AuthorizationCodeFlowTest.java` changed. No new log identifier (every refusal is a thrown exception) |
| Declared but not touched | n/a | `LogoutOpenRedirectTest.java` |

Surface delta: 20 merged paths against 13 declared. Twelve declared paths were touched; the one
untouched is `LogoutOpenRedirectTest.java`. Eight merged paths were not declared: `EndpointQuery.java`,
`EndpointSchemePolicy.java`, `ErrorTextSanitizer.java`, `AuthorizationCodeFlowTest.java`,
`AuthorizationRequestBuilderTest.java`, `EndpointQueryTest.java`, `EndpointSchemePolicyTest.java`,
`ErrorTextSanitizerTest.java`. The PR states `AuthorizationRequestBuilder.build` still appends its query
after a fragment: that stays with PLAN-10, as the spec required.

## Metrics and Anomalies

- Tokens: 39,378,383 (landing message; a floor, see below). The 6-finalize accumulator was completed after the fact with six dispatches that had not been accumulated, and the phase-5 TASK-19 envelope (192,197 tokens) was not folded into the closed 5-execute row.
- Duration: 154,547 s wall (landing message). Most of it is waiting: the CodeRabbit quota wait, roughly three hours behind it, and a merge-queue ejection.
- Effort: `medium`; model `claude-sonnet-5-5` (landing message).
- Anomalies:
  - A test-only commit after the gated head made the pre-merge head binding refuse the merge (`merge_candidate_not_gated`): the operator's earlier re-run waivers did not carry, and `finalize-step-security-audit` and `functional-review` had to be re-run for real (candidate lesson 002).
  - The merge queue ejected the PR once on an unrelated 20 ms Awaitility test in `benchmarking/benchmark-core` (`LibraryMetricsExporterTest`, merge_group run 41108666343), the same flake PLAN-05 hit. One re-enqueue at the same head merged it (candidate lesson 004).
  - Installed finalize scripts lack verbs the docs name (`pre_merge_gate record-merge`, `consequence-text`); `branch-cleanup` was recorded through `mark-step-done` with the same facts.

## Routing and Merge Behavior

- Review: merged under a recorded `barrier-ask-override` over `review-barrier-gap`, bound to head `aa72e8cb`. CodeRabbit (required) had reviewed `0ad8f597` but not the final head: its hourly quota was spent by PLAN-07, which ran concurrently. Sourcery (optional) was quota-limited. The last commit adds one test input for a CodeRabbit nitpick. The operator chose this explicitly. Landing message; not independently verified.
- Code review: the landing carries no `step.code-review.*` counts, so blocking and advisory counts are unknown for this plan.
- CI/merge: merged through the merge queue as `a56b1b59` (read from the PR: `state: merged`, merge commit `a56b1b59c8f4fd1bde020992068b802e4d03ca01`).
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys. Read by this analysis: quality gate `OK` on PR 840 (new coverage 99.3, new duplication 0.0, all ratings 1, hotspots 100 percent reviewed), and a direct `resolved=false` issue query returned 0 open issues. A snapshot of the last analysis of the PR, not a re-run.
- `adr-propose` was skipped: the plan's scope forbids repository documentation writes and an ADR file would have moved HEAD past the verified tree. Whether an ADR is wanted is an operator decision. Candidate: single-use `FlowContext` (fail-closed, a rejected first call spends the context) and the shared `ErrorTextSanitizer` living in the validation module. Recorded as a Watch.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-06 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-06 --field pr --value 840`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-06 --field landing --value landings/PLAN-06.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-06-callback-logout-hardening`
- [x] `findings-disposition.md`: C-4, C-5, C-6, C-7, C-8a, TC-5, gap CLI 1 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened (skipped ADR, shared sanitiser, quota)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-10 declares `AuthorizationRequestBuilder.java` and `AuthorizationCodeFlowTest.java` (both changed; the spec expected the single-file overlap on the builder), and PLAN-14 declares `EndSessionFlowTest.java`. Both re-verify at outline. PLAN-10 must re-ground its fragment claim against the new `EndpointQuery`/`EndpointSchemePolicy` split: the scheme check no longer lives in the builder.
- PLAN-13 (log hygiene) and PLAN-09 (parser hardening) overlap nothing here, but any later sanitiser work should extend `ErrorTextSanitizer`, not fork it.
- Five `candidate-lesson` messages are queued and not yet dispositioned: three name `plan-marshall:*` components (review quota contention, head-binding waiver, documented verbs the installed scripts lack), two name components of this repo (the flaky 20 ms benchmark test, sanitiser duplication caught only by Sonar).
