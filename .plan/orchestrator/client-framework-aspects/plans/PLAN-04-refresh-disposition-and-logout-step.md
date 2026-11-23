# PLAN-04: CLIENT-32 and CLIENT-33 — published refresh disposition and logout step

epic: client-framework-aspects
workstream: WS-02

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Publish two rules that each consumer re-derived, so that a session layer applies them instead of
re-inventing them.

**CLIENT-32 publishes the refresh disposition.** It is a table mapping each
`RefreshFailureClassification.Kind`, plus the two cases that arise when the session is written (persist
failed, session already terminated), to three outcomes:

- the fate of the session: kept or ended;
- the token to revoke: none, the presented one, or the successor;
- the log record.

Two versions of this table exist today. `TokenLifecycleManager` applies one internally (ADR-0005 and
ADR-0010). API Sheriff applies a richer one in `TokenRefreshCoordinator.disposeRefusal` (ADR-0046).
CLIENT-32 publishes one reconciled table, and `TokenLifecycleManager` uses it.

**CLIENT-33 is a logout step.** It takes the token record the caller has ALREADY removed from its
session, so the signature itself enforces "destroy locally first". It revokes best effort and never
throws. The first failure is logged at WARN and later ones at DEBUG (latched, as in API Sheriff's
`RpInitiatedLogout`). It returns the end-session redirect, or none. Which tokens it revokes is ADR-0032's
decision (PLAN-01, question 2).

## Deliverables

1. **CLIENT-32 table** in `lifecycle`: an immutable, documented type answering
   `dispose(Kind | WriteOutcome)`. Its rows must reproduce `TokenLifecycleManager`'s current switch
   exactly for the four kinds, unless the reconciliation deliberately changes a row. A deliberate change
   is named in the PR and in ADR-0032's follow-up note.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
2. **`TokenLifecycleManager` reads the published table** instead of its private switch. Its behaviour is
   preserved row for row, so these consumer-visible edges must hold, each with a test:
   - `REDEEMED` quarantines the session and revokes the successor best effort;
   - `CREDENTIAL_REJECTED` clears and revokes nothing;
   - `PRE_REDEMPTION` keeps the session;
   - `SCOPE_REFUSED` keeps the session;
   - an unknown kind takes the failing default arm.
   - Skills: pm-dev-java:java-core
   - Persona: implementer
3. **CLIENT-33 logout step** in `logout`. It composes `RevocationClient.revokeIfSupported` and
   `EndSessionFlow.buildLogoutRedirect`, applies the latched WARN/DEBUG rule through `ClientLogMessages`,
   and revokes the token set ADR-0032 fixes.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-logging, pm-dev-java:javadoc
   - Persona: implementer
4. **Tests:**
   - every table row;
   - the manager's preserved edges;
   - the logout step on revocation success, failure, an unsupported endpoint, no end-session endpoint,
     and a second failure (logged at DEBUG).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java-cui:cui-http-testing
   - Persona: module-tester
5. **Requirements and specification:**
   - CLIENT-32 and CLIENT-33 in `doc/client/requirements.adoc`;
   - the refresh section of `doc/client/specification/token-handling.adoc` and the logout section of
     `doc/client/specification/step-up-and-logout.adoc`;
   - log rows in `doc/LogMessages.adoc` and test-mapping rows.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- The published table and the logout step are covered by tests that fail at `9c242518`.
- `TokenLifecycleManager`'s refresh-refusal behaviour holds at `9c242518` and after: its existing
  lifecycle tests stay green unchanged, beside the new edge tests.
- `./mvnw -pl token-sheriff-client -am verify` is green.
- CLIENT-32 and CLIENT-33 are documented.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Preserve `TokenLifecycleManager`'s observable refresh behaviour unless a row change is deliberate and
  named.
- The logout step never throws on a revocation failure and never logs a token value.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: `TokenLifecycleManager` switches on the refusal kind internally, with `case REDEEMED` at line 1006 and `case CREDENTIAL_REJECTED` at line 1020 — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § refresh-refusal switch (orchestrator grep 2026-11-23).
- OBSERVED: `RefreshFailureClassification.Kind` has `PRE_REDEMPTION`, `SCOPE_REFUSED`, `CREDENTIAL_REJECTED`, `REDEEMED`; `RefreshFlow.classify(Throwable)` is public — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java` § line 366.
- OBSERVED: `revokeAndClear` only removes and returns the tokens and does not call the server — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `revokeAndClear` (line ~1295, leaf report).
- OBSERVED: `EndSessionFlow` builds the redirect and revokes nothing — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/EndSessionFlow.java` § `buildLogoutRedirect` (leaf report).
- OBSERVED: API Sheriff's table adds persist-failure and session-terminated rows and revokes the successor or the presented token — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/refresh/TokenRefreshCoordinator.java` § lines 565-585 (leaf report).
- OBSERVED: API Sheriff latches the first revocation failure at WARN (`ApiSheriff-147`), later ones DEBUG, and revokes only the refresh token — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/logout/RpInitiatedLogout.java` § lines 60-61, 256-266 (leaf report).
- HYPOTHESIS: the two tables agree on the four kinds and differ only in the write-outcome rows and in which token is revoked on `REDEEMED` — confirm/refute at `TokenLifecycleManager.java` § `revokeAndClearFailClosed` (~1066-1174) against `TokenRefreshCoordinator.java` § `disposeRefusal` (verify-at-outline).
- Verify-first clause: the revoked token set at logout is ADR-0032's decision. If PLAN-01 has not landed, stop.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/RefreshDisposition.java` — new type (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/LogoutStep.java` — new type (verify-at-outline)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/token-handling.adoc`
- OBSERVED: `doc/client/specification/step-up-and-logout.adoc`
- OBSERVED: `doc/LogMessages.adoc`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first), PLAN-01 (logout revocation rule).
- Overlaps with: PLAN-02 (logout tests directory, step-up-and-logout specification, requirements, test
  mapping) and PLAN-03 (requirements, log messages, test mapping). Run in sequence.
- Adjacent to: `lifecycle/RevocationClient.java` and `logout/EndSessionFlow.java`, which are used
  unchanged. PLAN-06 (SESSION-5 and SESSION-6) consumes both rules.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-04-refresh-disposition-and-logout-step.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
