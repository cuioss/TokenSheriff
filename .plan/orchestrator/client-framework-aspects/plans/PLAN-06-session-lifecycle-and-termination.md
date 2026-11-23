# PLAN-06: `token-sheriff-session` — login, refresh, termination, ended event (SESSION-4 to SESSION-7)

epic: client-framework-aspects
workstream: WS-03

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted, so re-ground it against the merged
> ADR at outline. Wherever the ADR decides differently, the ADR wins and the plan re-scopes.

## Objective

Add to `token-sheriff-session` the operations that end or renew a session.

- **SESSION-4.** A completed login ends the session whose handle the browser presented, before it
  creates the new one.
- **SESSION-5.** Refresh near expiry, one refresh per session at a time, with back-off. The disposition
  follows the published CLIENT-32 table (PLAN-04). Groups and identity values are taken from a refreshed
  ID token when there is one.
- **SESSION-6.** RP-initiated logout destroys the session first and then runs the CLIENT-33 logout step
  (PLAN-04). A back-channel logout ends the sessions the CLIENT-35 selector names (PLAN-02) and revokes
  nothing. The store is per process; a boot notice states this, as ADR-0032 decides.
- **SESSION-7.** Every end of a session publishes one framework-neutral event carrying the session
  identity and the cause: logout, back-channel, expiry, bound, replaced by a login, or refresh.

The generic half of API Sheriff's `bff/refresh/TokenRefreshCoordinator` (single-flight, back-off,
disposition; about 600 of 1,141 lines) and its `bff/logout/RpInitiatedLogout`,
`BackchannelLogoutReceiver` and `bff/session/SessionRelayRegistry` are the extraction sources. The
cause enum of SESSION-7 is new design: API Sheriff's listener has no cause.

## Deliverables

1. **SESSION-4 and SESSION-6 termination paths.** Login-replaces-session, RP-initiated logout over
   CLIENT-33, back-channel termination over the CLIENT-35 selector, and the per-process boot notice.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-logging
   - Persona: implementer
2. **SESSION-5 refresh coordination.**
   - Single-flight per session; a bounded back-off map (API Sheriff caps it at 10,000 entries); a bounded
     number of concurrent revocations (API Sheriff: 64).
   - The disposition comes from the CLIENT-32 table, never a local switch. Identity values are taken
     from the refreshed ID token, which must have passed `RefreshResultVerifier` with the session's
     subject bound.
   - The scope-driven refresh leg is API Sheriff's gateway concern; leave it out.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-logging
   - Persona: implementer
3. **SESSION-7 session-ended event.** A listener SPI with one callback carrying the identity and a cause
   enum. Exactly one event is published per ended session, whichever path ended it. A listener that
   throws never blocks the end of the session.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc
   - Persona: implementer
4. **Tests.** Cover:
   - logout beating an in-flight refresh;
   - every disposition row as seen through the session;
   - back-channel termination by `sid` and by `sub`;
   - exactly one event per end and per cause, and a throwing listener;
   - single-flight under concurrency.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java-cui:cui-http-testing
   - Persona: module-tester
5. **Requirements, specification and threat-model entries** for SESSION-4 to SESSION-7 in the module's
   documentation set, plus test-mapping rows.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- `./mvnw -pl token-sheriff-session -am verify` is green.
- SESSION-4 to SESSION-7 are covered by tests that fail at the PLAN-05 merge commit, where these
  operations do not exist.
- The module consumes CLIENT-32, CLIENT-33 and CLIENT-35 rather than re-implementing them: no
  `switch` over `RefreshFailureClassification.Kind` in the module.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Framework-neutral: no Quarkus, Vert.x or CDI. Scheduling is the caller's; the module exposes
  `sweep()` and refresh triggers.
- Never revoke on back-channel termination: the provider has already ended the session.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: API Sheriff's refresh coordinator implements single-flight (`inFlight` map), a 5 s back-off (`PRE_REDEMPTION_RETRY_BACKOFF`), `MAX_BACKOFF_ENTRIES = 10_000`, and `MAX_CONCURRENT_REVOCATIONS = 64` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/refresh/TokenRefreshCoordinator.java` § lines 206-232 (leaf report).
- OBSERVED: API Sheriff's `SessionEndListener` has only `sessionEnded(String sessionId)` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/session/SessionEndListener.java` § line 40 (orchestrator grep 2026-11-23).
- OBSERVED: API Sheriff's callback destroys the presented session before binding the new one — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/CallbackEndpoint.java` § lines 516-518 (leaf report).
- OBSERVED: Audit Notary never refreshes and does not end the browser's previous session on login — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/security/LoginService.java` § `complete` (leaf report).
- HYPOTHESIS: CLIENT-32, CLIENT-33 and CLIENT-35 have landed in the shapes PLAN-02 and PLAN-04 specify — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/` and `.../logout/` § the published types (verify-at-outline).
- Verify-first clause: re-ground against the merged ADR-0032 and the landed PLAN-02 and PLAN-04 before scoping.

## Expected Surface

- HYPOTHESIS: `token-sheriff-session/` — module source and tests (verify-at-outline)
- HYPOTHESIS: `doc/session/` — documentation set (verify-at-outline)
- OBSERVED: `doc/developer/test-impl-mapping.adoc`
- OBSERVED: `doc/LogMessages.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first), PLAN-05, PLAN-04, PLAN-02.
- Overlaps with: PLAN-05 and PLAN-07, inside the new module; strictly sequential.
- Adjacent to: `token-sheriff-client/.../lifecycle/` and `.../logout/` (consumed, unchanged).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-06-session-lifecycle-and-termination.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
