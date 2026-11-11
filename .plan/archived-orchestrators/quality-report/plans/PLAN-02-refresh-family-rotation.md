# PLAN-02: Refresh-token family rotation correctness

epic: quality-report
workstream: WS-02

## Objective

Stop the client from destroying a legitimate session as false "refresh-token reuse". When a new
grant (re-login, step-up) is stored into an existing session, the rotation family must be reseeded
so the next refresh is not treated as reuse (C-1). The refresh path must use the plausibility-capped
`expires_in` (C-2). Pin the post-eviction replay behaviour of the bounded family map and make the
caps testable without reflection (TC-6, C-8b). Closes C-1, C-2, C-8b, TC-6.

## Deliverables

1. **C-1** — `TokenLifecycleManager.store()` reseeds the session's `RefreshTokenFamily` when the
   incoming refresh token differs from the family's current one and is not a known superseded
   token (stale replay stays reuse). Public `applyRefresh` overloads advance the family too (or are
   narrowed). Test: `store(A)` → `store(B)` → refresh(B) succeeds; replay of superseded A still
   revokes.
2. **C-2** — `RefreshFlow` / `RotationResult` carry the capped `TokenResponse.getExpiresInSeconds()`
   (absent → no expiry) instead of the raw field. Test with an absurd `expires_in`.
3. **C-8b / TC-6** — family and JTI caps become constructor-injectable (package-private), the
   reflection in `TokenLifecycleManagerFamilyEvictionTest` is removed, and a test replays a
   superseded token after its family was evicted, pinning the resulting behaviour (document the
   accepted residual risk in the class Javadoc if reuse can no longer be detected).

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Reuse detection must not be weakened: a replayed superseded token must still revoke the family
  whenever the family is still tracked.

## Claim Labels

- OBSERVED: `store()` uses `families.computeIfAbsent(sessionId, …)`, so an existing family is left intact on a new grant — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `store`
- OBSERVED: `doRefresh` calls `family.rotate(presented, …)` and `revokeReusedFamily` on mismatch — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `doRefresh`
- OBSERVED: no test covers store(A) → store(B) → refresh — swept `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RotationReuseDetectionTest.java`
- OBSERVED: `RefreshFlow` passes raw `tokenResponse.expiresIn` into `RotationResult`; the capped accessor `getExpiresInSeconds()` is unused on this path — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`
- OBSERVED: `MAX_TRACKED_FAMILIES = 10_000` (LRU) and `MAX_TRACKED_JTIS = 10_000`; eviction test reads private fields via `Field.setAccessible` — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerFamilyEvictionTest.java` § `shouldBoundFamilyMapGrowth`
- HYPOTHESIS: after family eviction a superseded token is accepted as a fresh family root (no reuse detection) — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `doRefresh` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/RefreshTokenFamily.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/RotationResult.java`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/DpopProofGenerator.java` (JTI cap injectability — verify-at-outline)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RotationReuseDetectionTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerFamilyEvictionTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshAdversarialTest.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-10 (`DpopProofGenerator.java` only if the JTI cap is touched — single file)
- Adjacent to: Keycloak refresh ITs (`RefreshSingleUseSpecIT`) — unchanged; they must stay green

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-02-refresh-family-rotation.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
