# Landing Analysis: PLAN-07 — JWKS staleness bound, re-discovery, and well-known timeouts

epic: quality-report
workstream: WS-01
pr: #844 (https://github.com/cuioss/TokenSheriff/pull/844)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-07-jwks-staleness-timeouts-006.md` against the merged PR and
> the merge commit. Corroborated by this analysis: PR state `merged`, merge commit `50c295e6`, the PR
> title/body, the merged file footprint (`git show --name-only 50c295e6`), the new LogRecord and the
> `LoaderStatus` diff, the Sonar quality gate and a direct unresolved-issue query. NOT corroborated:
> code-level behaviour (no build or test was run here) and every figure that comes only from the
> landing message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| V-7 remainder: connect/read timeouts reach the well-known transport | shipped-as-specified | PR body "V-7 (remainder)"; `HttpJwksLoaderConfig.java` changed; `HttpJwksLoaderConfigWellKnownTest.java` and `HttpJwksLoaderConfigTest.java` changed. Both the discovery handler and the derived JWKS handler honour the timeouts, defaults 2 s and 3 s |
| V-10a staleness bound, logged once, safe default | shipped-modified | PR body "V-10a". The spec offered "max staleness or N consecutive failures"; the PR took the max-staleness arm, as an opt-in knob that is off by default, so today's availability is unchanged. `HttpJwksLoader.java` tracks the last success; a new LogRecord `TokenSheriff-170` (`JWKS_KEYS_STALE`) is logged once per degradation. No new `LoaderStatus` value: the existing `UNDEFINED` is reused (`LoaderStatus.java` Javadoc only). When the bound is configured and exceeded, the issuer's tokens are rejected until a refresh succeeds and no installed key is dropped |
| V-10b re-discovery of `jwks_uri` after repeated refresh failures | shipped-as-specified | PR body "V-10b"; `HttpJwksLoader.java`, new `HttpJwksLoaderRediscoveryTest.java` with `DiscoveryGenerationDispatcher.java`. After three consecutive failed refresh cycles in well-known mode, discovery runs again through the existing `resolveJWKSAdapter()` path, so the issuer gate and egress guard apply each time; a rejected re-discovery keeps the current endpoint and keys. The spec's HYPOTHESIS that this is safe against the #818 issuer gate is confirmed by the design, not by a code read here |
| Added, unplanned | added-unplanned | **Behaviour change outside the loader**: `AccessTokenValidationPipeline.java` changed so a cached token is checked against the issuer's current health before validation (the PR's release note: "degraded issuers are not accepted from cache"). `IssuerConfigCache.java` changed, with `IssuerConfigCacheRecoveryTest.java` for issuer admission under a configured bound. Docs: `openid-discovery.adoc`, `api-reference.adoc`, an amendment to ADR 0017, and `doc/LogMessages.adoc` |
| Declared but not touched | n/a | none: all seven declared paths were touched, including both HYPOTHESIS paths (`JWTValidationLogMessages.java`, `doc/LogMessages.adoc`) |

Surface delta: 16 merged paths against 7 declared. All seven declared paths were touched; nine merged
paths were not declared: `LoaderStatus.java`, `IssuerConfigCache.java`,
`AccessTokenValidationPipeline.java`, `IssuerConfigCacheRecoveryTest.java`,
`DiscoveryGenerationDispatcher.java`, `HttpJwksLoaderRediscoveryTest.java`, `openid-discovery.adoc`,
`api-reference.adoc` and ADR 0017. No Quarkus config key was added for the knob, and the PR is silent on
Quarkus; the spec allowed that only if trivial.

## Metrics and Anomalies

- Tokens: 27,858,948 (landing message; a measured-subset floor, since many steps recorded `unmeasured`).
- Duration: 66,146 s wall (landing message).
- Effort: `medium`; model `claude-sonnet-5-5`. Self-reported at start: the runtime stamp could not be refreshed at landing (`model_unknown`).
- Anomalies:
  - CodeRabbit raised seven comments, all fixed, and the review loop went back twice; they were real defects in the generation fence (a stale cycle's failure count leaking across `close()`, a fetch after close, an unusable advertised `jwks_uri` skipping the retained-endpoint fetch, a suppression flag with no lift). Candidate lesson 001.
  - Commit trailers named the wrong model; the rewrite left change-ledger rows pointing at old SHAs and refused the 5-execute to 6-finalize transition until repaired (candidate lesson 002). Plan-marshall tooling.
  - The Performance Benchmark job was still running when the post-merge wait timed out; the other 23 checks were green.
  - The installed executor lacks `pre_merge_gate record-merge` and `review_completeness roster`.

## Routing and Merge Behavior

- Review: a `barrier-ask-override` over `review-barrier-gap` was recorded at head `75dc96e0`: the operator merged without a CodeRabbit review of the final test-and-doc-only commit (its quota was spent for about an hour). The CodeRabbit check on the PR itself passed. `branch-cleanup` was recorded with the clean merge wording, so a reader of that step alone does not see the grant. Landing message; not independently verified.
- Code review: 0 blocking, 0 advisory (landing message; no rules cited).
- CI/merge: merged through the merge queue as `50c295e6` (read from the PR: `state: merged`).
- Sonar: landing says `new_code_issue_count=0`, `count_status=confirmed`. Cross-checked here: gate `OK` on PR 844 (new coverage 92.5, duplication 0.0, all ratings 1), and a direct query returned 0 open issues.
- `adr-propose` ran and amended ADR 0017 (no new ADR number).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-07 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-07 --field pr --value 844`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-07 --field landing --value landings/PLAN-07.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-07-jwks-staleness-timeouts`
- [x] `findings-disposition.md`: V-7 and V-10 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened (unplanned cache-health check, wall-clock staleness, no Quarkus key, quota)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: PLAN-13 declares `HttpJwksLoader.java`, `doc/LogMessages.adoc` and `JWTValidationLogMessages.java`, all three changed (the planned sequencing PLAN-07 → PLAN-13, now satisfied). PLAN-13 must re-ground its log-record claims: `TokenSheriff-170` is new, and `TokenSheriff-156` was retired by PLAN-05. Its V-14 duplicate identifier 168 finding (`DUPLICATE_JWKS_KID` vs `TransportLogMessages`) is untouched, as the landing confirms. PLAN-03 and PLAN-01 also match but already shipped.
- PLAN-08 (Quarkus config/CDI): no Quarkus key exposes `maxStaleness`. Record as a candidate for PLAN-08 or leave it unexposed.
- The unplanned `AccessTokenValidationPipeline` change adds a health check on the cache-hit path. PLAN-03's cache-hit policy work and PLAN-09's expiry claims should be read against it at outline.
- Staleness is measured on the wall clock (accepted after a CodeRabbit comment). Not fixed, by design; recorded as a Watch.
- Five `candidate-lesson` messages are queued and not yet dispositioned: one names a component of this repo (fence every closed-generation side effect, not only the publish), four name `plan-marshall:*` components (commit-trailer resolution, documented verbs the installed scripts lack, a leaf self-transitioning the phase, invented script subcommands).
