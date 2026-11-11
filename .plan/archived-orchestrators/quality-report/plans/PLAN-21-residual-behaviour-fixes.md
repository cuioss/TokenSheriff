# PLAN-21: Residual behaviour fixes left open by PLAN-01 to PLAN-05

epic: quality-report
workstream: WS-01

## Objective

Five behaviour-level residuals were merged unfixed by the plans that found them (landing messages and PR
bodies, recorded as Watches). Under the operator's standing rule that findings are fixed immediately, close
them: a loader initialization path that can leave the status at `LOADING` (PLAN-01), a suppressed catch-all
in the Quarkus producer's `destroy()` (PLAN-01), public `applyRefresh` overloads that cannot tell a stale
external result from a newer re-login grant (PLAN-02), `AccessTokenCache.put()` accepting an entry without
`iat` when a max age is set (PLAN-03), and a top-level JSON `null` JWT header that is rejected but neither
logged nor counted (PLAN-05). No finding ID: these are residuals, not report findings.

## Deliverables

1. **Loader init path** — `HttpJwksLoader.initialize()`: an unexpected exception must not leave the status at
   `LOADING`; it must end in a terminal or retry-pending status with a LogRecord. Test: inject a failure on
   the unexpected path and assert the status.
2. **Producer destroy** — `TokenValidatorProducer.destroy()`: replace the retained suppressed catch-all with
   narrowed handling (or, if the SPI carve-out in `AGENTS.md` applies, the sanctioned suppression with its
   reason). Test: a failing close still releases the remaining resources.
3. **applyRefresh base token** — the public `applyRefresh` overloads gain a way to name the grant the result
   was derived from, so a late external result cannot replace a newer re-login grant. This is a public
   signature change: keep the old overloads deprecated or remove them only if the outline shows no external
   caller. Test: late result after a re-login is refused.
4. **Cache put without iat** — `AccessTokenCache.put()` rejects (or safely handles) an entry without `iat`
   when a max age is configured, so eviction can no longer throw `MISSING_CLAIM`. Test through the public API.
5. **Null header accounting** — a top-level JSON `null` JWT header is logged and counted like every other
   rejected header, in `NonValidatingJwtParser`. Test: the event type and the counter.

## Standing Constraints

- Every claim below came from a landing message or PR body, not from a code read; re-verify each against the
  implementing source at outline before scoping on it (refutation loops back to re-scope; a residual that no
  longer exists is recorded and dropped). Claims were last checked against HEAD `a56b1b59` only for file
  existence. The original report text is in `.plan/orchestrator/quality-report/source-report/`.
- Do NOT edit `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and `doc/LogMessages.adoc`
  updated in the same PR for any new identifier. Allocate the identifier at outline: `TokenSheriff-170` is
  taken (PLAN-07), `156` is retired, and `168` is duplicated until PLAN-13.
- Operator policy: tool findings (OpenRewrite markers, Sonar issues) are fixed immediately; suppression only
  in the rule's sanctioned syntax with a stated reason.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Scope guard: five deliverables in four modules. If the outline finds deliverable 3 larger than the rest,
  split it out rather than letting the plan grow.

## Claim Labels

- HYPOTHESIS: an unexpected exception in `HttpJwksLoader.initialize()` leaves the status at `LOADING` — PLAN-01 landing residual; verify at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` § `initialize` (verify-at-outline; PLAN-07 reworked this class)
- HYPOTHESIS: `TokenValidatorProducer.destroy()` retains a suppressed catch-all — PLAN-01 landing residual; verify at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorProducer.java` § `destroy` (verify-at-outline)
- OBSERVED: `TokenLifecycleManager` documents `applyRefresh` as public and as holding the grant stripe across its atomic update — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § class Javadoc
- HYPOTHESIS: the public `applyRefresh` overloads take no base-token argument, so a late external result can replace a newer re-login grant — PLAN-02 landing residual (pre-existing); verify at the same file (verify-at-outline)
- HYPOTHESIS: `AccessTokenCache.put()` does not require `iat` when a max age is set, and a direct public-API `put` could make eviction throw `MISSING_CLAIM` — PLAN-03 landing residual; verify at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` § `put` (verify-at-outline)
- HYPOTHESIS: a top-level JSON `null` header is rejected but neither logged nor counted (pre-existing) — PLAN-05 landing residual; verify at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` § header decode (verify-at-outline)

## Expected Surface

- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoader.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorProducer.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/NonValidatingJwtParser.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java` and `doc/LogMessages.adoc` (only if a LogRecord is added — verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-20 (same file, `TokenLifecycleManager.java`; PLAN-20 removes the OpenRewrite marker first)
- Overlaps with: PLAN-13 (`HttpJwksLoader.java`, `JWTValidationLogMessages.java`, `doc/LogMessages.adoc`), PLAN-09 and PLAN-17 (`NonValidatingJwtParser.java`), PLAN-20 (`TokenLifecycleManager.java`) and PLAN-22 (`AccessTokenCacheTest.java`) — read by `corpus declaration-currency` at staging; PLAN-13, 09 and 17 run after PLAN-21 and re-verify at outline, PLAN-20 and PLAN-22 are sequenced as stated here
- Adjacent to: PLAN-22 (documentation and test residuals of the same plans)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-21-residual-behaviour-fixes.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
