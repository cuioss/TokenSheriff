# PLAN-09: `token-sheriff-bff-quarkus` — CSRF, SecurityIdentity, application hooks, limits (BFF-4 to BFF-7)

epic: client-framework-aspects
workstream: WS-04

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted. If the ADR rejects Part C, the
> orchestrator retires this spec. Otherwise re-ground it against the merged ADR at outline.

## Objective

Complete the extension's request-side behaviour.

- **BFF-4, CSRF.** It applies to unsafe methods and cannot be turned off. `Origin` must match the trusted
  origins, or `Sec-Fetch-Site: same-origin` must be present. An optional anti-CSRF token comes with the
  session view and is carried in a header; whether it is optional or mandatory is per ADR-0032. Logout
  needs that proof, or a confirmation.
- **BFF-5, Security identity.** A filter resolves the session and sets the Quarkus `SecurityIdentity`:
  the principal, the groups as roles, and the identity values as attributes. It calls `recordAccess`
  ONLY for requests it lets through. A request without a session gets `401` with the login address in
  the body, or a redirect for a navigation.
- **BFF-6, hooks the application supplies.**
  - `ReturnAddressPolicy`: by default same-origin paths that start with `/`, contain no `//` and no `\`,
    and are bounded in length.
  - `SessionAugmentor`: values added at login.
  - `StepUpRequirement`: maps a pending action to a step-up address.
  - `SessionViewContributor`: adds the application's domain members to the BFF-9 session-info view
    (PLAN-08), for example Audit Notary's rights, pseudonym and first-login flag. The anti-CSRF token is
    delivered through that view too, if ADR-0032 decides so (questions 8 and 11).
- **BFF-7, limits and answers.** The CLIENT-31 bounds, `503` with `Retry-After`, `502`, and COMMONS-10
  problem bodies.

The CSRF logic follows API Sheriff's `bff/csrf/CsrfDefence` (the generic Origin / Sec-Fetch-Site half,
without its cookie-forwarding variant) and Audit Notary's `web/RequestGate` (the token). The identity
filter has no example in either consumer: neither uses `SecurityIdentity`.

## Deliverables

1. **BFF-4 CSRF check** for every unsafe method on every protected path, with no configuration knob that
   disables it, plus the logout proof or confirmation page.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-security
   - Persona: security-expert
2. **BFF-5 `SecurityIdentity` augmentation.** An `HttpAuthenticationMechanism` / `IdentityProvider` pair
   or an equivalent per ADR-0032. `recordAccess` is called only after the request is admitted, and never
   on a refused, challenged or redirected request. It answers `401` with the login address for an API
   call and a redirect for a navigation.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-cdi, pm-dev-java:java-security
   - Persona: implementer
3. **BFF-6 hook SPIs** as CDI beans with the documented defaults, `SessionViewContributor` included. A
   contributor's members are added to the session-info view. A contributor can never remove or override
   the allowlist cap, and an exception in a contributor answers `500` without leaking the session.
   Also the **BFF-7 answers**, with COMMONS-10 problem bodies.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-cdi, pm-dev-java:javadoc
   - Persona: implementer
4. **Tests**, covering:
   - CSRF: a foreign `Origin`, a missing `Origin` with and without `Sec-Fetch-Site`, a missing or wrong
     token when one is required, and each safe method passing;
   - identity: roles from groups, attributes, `401` versus redirect, and no `recordAccess` on a refused
     request;
   - hooks: the default return-address policy's edge cases (`//`, `\`, an absolute URL, the length
     bound) and an overridden policy.
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-quarkus, pm-dev-java-cui:cui-testing
   - Persona: module-tester
5. **The `doc/bff/` requirements and threat model** (placement per ADR-0032) for BFF-1 to BFF-9, including
   the CSRF threats moved from API Sheriff's BFF catalogue as ADR-0032 decides, plus test-mapping rows.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- `./mvnw verify` is green across the reactor.
- BFF-4 to BFF-7 are covered by tests that fail at the PLAN-08 merge commit.
- A refused request provably does not move the idle deadline (a test).
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- CSRF can never be disabled by configuration.
- Domain authorization (mapping groups to rights), canonical addresses, the session view and the
  first-login gate stay in the application (addendum §7). Provide hooks, not implementations.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: API Sheriff's CSRF check is `Origin` in the trusted set, else `Sec-Fetch-Site: same-origin`, with no disable knob; it has no anti-CSRF token — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/csrf/CsrfDefence.java` § lines 41-58 (leaf report).
- OBSERVED: Audit Notary's CSRF check is an `X-CSRF-Token` compared with `MessageDigest.isEqual`, plus `Origin`, with no `Sec-Fetch-Site` — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/web/RequestGate.java` § `csrfValid` (leaf report).
- OBSERVED: neither consumer sets a Quarkus `SecurityIdentity`; Audit Notary stores the session in the Vert.x `RoutingContext` — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/web/RequestGate.java` § `SESSION_KEY` (leaf report).
- OBSERVED: Audit Notary's generic return-address rule is: starts with `/`, no `//`, no `\`, at most 512 characters — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/security/ReturnAddresses.java` § validation method (leaf report).
- Verify-first clause: re-ground against the merged ADR-0032 (questions 8 and 10) and the landed PLAN-08.

## Expected Surface

- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-bff-quarkus/` — runtime module source and tests (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-bff-quarkus-deployment/` — deployment tests (verify-at-outline)
- HYPOTHESIS: `doc/bff/` — documentation set, placement per ADR-0032 (verify-at-outline)
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-08, PLAN-06.
- Overlaps with: PLAN-08 and PLAN-10 inside the extension; strictly sequential.
- Adjacent to: `token-sheriff-session/` (consumed, unchanged).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-09-bff-csrf-identity-and-hooks.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
