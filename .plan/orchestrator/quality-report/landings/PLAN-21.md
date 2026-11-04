# Landing Analysis: PLAN-21 — Residual behaviour fixes left open by PLAN-01 to PLAN-05

epic: quality-report
workstream: WS-01
pr: #849 (https://github.com/cuioss/TokenSheriff/pull/849)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-21-residual-behaviour-fixes-008.md` against the merged PR and
> the merge commit. Corroborated by this analysis: PR state `merged`, merge commit `c308f7c0`, the PR
> title/body, the merged file footprint (`git show --name-only c308f7c0`), the Sonar quality gate and a
> direct unresolved-issue query. NOT corroborated: code-level behaviour (no build or test was run here)
> and every figure that comes only from the landing message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Loader init path | shipped-as-specified | PR body item 1: an unexpected failure in `HttpJwksLoader.initialize` ends in a published terminal (`ERROR`) or retry-pending status through the existing lifecycle helpers, and a retry-pending status always has its refresh scheduled. `HttpJwksLoader.java` and `HttpJwksLoaderFailureTest.java` changed. A CodeRabbit follow-up (lock-release race: a status decided under `lifecycleLock`, published after release) was fixed in `e33b49bf` |
| 2. Producer destroy | shipped-as-specified (narrow arm), wider than listed | PR body item 2: the catch is narrowed to the documented thrower and the rewrite suppression is gone. `TokenValidator.close` was changed too (not declared) so a failing close no longer skips the remaining resources. `TokenValidatorProducer.java`, `TokenValidator.java` and their tests changed |
| 3. `applyRefresh` base token | shipped-modified | PR body item 3: `applyRefresh` takes the refresh token the result was derived from and compare-and-sets it against the stored bundle; a miss returns the newer bundle unchanged. The PR calls it a breaking signature change and the landing speaks of a signature *replacement*, so the old overloads appear to be gone, not deprecated (the spec allowed removal only if the outline found no external caller; whether it checked that is not visible here). Five client tests, two Quarkus integration tests (caller updates) and `doc/client/specification/token-handling.adoc` changed |
| 4. Cache put without `iat` | shipped-modified | PR body item 4: `put` refuses to cache an `iat`-less token under a maximum-age policy, so it stays on the full validation path (the spec allowed "reject or safely handle"). `AccessTokenCache.java` and `AccessTokenCacheTest.java` changed |
| 5. Null header accounting | shipped-as-specified, wider than listed | PR body item 5: a JSON-null header is logged and counted like every other rejected header, and an invalid Base64Url part is now too (not listed). `NonValidatingJwtParser.java`, `NonValidatingJwtParserTest.java`, `NonValidatingJwtParserJweTest.java` changed. `doc/validation/specification/token-decryption.adoc` changed with it |
| Added, unplanned | added-unplanned | `TokenValidator.java` and `TokenValidatorTest.java`; `ConstraintPreservationTest`, `TokenLifecycleManagerFamilyEvictionTest`, `TokenStoreTest`, `RefreshFamilyReseedTest` (caller updates after the signature change); `RefreshConstraintLifecycleSpecIT`, `RefreshLifecycleSpecIT`; `TokenValidatorProducerUnitTest`; `HttpJwksLoaderFailureTest`; `NonValidatingJwtParserJweTest`; the two `.adoc` files |
| Declared but not touched | n/a | `JWTValidationLogMessages.java` and `doc/LogMessages.adoc` (declared conditionally, "only if a LogRecord is added"): the fixes reuse existing records |

Surface delta: 21 merged paths against 8 declared. Six declared paths were touched; two were not (the
conditional log files). Fifteen merged paths were not declared. The landing-check state is
`expansion_detected`.

## Metrics and Anomalies

- Tokens: 24,239,491 (landing message; not independently verifiable here).
- Duration: 51,824 s wall (landing message).
- Effort: `medium`; model `claude-sonnet-5-5` (landing message). Landing complete.
- Executor: `executor_version` 0.1.2046 equals `plugin_version` 0.1.2046. The drift recorded in the PLAN-06, 07, 12 and 20 landings (executor 0.1.2027 against newer docs) is gone; the operator's marshall-steward run regenerated it.
- Anomalies:
  - The pre-push gate recorded `test-compile` and the separate `module-tests` run as UN-GATED: the Maven reactor `test-compile` cannot resolve the `token-sheriff-validation` generators test-jar (attached at `package`), so it fails independently of this change. The whole-project `verify -Ppre-commit` ran 3476 tests green at the merged head. The failed attempt filed 120 `build-error` findings (one root cause), resolved `accepted`. Candidate lesson 001.
  - The first sync-baseline record forwarded `--head-at-completion`, which tripped `merge_candidate_not_gated` at the pre-merge barrier; re-recorded without the flag. Candidate lesson 002.
  - A triage-produced fix commit after the pre-push gate needed a gate re-run before the push. Candidate lesson 003 (a recurrence of the PLAN-20 late-commit cost).
  - Three Q-Gate findings accepted by hand (two description-drift false positives on ADR numbers from the phase-4 ADR scan, one `module_mapping_validator` flag on compile-compat IT edits), each filed as a candidate lesson.
  - The post-merge `Run JMH Benchmarks` workflow was still running when the post-merge wait reached its deadline; every merge-gating check had passed. Not read here.
  - The spec's ordering dependency on PLAN-20 was not verified by the run. It is satisfied on `main`: #847 (`5349a3bc`) precedes #849, and the sanctioned `cui-rewrite:disable InvalidExceptionUsageRecipe` suppression is still in `TokenLifecycleManager.java`.

## Routing and Merge Behavior

- Review: CodeRabbit finding `fda655` (lock-release race plus three under-pinned tests) was fixed in `e33b49bf` (TASK-11). Landing message; not independently verified.
- Code review: 0 blocking, 2 advisory, 2 rules cited. Left open by the run's decision: (1) a JWE test row cannot fail for the message leak it names; (2) the `applyRefresh` Javadoc does not say that a compare-and-set miss orphans the rotated refresh token. Four earlier advisories were accepted: `AccessTokenCache` Javadoc wording, `TokenValidator.close` dropping the first of two failures, `HttpJwksLoader` retrying an unusable `jwks_uri`, and a `DecodeMode` gap on size/structure rejections.
- Operator ruling inside the run: no ADR for the `applyRefresh` compare-and-set decision (declined). ADR-0017 could be amended to say retry-pending applies only when a recovering refresh is scheduled.
- CI/merge: merged through the merge queue as `c308f7c0` (read from the PR: `state: merged`).
- Sonar: gate `OK` on PR 849 (new coverage 83.5, duplication 0.0), and a direct `resolved=false` query returned 0 open issues. A project-wide query on the default branch returns 44 open issues (11 `java:S8714`, 7 `java:S5778`, 4 `java:S4449`, ...); the newest was created on 2026-10-10, so none comes from this PR.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-21 --status shipped`
- [x] row `pr` stamped — value 849
- [x] row `landing` stamped — value `landings/PLAN-21.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-21-residual-behaviour-fixes`
- [x] `findings-disposition.md`: no finding ID (PLAN-01 to PLAN-05 residuals); nothing to mark
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-09 (`AccessTokenCache.java`, `NonValidatingJwtParser.java`, `NonValidatingJwtParserTest.java`), PLAN-13 (`HttpJwksLoader.java`), PLAN-17 (`TokenValidator.java`, `HttpJwksLoader.java`, `NonValidatingJwtParser.java`), PLAN-19 (`token-handling.adoc`) and PLAN-22 (`token-decryption.adoc`, `AccessTokenCacheTest.java`) declare paths this plan changed. Each re-verifies its claims against the new code at outline. PLAN-22's `token-decryption.adoc` wording claim (the `kty` rejection) is the one most likely to have moved, since #849 edited that file.
- The two open advisories and four accepted ones contradict the standing fix-immediately policy; routing is recorded in `epic.md`.
- `applyRefresh` is a breaking public signature change; it belongs in the release notes.
- Seven `candidate-lesson` messages were queued; see `epic.md` for the dispositions.
