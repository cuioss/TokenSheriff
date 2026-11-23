# PLAN-07: `token-sheriff-session` — step-up into the session and the binding seam (SESSION-8, SESSION-9)

epic: client-framework-aspects
workstream: WS-03

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted, so re-ground it against the merged
> ADR at outline. Wherever the ADR decides differently, the ADR wins and the plan re-scopes.

## Objective

Finish `token-sheriff-session` with the two remaining parts of the outline.

- **SESSION-8, step-up into the same session.** A step-up result verified with the session's subject
  bound (CLIENT-34, PLAN-02) updates the session through `persist`, keeping the new `acr`, `amr` and
  `auth_time`. The step-up transaction carries an opaque *pending action* the consumer supplies (for
  example "release document X at revision Y"). On return the consumer gets that action back with the
  verified values and re-checks it itself.
- **SESSION-9, binding seam.** A `SessionBinding` SPI with "raw cookie header in, Set-Cookie out", in
  the shape of API Sheriff's. Server mode is the implementation this module ships. Whether the
  sealed-cookie mode moves here is decided by ADR-0032 (question 6). If it moves, that move is a
  SEPARATE follow-up plan staged after this one lands (about 2,253 lines plus about 3,725 test lines). It
  is never folded into this plan.

SESSION-8 is new design: API Sheriff's step-up is triggered by an upstream challenge and has no pending
action, and Audit Notary has specified step-up (AUN-AUTH-4, UC-ACC-3) but not built it. Design against
Audit Notary's specification.

## Deliverables

1. **SESSION-8 step-up transaction and session update.** The pending action is opaque and size-bounded.
   The transaction is single-use, its lifetime bounded like a pending login. A result for another `iss`
   or `sub` is refused (CLIENT-34). The update goes through `persist`, so a logout during the step-up
   wins.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
2. **SESSION-9 binding SPI with the server-mode implementation**, in the shape of API Sheriff's
   `SessionBinding` and `ServerSessionBinding`.
   - Raw `Cookie` header in, `Set-Cookie` values out.
   - Handle re-issue keeps the identity.
   - The binding declares whether it can end a session on behalf of the identity provider (API Sheriff:
     `IdpDestruction.UNSUPPORTED` in cookie mode). A back-channel endpoint therefore knows when to answer
     404.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety
   - Persona: implementer
3. **Tests.** Cover:
   - a step-up with the same subject, a different subject, a replayed transaction, an expired
     transaction, and a logout during the step-up;
   - binding round-trip, handle re-issue, a malformed cookie header, and the capability flag.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **Documentation and threat-model entries** for SESSION-8 and SESSION-9, plus test-mapping rows. If
   ADR-0032 keeps cookie mode in API Sheriff, state that this seam is what keeps it possible.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- `./mvnw -pl token-sheriff-session -am verify` is green.
- SESSION-8 and SESSION-9 are covered by tests that fail at the PLAN-06 merge commit.
- The module documentation lists SESSION-1 to SESSION-9 complete.
- The cookie-mode follow-up is either staged by the orchestrator, if ADR-0032 moves cookie mode, or
  recorded as not needed.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Framework-neutral; no Quarkus, Vert.x or CDI.
- The pending action is opaque to the module: never parse or interpret it.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: API Sheriff's `SessionBinding` is framework-neutral ("raw Cookie header in, Set-Cookie out") — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/session/SessionBinding.java` § line 67 (leaf report).
- OBSERVED: API Sheriff's step-up is driven by an upstream 401 `insufficient_user_authentication` challenge and replaces the session as an ordinary login — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/refresh/StepUpCoordinator.java` § lines 57-64 (leaf report).
- OBSERVED: Audit Notary specifies step-up with same-`sub` binding but has not built it — read at `Audit-Notary/doc/specification/authentication.adoc` § AUN-AUTH-4 and `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/config/Action.java` § line 33 (leaf report).
- OBSERVED: the sealed-cookie mode is 2,253 main lines and 3,725 test lines in API Sheriff's `bff/cookie/` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/cookie/` § `wc -l` (leaf report).
- Verify-first clause: re-ground against the merged ADR-0032 (questions 5 and 6) and the landed PLAN-05 and PLAN-06 before scoping.

## Expected Surface

- HYPOTHESIS: `token-sheriff-session/` — module source and tests (verify-at-outline)
- HYPOTHESIS: `doc/session/` — documentation set (verify-at-outline)
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-06, PLAN-02.
- Overlaps with: PLAN-05 and PLAN-06, inside the new module; strictly sequential.
- Adjacent to: API Sheriff's `bff/cookie/` (stays there unless ADR-0032 moves it, in a separate plan).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-07-session-step-up-and-binding-seam.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
