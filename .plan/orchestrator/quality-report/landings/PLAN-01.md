# Landing Analysis: PLAN-01 — JWKS loader lifecycle and status truthfulness

epic: quality-report
workstream: WS-01
pr: #830 (https://github.com/cuioss/TokenSheriff/pull/830)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-01-jwks-loader-lifecycle-009.md` against the merged PR
> and the merge commit. Corroborated by this analysis: PR state `merged`, merge commit
> `1ca5ab35`, the PR title/body, and the merged file footprint (`git show --name-only 1ca5ab35`).
> NOT corroborated: code-level behaviour (no build or test was run here) and every figure that
> comes only from the landing message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.
The plan's own `deliverables_total=2` counts its internal outline deliverables, not the five
spec deliverables below.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| V-3 refresh-installed keys move status to `OK` | shipped-as-specified | PR body "Fixes V-3"; `HttpJwksLoader.java`, `IssuerConfigCacheRecoveryTest.java` changed (real-loader recovery test per PR body) |
| V-8 `OK` only with usable keys | shipped-as-specified | PR body "Fixes V-8"; `HttpJwksLoader.java`, `HttpJwksLoaderFailureTest.java` changed |
| V-4 retry transient discovery failure | shipped-modified | PR body "Fixes V-4"; `HttpJwksLoaderWellKnownAsyncTest.java`, `HttpWellKnownResolver.java`, `WellKnownConfigurationConverter.java` changed. An absent advertised issuer is now a TERMINAL rejection (new behaviour, not in the spec) |
| V-16 exactly one init/refresh task | shipped-as-specified | PR body "Fixes V-16"; `HttpJwksLoaderAsyncInitializationTest.java` changed |
| V-5 `TokenValidator.close()` closes loaders | shipped-modified | PR body "Fixes V-5"; `TokenValidator.java`, new `HttpJwksLoaderValidatorCloseTest.java`; plus a Quarkus `TokenValidatorProducer` `@PreDestroy` hook that the spec did not list |
| Added, unplanned | added-unplanned | Generation-epoch/lifecycle-lock redesign of `HttpJwksLoader`; public `FailureReason` / `getFailureReason()` on `HttpWellKnownResolver`; retry-probe IT fixture (`docker-compose.yml`, IT README, `WellKnownRetryConfigSpecIT`); docs (`multi-issuer-setup.adoc`, `openid-discovery.adoc`, `doc/validation/architecture.adoc`, `doc/validation/requirements.adoc`); ADRs 0016, 0017, 0018 (status Proposed) |
| Declared but not touched | n/a | `IssuerConfigCache.java` (spec: HYPOTHESIS, "may need no change") — unchanged, consistent with the PR's non-goals; `TokenValidatorTest.java` — not in the merged footprint |

Surface delta: 28 merged paths against 8 declared. The scope growth is named in the PR body as an
operator decision.

## Metrics and Anomalies

- Tokens: 46,477,133 (landing message; not independently verifiable here)
- Duration: 57,877 s wall (landing message)
- Anomalies:
  - The code-review loop-back ceiling (3 rounds) was fully used by successive narrow `close()`-window findings; one root cause, fixed one window per round until an epoch binding closed the class.
  - Six head-dependent gates re-ran after a late formatting commit and a docs-only ADR commit moved HEAD.
  - `effort` is `unknown` in the landing facts, so the landing is incomplete at that key (see Open Defects in `epic.md`).

## Routing and Merge Behavior

- Review: CodeRabbit (required, hourly quota) last reviewed `f3dd50e9`; the final HEAD `6198234e` adds only three ADR `.adoc` files. 11 bot threads replied to and resolved; two late CodeRabbit comments declined with rationale (landing message).
- CI/merge: merged through the merge queue as `1ca5ab35` (read from the PR: `pr_state: merged`). Merged past the review-coverage gap under an operator-recorded barrier-ask-override (landing message; not independently verified).
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys, so no open-issue figure is available for this merge head. Treat as `unknown`, not `0`.

Pairing consequence: this plan realized changes in files that staged specs declare — see Follow-Ups.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-01 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-01 --field pr --value 830`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-01 --field landing --value landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-01-jwks-loader-lifecycle`
- [x] epic.md narrative reconciled against the queue rows
- [x] Open Defect opened for the incomplete landing (`effort`)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: six other staged specs declare a path this plan changed — PLAN-03 and PLAN-17 (`TokenValidator.java`), PLAN-04 (`TokenValidatorProducer.java`), PLAN-07 (`HttpJwksLoader.java`), PLAN-13 (`HttpJwksLoader.java`, `HttpWellKnownResolver.java`, `WellKnownConfigurationConverter.java`), PLAN-19 (`doc/validation/architecture.adoc`, `doc/validation/requirements.adoc`). Their claims were grounded at `aa77acd9`; each plan's verify-first outline must re-check them against the merged code. Recorded as a Queue annotation.
- PLAN-04 (Quarkus startup health) is the most exposed: `TokenValidatorProducer.java` gained a `@PreDestroy` hook and Quarkus readiness now reports an issuer as loading (`IN_PROGRESS`) while discovery retries. Its Q-4 premise (liveness/readiness behaviour) may have moved.
- Three ADRs were authored as Proposed with provisional numbers 0016–0018; a later ADR-adding plan must allocate numbers by selection rule, not assume the next one.
