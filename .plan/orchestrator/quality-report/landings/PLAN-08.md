# Landing Analysis: PLAN-08 — Quarkus configuration and CDI fixes

epic: quality-report
workstream: WS-03
pr: #850 (https://github.com/cuioss/TokenSheriff/pull/850)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-08-quarkus-config-cdi-012.md` against the merged PR and the
> merge commit. Corroborated by this analysis: PR state `merged`, merge commit `ed172e43`, the PR
> title/body, the merged file footprint (`git show --name-only ed172e43`), the Sonar quality gate and a
> direct unresolved-issue query. NOT corroborated: code-level behaviour (no build or test was run here)
> and every figure that comes only from the landing message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Q-1 class-level `@BearerAuth` | shipped-as-specified | PR body: the interceptor reads the requirement from the real bean class (the superclass of the ArC-generated subclass) and merges it with the method-level one. `BearerTokenInterceptor.java` and its test changed; new `BearerAuthSpecIT` and three class-level endpoint fixtures in the integration-tests module |
| 2. Q-5 `@DefaultBean` | shipped-as-specified | PR body: `tokenStore()` and `refreshScheduler()` are `@DefaultBean`. `TokenSheriffClientProducer.java`, its test and the new container-level `DefaultBeanReplacementTest` changed |
| 3. Q-6 strict JWE configuration | shipped-as-specified, wider than listed | PR body: unknown `default-key-id`, orphaned ancillary JWE properties, a `decryption-keys.<kid>` entry without `.path` and a bare `decryption-keys.<kid>` value now fail startup. `JweDecryptionConfigResolver.java` and its test changed |
| 4. Q-9 env-var-only issuers | shipped-modified (fix arm) | The spec said verify at outline whether discovery works. The PR took the fix arm: discovery anchored on `SHERIFF_TOKEN_ISSUERS_<NAME>_ENABLED`, name lower-cased; `_DPOP`-suffixed names and env-only JWE keys are not discovered. `ConfigValueParser.java` and `IssuerConfigResolver.java` (the latter not declared) changed with tests |
| 5. D-8 / J-8 TTL default | shipped-as-specified | PR body: config doc and `JwtPropertyKeys` state default 360 and the `proofMaxAge + 60` floor. `quarkus-config-doc.adoc` and `JwtPropertyKeys.java` changed. `DpopConfig.java` was not touched (a spec non-goal), and its class Javadoc still says 300 |
| Added, unplanned | added-unplanned | `scripts/build-native-if-needed.sh` now builds with `-am`, and the integration-tests `pom.xml` gains the property `integration-tests.native-build.phase` (default `none`): the native build had resolved the extension modules from a stale `~/.m2` snapshot of `main`, so the ITs never exercised the branch. Two extra JWE rows and a JWE note in `quarkus-config-doc.adoc`. Both were operator-approved scope exceptions. Also `IssuerConfigResolver(Test).java`, `DefaultBeanReplacementTest.java`, `BearerAuthSpecIT.java` |
| Declared but not touched | n/a | `BearerAuth.java` (a PLAN-17 Javadoc overlap) |

Surface delta: 19 merged paths against 12 declared. Eleven declared entries were touched (the declared
`endpoint/` directory covers the three new fixtures); one was not (`BearerAuth.java`). Six merged paths were not
declared. The landing-check state is `expansion_detected`. The spec lists 5 deliverables; the landing counts 7
(the outline split them).

## Metrics and Anomalies

- Tokens: 31,139,010 (landing message; not independently verifiable here).
- Duration: 64,118 s wall (landing message).
- Effort: `medium`; model `claude-sonnet-5-5` (landing message). Landing complete. Executor and plugin both 0.1.2046.
- Anomalies:
  - Pre-existing red on `main`: whole-tree `test-compile` and `module-tests` fail because `token-sheriff-client` and `benchmark-core` tests cannot see the validation test-jar before packaging (the same cause as PLAN-21, PLAN-05, PLAN-12). The operator accepted `verify:quality-gate` (3476 tests) in its place and the PR body says so.
  - The pre-merge gate refused on 120 stale pending `build-error` findings, re-opened by the build tooling's dedupe when the red `test-compile` was re-observed. Dispositioned one by one (17 fixed, 103 accepted).
  - Two loop-backs from the finalize code review (config doc drift, then a documented startup failure the code did not implement) and one operator scope exception for the fix that touched JWE rows outside deliverable 4's carve-out.
  - The 5 → 6 phase transition was refused twice for commits with no `kind=change` ledger row (code-review loop-back and triage fix commits).
  - Several argparse rejections on plan-marshall scripts (invented flags `--number` and `--pr-number`, a comma-joined `--status`), each retried.
  - A post-merge `Run JMH Benchmarks` workflow was still running when finalize moved on. Not read here.
  - The 120 findings and the pre-existing red are one root cause with PLAN-21's candidate lesson 001.

## Routing and Merge Behavior

- Review: CodeRabbit (free tier, one review per hour) delayed the first review about 19 minutes and could not re-review a further fix commit inside the same hour; Sourcery refused on a quota (about 2 days) and is optional. Findings `f75e6f`, `91ec0c` and `cdb311` were fixed (TASK-18, TASK-19 and an inline doc commit). Landing message; not independently verified. No `barrier-ask-override` is recorded in the message.
- Code review: 0 blocking, 3 advisory, 3 rules cited (landing message).
- CI/merge: merged through the merge queue as `ed172e43` (read from the PR: `state: merged`).
- Sonar: gate `OK` on PR 850 (new coverage 96.5), and a direct `resolved=false` query returned 0 open issues. Project-wide on the default branch: 44 open, unchanged from the last read.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-08 --status shipped`
- [x] row `pr` stamped — value 850
- [x] row `landing` stamped — value `landings/PLAN-08.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-08-quarkus-config-cdi`
- [x] `findings-disposition.md`: Q-1, Q-5, Q-6, Q-9, D-8, J-8 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: no staged spec declares a path this plan changed.
- Open question held for the maintainer by the plan: CodeRabbit asked for a native-image fixture with class-level `@BearerAuth` and without `@RegisterForReflection`, plus two notes marked unverified (native reflection metadata for the class-level lookup; a fail-open when ArC reports an unannotated generated override). Q-1 was verified in a real native container only with the two registered fixtures, so whether the unregistered case can fail open is untested. This is a security-relevant gap; see `epic.md`.
- Advisories left open and routed under the fix-immediately policy: `DpopConfig.java:36` Javadoc (300 vs 360) and the untested empty-candidate guard in `ConfigValueParser.discoverEnvNameSegments` went to PLAN-22. Env-only multi-key JWE configuration is skipped without a startup signal (documented and pinned by a test): a design note, kept as a Watch.
- Four ADR candidates were not created (env-var issuer discovery anchored on the enabled key; interceptor binding read from the bean class, not the ArC subclass; `@DefaultBean` client producers; fail-loud orphaned or unresolvable configuration). They went to PLAN-22's ADR deliverable.
- Eleven `candidate-lesson` messages were queued; see `epic.md` for the dispositions.
