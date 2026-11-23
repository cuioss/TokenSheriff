# PLAN-08: `token-sheriff-bff-quarkus` — extension pair, endpoints, cookies, flow defaults, configuration (BFF-1, BFF-2, BFF-3, BFF-8, BFF-9)

epic: client-framework-aspects
workstream: WS-04

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted. If the ADR rejects Part C (for example
> in favour of the Quarkus OIDC extension in web-app mode), the orchestrator retires this spec and does
> not emit it. Otherwise re-ground it against the merged ADR at outline.

## Objective

Create the Quarkus extension pair `token-sheriff-bff-quarkus` (runtime) and
`token-sheriff-bff-quarkus-deployment`, on top of `token-sheriff-session`, with the browser-facing core.

- **BFF-1, endpoints.** Login start, callback, logout, back-channel logout and step-up, under a
  configurable root (default `/auth`). The callback answers with a same-origin page that navigates to
  the checked return address, not with a `302`, so a `SameSite=Strict` session cookie also works.
- **BFF-9, session-info endpoint (the logged-in sentinel).** The browser client polls one configurable
  path to learn whether it is logged in and who the user is.
  - It returns a curated view of the validated ID-token claims, capped by an operator allowlist and with
    a default view, plus the session metadata `expires_at`, `auth_time` and `acr`.
  - Claims keep their native JSON types. Raw tokens never appear in it.
  - Without a session it answers `401` problem JSON, never a redirect. A request for a claim outside the
    allowlist is answered `403`.
  - Every answer is `Cache-Control: no-store`.
  - A poll NEVER renews the idle deadline.

  The model is API Sheriff's `UserInfoEndpoint` (`oidc.user_info.path`, 650 lines including its
  allowlist filter and claim projection). The domain part of the view is added through the application
  hook in PLAN-09. The split follows ADR-0032 question 11.
- **BFF-2, cookies.** All are `__Host-`, `HttpOnly` and `Secure`. The pending-login cookie is
  `SameSite=Lax` and taken once. The session cookie's `SameSite` value is configurable, with the
  ADR-0032 default. Any response that sets or clears one of them is `Cache-Control: no-store`, applied
  centrally.
- **BFF-3, flow defaults.** `response_mode=query` through `ResponseMode.QUERY` (CLIENT-27), PKCE `S256`
  and PAR. DPoP and `private_key_jwt` per ADR-0032. The pending-login store is CLIENT-31 (PLAN-03).
- **BFF-8, configuration** under `sheriff.bff.*`. Every limit is checked at boot. A key whose feature is
  absent fails the start (Token Sheriff's boot-validation posture). The extension registers its own
  classes for the native image.

Audit Notary's `web/AuthResource`, `web/Cookies` and `security/LoginService` are the reference for the
shipped behaviour; API Sheriff has no Quarkus example to extract from.

## Deliverables

1. **Extension scaffold.**
   - Runtime and deployment modules under `token-sheriff-quarkus-parent/`, in dependency order before
     `token-sheriff-quarkus-integration-tests`.
   - Runtime: `quarkus-extension-maven-plugin` (`extension-descriptor`),
     `META-INF/quarkus-extension.yaml`, `quarkus-extension-processor`. Deployment: depends on the runtime
     and on the client deployment module.
   - A BOM entry for the runtime artifact; the new modules' `jacoco.xml` in the quarkus-parent
     `coverage` profile.
   - ⛔ `quarkus-bom` stays BEFORE `token-sheriff-bom` in `token-sheriff-quarkus-parent/pom.xml`.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:manage-maven-profiles
   - Persona: implementer
2. **BFF-8 configuration mapping and boot validation**, under `sheriff.bff.*`: every limit range-checked,
   unknown or orphan keys refused at start.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-cdi, pm-dev-java:java-security
   - Persona: implementer
3. **BFF-1 endpoints and BFF-2 cookies**:
   - login start (pending store, `503` with `Retry-After` when full, `502` when the provider is
     unusable);
   - callback (a same-origin navigation page), logout (POST, over SESSION-6), back-channel (over
     SESSION-6; `404` when the binding cannot destroy on the provider's behalf), step-up start and
     return (over SESSION-8);
   - central `no-store` on every response that sets or clears a cookie;
   - the BFF-9 session-info endpoint: allowlist and default view from configuration (BFF-8 checks at
     boot that the default view is a subset of the allowlist), `401`/`403`/`no-store` as above, and
     only `resolve`, never `recordAccess`.
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-cdi, pm-dev-java:java-security, pm-dev-java-cui:cui-logging
   - Persona: implementer
4. **BFF-3 flow defaults and native registration** in the deployment processor: build steps for the
   extension's own reflective types only. The engine's converters are already covered by the client
   jar (CLIENT-26).
   - Skills: pm-dev-java:java-quarkus
   - Persona: implementer
5. **Tests.** `QuarkusUnitTest`-style deployment tests for the configuration refusals and the build steps,
   and runtime unit tests for the cookie attributes, the callback page and the `503`/`502` answers. For
   the session-info endpoint the tests cover:
   - `401` without a session and a `403` disallowed claim;
   - the default view, the full view and the native claim types (an array stays an array, a number
     stays a number);
   - an empty allowlist disclosing nothing;
   - a poll leaving the idle deadline unchanged.
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-quarkus, pm-dev-java-cui:cui-testing
   - Persona: module-tester

**Done when:**

- `./mvnw verify` is green across the reactor, with both new modules' JaCoCo gates.
- BFF-1, BFF-2, BFF-3, BFF-8 and BFF-9 are covered by tests that fail at the PLAN-07 merge commit, where the
  extension is absent.
- `token-sheriff-bom` exports the runtime artifact.
- `quarkus-bom` still precedes `token-sheriff-bom` in `token-sheriff-quarkus-parent/pom.xml` (holds at
  `9c242518` and after).
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- No `quarkus-oidc` dependency. If ADR-0032 chose it, this spec is retired, not adapted.
- Problem bodies follow COMMONS-10 (RFC 9457). Never log a token, handle or cookie value.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: `token-sheriff-quarkus-parent` holds runtime/deployment pairs for validation and client, plus `token-sheriff-quarkus-integration-tests` and `e-2-e-playwright` — read at `token-sheriff-quarkus-parent/` § directory listing (orchestrator ls 2026-11-23).
- OBSERVED: the quarkus-parent's `dependencyManagement` imports `quarkus-bom` before `token-sheriff-bom`, and the order is load-bearing — read at `token-sheriff-quarkus-parent/pom.xml` § lines 62-75 (leaf report; project memory).
- OBSERVED: the client jar ships its own `reflect-config.json` for the engine's DSL-JSON converters — read at `token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/reflect-config.json` § 8 converter entries (orchestrator grep).
- OBSERVED: Audit Notary's callback is a meta-refresh page, its full-store answer is `503 TOO_MANY_LOGINS` with `Retry-After: 60`, and the provider failure is `502` — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/web/AuthResource.java` § lines 89-124 (leaf report).
- OBSERVED: neither consumer uses `quarkus-oidc` — read at `API-Sheriff/pom.xml` and `Audit-Notary/pom.xml` § dependencies (leaf reports).
- OBSERVED: API Sheriff's `UserInfoEndpoint` resolves the session without recording access, caps disclosure with an operator allowlist, answers `401` problem JSON (never a redirect) and `403` for a disallowed claim, and sets `no-store` on every answer — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/UserInfoEndpoint.java` § class Javadoc and lines 143, 191; config keys `oidc.user_info.path`, `allowed_claims` and `default_view` at `API-Sheriff/doc/configuration/authentication-and-oidc.adoc` § lines 590-603 (orchestrator read 2026-11-23).
- HYPOTHESIS: the native-JSON-type claim projection can rest on the validation module's published native JSON claims (ADR-0029) instead of a projection of its own — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/IdTokenContent.java` § the raw or native claims accessor, against `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/IdTokenClaimProjection.java` (verify-at-outline).
- HYPOTHESIS: the client deployment module's build steps are generated by `quarkus-extension-processor`, so no handwritten `quarkus-build-steps.list` is needed — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/pom.xml` § `annotationProcessorPaths` (verify-at-outline).
- Verify-first clause: re-ground against the merged ADR-0032 (questions 1, 4, 9 and 10) and the landed PLAN-03 and PLAN-07.

## Expected Surface

- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-bff-quarkus/` — new runtime module (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-bff-quarkus-deployment/` — new deployment module (verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/pom.xml`
- OBSERVED: `bom/pom.xml`

## Dependencies and Sequencing

- Depends on: PLAN-07, PLAN-03, PLAN-01.
- Overlaps with: PLAN-05 on `bom/pom.xml` (one file, and already sequenced); PLAN-09 and PLAN-10 inside
  the new modules (strictly sequential).
- Adjacent to: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus*` (depended on, unchanged).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-08-bff-extension-core.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
