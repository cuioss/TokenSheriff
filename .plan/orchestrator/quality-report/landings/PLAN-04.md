# Landing Analysis: PLAN-04 — Quarkus startup validation and honest liveness

epic: quality-report
workstream: WS-03
pr: #834 (https://github.com/cuioss/TokenSheriff/pull/834)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-04-quarkus-startup-health-009.md` against the merged PR
> and the merge commit. Corroborated by this analysis: PR state `merged`, merge commit `ef720fd0`,
> the PR title/body, and the merged file footprint (`git show --name-only ef720fd0`). NOT
> corroborated: code-level behaviour (no build or test was run here) and every figure that comes only
> from the landing message (tokens, wall time, review-step counts, Sonar outcome).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics. The
plan's own `deliverables_total=6` counts its internal outline deliverables, not the five spec
deliverables below.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Q-3 eager startup validation | shipped-modified | PR body "Q-3"; new `TokenValidatorStartupInitializer.java`, `TokenValidatorProducer.java` and `TokenSheriffProcessor.java` changed, `TokenValidatorEagerStartupTest.java` new. Gated: built eagerly only when at least one issuer is enabled; an app with no enabled issuer or its own `TokenValidator` still boots (new ADR 0019) |
| Q-4 liveness never throws | shipped-as-specified (catch-and-report arm) | PR body "Q-4"; `TokenValidatorHealthCheck.java` and `TokenValidatorHealthCheckTest.java` changed. Chose catch + report ("resolution failed", still UP) over moving it to readiness, which the spec allowed and the PR lists as a non-goal |
| Q-2 remainder register both exception mappers via build steps | shipped-as-specified | `TokenSheriffProcessor.java`, `TokenSheriffClientProcessor.java`, `TokenSheriffProcessorBuildStepTest.java`, `TokenSheriffClientProcessorTest.java` changed; new `ProblemDetailMappingIT.java` asserts `application/problem+json` |
| Q-8 Classic-only limit | shipped-as-specified (document arm) | PR body "Q-8 documented"; `quarkus-integration.adoc`, `README.adoc` changed. Quarkus REST support not added, which the spec allowed |
| D-1 remainder README `enabled: true` | shipped-as-specified | `token-sheriff-validation-quarkus/README.adoc` changed; also `health-checks.adoc` and `quarkus-integration.adoc` |
| Added, unplanned | added-unplanned | `TokenValidatorStartupInitializer` (new class), ADR 0019 (`doc/adr/0019-Eager_TokenValidator_startup_is_gated_at_runtime_on_enabled-issuer_presence.adoc`), `JwtValidationEndpoint.java` adjusted for the IT, test resources `application-integration.properties` and `application-test.properties`, `TokenValidatorEagerStartupTest`, `TokenValidatorStartupInitializerTest` |
| Declared but not touched | n/a | `ObservedValidatorResolver.java`, `quarkus-config-doc.adoc` (so the planned PLAN-08 single-file overlap did not materialise), `HealthCheckIntegrationIT.java` |

Surface delta: 18 merged paths against 10 declared. Three declared paths were not touched and 11
merged paths were not declared (new initializer, ADR 0019, the IT and its endpoint, the eager-startup
test, the initializer test, two test property files, two docs).

The PR's "mappers inactive at runtime" hypothesis is recorded as REFUTED as a cause, with a caveat:
the IT was never run on a tree without the registration, so "failed before registration" was not
observed. Its single failure came from a native image built against stale `~/.m2` extension jars.

## Metrics and Anomalies

- Tokens: 25,399,213 (landing message; not independently verifiable here). A phase-5 dispatch boundary absorbed 327,956 triage tokens that belong to finalize, so per-phase attribution is skewed; the total is unaffected.
- Duration: 27,953 s wall (landing message)
- Effort: `unknown` — never stamped, so the landing is incomplete at that key (see Open Defects in `epic.md`).
- Anomalies:
  - The pre-push quality gate recorded "whole-tree verify green (3201 tests); test-compile arm unusable". The bare whole-tree test-compile arm fails identically on `main`, so it is a recorded degradation, not a pass. The PR body cites 3200 tests green.
  - A post-merge `checks wait --adaptive` failed with "gh CLI not found" because it ran inside the already-removed worktree: a tooling failure, not a CI result. CI was green before merge.
  - 120 stale pending `build-error` console-noise findings remain in the plan store; `pending_findings_blocking_count` was 0.

## Routing and Merge Behavior

- Review: merged under a recorded `barrier-ask-override` (`review-barrier-gap`). CodeRabbit (required) was quota-limited and Sourcery (optional) refused on head `0053b11c`; both had reviewed the previous head `7b0d80cc`, and the delta was one test assertion CodeRabbit had requested. Landing message; not independently verified.
- Functional review and code review were closed by the operator at `0053b11c` (`may_close: operator_override`), not re-run on that head. The functional review carries an accepted coverage gap (D5.C3), written into the PR description. Code review: 0 blocking, 4 advisory notes left unfixed (listed in the PR body).
- CI/merge: merged through the merge queue as `ef720fd0` (read from the PR: `pr_state: merged`, `mergedAt 2026-10-29T15:27:04Z`).
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys, so no open-issue figure is available for this merge head. Treat as `unknown`, not `0`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-04 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-04 --field pr --value 834`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-04 --field landing --value landings/PLAN-04.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-04-quarkus-startup-health`
- [x] `findings-disposition.md`: Q-3, Q-4, Q-2, Q-8, D-1 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Open Defect opened for the incomplete landing (`effort`); Watches opened
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-08 declares `JwtValidationEndpoint.java`; PLAN-17 declares three merged paths (`TokenSheriffClientProcessor.java`, `JwtValidationEndpoint.java` and a third). Both re-verify at outline. PLAN-08's expected single-file overlap on `quarkus-config-doc.adoc` did not materialise.
- PLAN-19 assumes `doc/adr/` holds ADR-0001…0015 and indexes them. With PLAN-01's ADRs 0016–0018 (Proposed, provisional numbers) and this plan's ADR 0019, its ADR index and D-7 count are stale; it must allocate by selection rule at outline.
- PLAN-08 (Quarkus config/CDI): the startup path changed, so its claims about `TokenValidatorProducer` and the config-doc startup wording must be re-grounded at outline.
- Advisories not fixed (PR body, not independently verified): `HealthCheck` catch-suppression placement per `AGENTS.md`; README wording for apps that supply their own `TokenValidator`; `CLIENT-21` vs `CLIENT-20` in the `TokenSheriffClientProcessor` Javadoc; the initializer unit test does not pin the proxy dereference. Recorded as a Watch.
- The functional-review coverage gap D5.C3 was accepted by the operator rather than closed. Recorded as a Watch.
- Eight `candidate-lesson` messages are queued and not yet dispositioned. Four name components of this repo (stale `~/.m2` native test runs, the formatter import flip-flop, documented guarantees broader than enforced, new tests asserting weaker properties than intended); four name `plan-marshall:*` components.
