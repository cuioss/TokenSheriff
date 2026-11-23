# PLAN-10: `token-sheriff-bff-quarkus` — Keycloak integration tests, native image, user documentation

epic: client-framework-aspects
workstream: WS-04

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted. If the ADR rejects Part C, the
> orchestrator retires this spec. Otherwise re-ground it against the merged ADR at outline.

## Objective

Prove the extension end to end, against a real identity provider and in native mode, and document it for
the first consumer.

The integration tests run against Keycloak in `token-sheriff-quarkus-integration-tests`. They cover:

- login with a return address, the callback, the session cookie's flags, and the identity reaching a
  protected resource;
- logout ending the session;
- a back-channel logout ending the session;
- the CSRF refusals;
- the full pending-login store answering `503`;
- the session-info sentinel answering `401` before login and the curated view after it, with repeated
  polls leaving the idle deadline unchanged;
- one step-up into the same session.

The same flows must pass with the integration-test application built as a native image. The user
documentation, written for a consumer like Audit Notary, explains the configuration, the hooks, and what
the application still owns.

## Deliverables

1. **Integration-test wiring.** The new runtime artifact at compile scope and the deployment artifact at
   test scope in `token-sheriff-quarkus-integration-tests/pom.xml`; a protected test resource; the
   Keycloak realm additions (a client with back-channel logout and an `acr` step-up level).
   - Skills: pm-dev-java:junit-integration, pm-dev-java:java-quarkus
   - Persona: integration-tester
2. **The IT suite** for the flows in the Objective, using the existing integration-test conventions
   (Failsafe, `*IT`).
   - Skills: pm-dev-java:junit-integration, pm-dev-java-cui:cui-http-testing
   - Persona: integration-tester
3. **Native proof.** The same IT suite passes against the native build of the integration-test
   application, so the extension's own native registration (PLAN-08) is proven. This also discharges the
   epic Watch "native build on a real consumer" for the client jar's `reflect-config.json`.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:junit-integration
   - Persona: integration-tester
4. **User documentation** in the `doc/bff/` set:
   - a getting-started guide;
   - a configuration reference for `sheriff.bff.*`, generated with `quarkus-config-doc` if the project
     does that for the other extensions;
   - the hooks (`ReturnAddressPolicy`, `SessionAugmentor`, `StepUpRequirement`);
   - the session-ended listener for closing long-lived connections;
   - "what stays in your application" (addendum §7);
   - the per-process limitation.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- The new ITs pass in JVM and native mode in CI: they are new and fail at the PLAN-09 merge commit.
- The existing integration tests hold at `9c242518` and after.
- The user documentation is linked from `doc/README.adoc`.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Failsafe 3.6.0 ignores `-DskipTests`, so always pass `-DskipITs` when ITs must be skipped. Never let an
  unintended IT run poison the JaCoCo gates (project memory).
- Do not change existing ITs' expectations. New flows get new IT classes.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: the integration-test module and an e2e Playwright module exist under the quarkus parent — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/` and `token-sheriff-quarkus-parent/e-2-e-playwright/` § directory listing (orchestrator ls 2026-11-23).
- OBSERVED: Audit Notary's ITs against Keycloak cover cookie flags, logout, CSRF and the return address — read at `Audit-Notary/integration-tests/src/test/java/` § `WebLoginIT`, `LogoutIT`, `CsrfIT`, `ReturnAddressIT` (leaf report). They are the model for the flows, not code to copy.
- HYPOTHESIS: the integration tests already run a Keycloak realm that can be extended with a back-channel logout client and an `acr` level — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/` § the realm import file (verify-at-outline).
- HYPOTHESIS: the integration-test application is already built natively in CI — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/pom.xml` § native profile (verify-at-outline).

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/`
- HYPOTHESIS: `doc/bff/` — documentation set (verify-at-outline)
- OBSERVED: `doc/README.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-09, PLAN-07.
- Overlaps with: none of the staged specs on resolved paths. PLAN-09 shares `doc/bff/` and is already
  sequenced.
- Adjacent to: `token-sheriff-quarkus-parent/e-2-e-playwright/` (left untouched unless the outline finds
  that a browser test is the only way to prove the callback page).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-10-bff-integration-native-and-docs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
