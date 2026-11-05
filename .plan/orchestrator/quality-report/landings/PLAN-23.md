# Landing Analysis: PLAN-23 — Close the pre-existing SonarCloud backlog on main

epic: quality-report
workstream: WS-05
pr: 855 (https://github.com/cuioss/TokenSheriff/pull/855)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `implement-plan-23-sonar-backlog-cleanup-004.md`) after verifying the claims against the merged source, the PR
> state read through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `0c56583e` (read: `git show --stat`, per-file diffs of the
five bug-class sites and the two flagged tests) and the PR (`ci pr view --pr-number 855`: `state: merged`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Test-code issues (27 in 16 files): `S8714`, `S5778`, `S8786`, `S5976`, `S4144`, `S2093`, ... | shipped-as-specified, one residual | The merged diff touches the spec's test files (32 of 32 declared paths realized). `ClientIpExtractorTest` collapses three tests into one `@CsvSource` parameterised test over the same two inputs (`null` and `192.168.1.50` fallback); `NativeTokenValidatorProducerIT` replaces `assert true` with a real `GET /q/health/started` = 200. **Residual:** SonarCloud still reports `java:S8786` (`AZ7xhrgAXGjoVdkwztNZ`, MAJOR) on `main` at `JwtTokenTamperingUtil.java` line 450. The merged line already carries the possessive-quantifier rewrite, so the analyser flags the possessive form too (the issue's `updateDate` is 2026-06-28, never refreshed). The plan's own PR gate could not see it. |
| 2. Cognitive complexity (3 `S3776`): `IssuerConfigCache`, `TokenValidator`, `TokenBuilder` | shipped-as-specified | The three files changed (`IssuerConfigCache` 80 changed lines, `TokenValidator` 78, `TokenBuilder` 97) with new tests `IssuerConfigCacheTest` (+139) and `TokenBuilderTest` (+135). All three issues are absent from the post-merge `main` query. |
| 3. Always-true/false conditions (5: `S2583` x2, `S2589` x3) | shipped, decided per site as keep-and-annotate | None of the five defensive checks was deleted. Read in the diff: `ClientAuthMethod.fromMetadataValue`, `DiscoveryResolver.parse`, `RefreshFlow.resolveRefreshToken`, `PostLogoutRedirectValidator.isRegistered` and `IdTokenValidationBridge.noncesMatch` now declare the parameter `@Nullable` with Javadoc stating the null case fails closed. This honours the spec's "do not delete a check only because the analyser cannot see why it exists". |
| 4. Nullness (4 `S4449`) | shipped | `IdTokenValidationBridge`, the `HttpJwksLoaderConfig` and `WellKnownConfig` `uri` accessors, `StepUpHandler.create`. **One behaviour change:** `StepUpHandler` now throws `IllegalArgumentException("client declares no redirect URI")` before building a `FlowContext` when the configured redirect URI is null or blank (read in the diff; test `StepUpHandlerTest` +15). It narrows, never widens. |
| 5. Minor main-code rules (`S8688` x3, `S9395`, `S1075`) | shipped | `AccessTokenCache`, `ValidationContext`, `AccessTokenValidationPipeline`, `JwtMetricsCollector`, `CustomAccessLogFilter` changed; all five issues are absent from the `main` query. |
| 6. Close-out: re-query by key, record every dismissal, PR gate reads 0 open | shipped as specified for the PR; backlog not fully closed (see residual) | PR 855 shows two `java:S1612` issues in `AccessTokenCacheTest` (lines 254 and 325); both read `CLOSED` / `FIXED` (suppressed in code on the operator's decision, recorded in the plan; the suppression is the sanctioned `@SuppressWarnings` form with a stated reason). Gate `OK`. |

**Behaviour change beyond the spec's text, noticed in the merged source.** `TokenBuilder.mapClaim` now catches a
mapper-thrown `TokenValidationException`, counts it under the exception's own event type and rethrows it unchanged; a
runtime exception from a custom mapper is still wrapped as `MISSING_CLAIM`. This resolves the PLAN-11 advisory that a
`StrictStringMapper` rejection is not counted (`MISSING_CLAIM` is now counted). It was a by-product of the
cognitive-complexity extraction, not a spec item. PLAN-24 is updated to expect it.

**Surface delta (landing-check, `unmeasured`: the landing supplied neither side).** Computed here from the spec's 32
declared paths (the `corpus surfaces` read before the merge) and the merged diff: declared 32, realized 39, 32
matched, 7 added, 0 missing. Added, all tests or fixtures that the behaviour changes needed: `StepUpHandlerTest`,
`HttpJwksLoaderConfigTest`, `WellKnownConfigTest`, `IdTokenValidationPipelineNullCheckTest`, `IssuerConfigCacheTest`,
`TokenBuilderTest`, `JsonTypedAzpFixture` (a one-line constructor argument). The spec is a record and is not edited.

## Metrics and Anomalies

- Tokens: 26,133,046; duration 77,606 wall seconds, about 21.6 hours (landing message; the longest of the plans, most
  of it the review-quota wait and the operator-closed review rounds).
- Effort: `medium`, stamped (the landing is complete).
- Anomalies:
  - Functional and code review were closed by operator override at each head because `review_support test-integrity`
    raised `weakened_test` findings that every verifier judged false positives. I read both flagged tests in the diff:
    the `ClientIpExtractorTest` merge keeps both inputs, and the `NativeTokenValidatorProducerIT` change strengthens
    the test. A third flag, on `RefreshFailureClassificationTest`, appeared only while the branch lagged a moved main
    (PLAN-10 #856 had edited that file) and cleared after the rebase: this is the collision recorded as a Watch at the
    PLAN-10 landing, now observed and harmless.
  - The first `lessons-capture` run did not receive the orchestration verdict and wrote two lessons to the global
    store with `--allow-foreign-store`; they were tombstoned and re-sent as inbox messages.
  - Pre-push gate `module-tests` arm degraded on every push (seventh plan with the degradation); the whole-tree
    `verify -Ppre-commit` ran 3680 tests, per the landing (not re-run).

## Routing and Merge Behavior

- Review: 3 candidate lessons and the landing; functional and code review closed by operator override (above).
  Advisories logged, not fixed (landing message; `TokenBuilder` counter-field comment and constructor Javadoc and the
  message of commit `627f2b5b` still describe only `MISSING_CLAIM` counting; `UncheckedThrowBoundaryTest` EXEMPT
  comments name `extractClaims` where the sanctioned broad catch is `mapClaim`).
- CI/merge: `ci checks status` on #855 returns `overall_status: success` over 24 checks, `integration-tests`,
  `e2e-tests` and `Run JMH Benchmarks` included. Merged through the merge queue as `0c56583e` and read back as
  `state: merged`. The merge was under a `barrier-ask-override` (`review-barrier-gap`): CodeRabbit had not reviewed the
  final head `8986a132` (its one-per-hour quota was spent on the previous push). All its earlier comments were fixed.
  `NativeTokenValidatorProducerIT` was not run locally and whether the native IT ran in CI is unconfirmed.
- Sonar: gate `OK` on PR 855 read directly (new coverage 95.8, duplication 0.0, hotspots reviewed 100, all ratings 1).
  The `main` branch, read through the Sonar REST API after the merge analysis (revision `0c56583e`, analysed
  2026-11-05T07:51:36Z): **1 open issue, down from 44**, `java:S8786` at `JwtTokenTamperingUtil.java:450`. Gate `OK`.
- Concurrency: PLAN-23 ran beside PLAN-10, PLAN-11, PLAN-13 and PLAN-14. The one collision, on
  `RefreshFailureClassificationTest.java`, was predicted by the Watch and cost a rebase. No collision with PLAN-13 or
  PLAN-14 was reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-23 --status shipped`
- [x] row `pr` stamped — value 855
- [x] row `landing` stamped — value `landings/PLAN-23.md`
- [x] row `plan_marshall_plan_id` already `implement-plan-23-sonar-backlog-cleanup`; it matches the message sender
- [x] `epic.md`: Decisions entry, Queue annotation, the Sonar item rewritten (44 down to 1), the PLAN-11 counting
      advisory marked resolved, Watches; no new Open Defect (the landing is complete)
- [x] three candidate-lesson messages dispositioned (see Follow-Ups)
- [x] resume anchor updated in `resume_anchor.md`; `queue-view.md` regenerated

## Follow-Ups

- The residual `S8786` (`AZ7xhrgAXGjoVdkwztNZ`) is FOLDED into PLAN-15 deliverable 1 (TV-6), which already edits the
  same tampering helper; `JwtTokenTamperingUtil.java` added to its Expected Surface. It is a tool finding under the
  standing "fix immediately" rule.
- PLAN-22 and PLAN-17 declared files PLAN-23 changed (`TokenValidator.java`, `AccessTokenCache.java`,
  `StepUpHandler.java`, `AccessTokenValidationPipeline.java`, `HttpJwksLoaderConfig.java`) and re-read them at outline;
  PLAN-22 is now unblocked (its dependency on PLAN-23 is met). PLAN-13 and PLAN-15 declare `WellKnownConfig.java`.
  PLAN-14 and PLAN-15 declare `DiscoveryResolver.java`. All re-read at outline.
- Candidate lessons: 001 (`test-integrity` grades against the `origin/main` tip, not the merge-base) and 002 (the
  Maven `module-tests` arm reported as un-gated on every push) are plan-marshall-owned and went into a 2026-11-05
  PLAN-23 addendum of `upstream-plan-marshall-report.md`; 003 (an owed `architecture enrich` hint about `java:S1612`)
  is outside the epic and is recorded as an operator-owed item in `epic.md`.
