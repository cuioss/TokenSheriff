# PLAN-11: RFC 8707 resource indicators — the `resource` parameter on every authorization and token request

epic: client-framework-aspects
workstream: WS-02

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Teach the engine RFC 8707 resource indicators. A client can then name the protected resource a token is
for, and receive an access token whose audience is that resource only. The `resource` parameter is sent
on:

- the authorization request, both front-channel and pushed (PAR);
- the authorization-code token request;
- the refresh request;
- the client-credentials request.

It can be configured once per client and overridden per call, following the per-call pattern
CLIENT-27/28 already uses for scopes. The refresh override matters most: one refresh token can then yield
an audience-restricted access token for each of several services (RFC 8707 §2.2) without another login.

Two consumers need this:

- The MCP authorization specification (revision 2026-07-28) requires `resource` on every authorization
  and token request.
- Audit Notary's local MCP gateway (`Audit-Notary/doc/concepts/local-mcp-gateway.adoc` §4.3, "Resource
  and issuer") names it.

The BFF and API Sheriff benefit too: an audience-bound token is useless at any other resource server.

The seam is independent of public-client support. The rest of the gateway's needs (a `none` client
authentication, a loopback redirect, the device grant) are deliberately NOT in this plan (operator
decision, 2026-11-23). They wait for the gateway spike.

## Deliverables

1. **Configuration and per-call options.**
   - `ClientConfiguration` gains an optional list of default resource indicators.
   - `AuthorizationRequestOptions` gains a per-call list that replaces the configured one for that call.
   - Each value is validated at construction, following RFC 8707 §2:
     - it is an absolute URI;
     - it carries no fragment;
     - its scheme passes the same https-or-opted-in-http rule `EndpointSchemePolicy` applies;
     - the list is bounded in size;
     - a duplicate is refused.

     A refused value is never echoed in the message, since it may come from untrusted input.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:java-lombok, pm-dev-java:javadoc
   - Persona: implementer
2. **Wire format on every request.** One `resource` form parameter per value, in configured order, on:
   - the authorization URL (`AuthorizationRequestBuilder`);
   - the PAR request (`ParClient`);
   - the code exchange (`TokenEndpointClient`);
   - the refresh request (`RefreshFlow`), with a new `refresh(metadata, refreshToken, scopes, resources)`
     overload beside the existing scope override;
   - the client-credentials request (`ClientCredentialsFlow`).

   With no resource configured and none passed, every request is byte-identical to today.

   An `invalid_target` error (RFC 8707 §2) is mapped to a typed refusal through the existing token-error
   mapping. Unlike a transient failure, it must never be retried.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-security, pm-dev-java-cui:cui-http
   - Persona: implementer
3. **Quarkus configuration.** Map a per-client `resource` list in `ClientRuntimeConfig` and
   `ClientConfigMapper`, validated at boot. An invalid value fails the start (the boot-validation
   posture).
   - Skills: pm-dev-java:java-quarkus, pm-dev-java:java-cdi
   - Persona: implementer
4. **Tests.** Cover:
   - the parameter on each of the five request kinds, as single and multiple values;
   - the per-call override replacing, not merging with, the configured list;
   - the refresh override yielding a request for a different resource;
   - the byte-identical default, proven against the existing request tests;
   - validation refusals: a relative URI, a fragment, an `http` scheme without the opt-in, a duplicate,
     too many values, and the value never echoed in the message;
   - `invalid_target` mapped and not retried;
   - an injection attempt in the style of the existing `AuthorizationRequestInjectionAttackDatabaseTest`;
   - a Quarkus boot refusal of an invalid configured value.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java-cui:cui-http-testing
   - Persona: module-tester
5. **Requirement and specification.**
   - A new CLIENT requirement for resource indicators, with the next free id after the ones PLAN-02 to
     PLAN-04 allocate (CLIENT-36 if they used 31 to 35); check the id is free.
   - The parameter in `doc/client/specification/retrieval-flow.adoc` and in the refresh section of
     `token-handling.adoc`.
   - The configuration key in the Quarkus client docs.
   - Test-mapping rows.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- The `resource` parameter is sent on all five request kinds, as covered by tests that fail at
  `9c242518` (the parameter is absent there).
- Without a configured or passed resource, every request holds byte-identical at `9c242518` and after:
  the existing request and flow tests stay green unchanged.
- `./mvnw -pl token-sheriff-quarkus-parent/token-sheriff-client-quarkus -am verify` is green.
- The new CLIENT requirement is documented with a test mapping.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Additive and opt-in. No existing public signature changes meaning; new overloads stand beside the
  existing ones.
- The engine does NOT check a returned access token's audience against the requested resource. The
  resource server owns that check. If an access token is a JWT the engine already validates, the outline
  may decide to add an optional client-side check, but must name that choice.
- Out of scope: per-resource caching of access tokens in `TokenLifecycleManager`/`TokenStore`. Its
  caller drives per-resource refresh through the new overload. If a consumer needs a cache, it is a
  follow-up plan.
- Out of scope: public client (`none`), loopback redirect, device grant, Client ID Metadata Document
  (the local-MCP-gateway Watch in `epic.md`).
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: the engine sends no `resource` parameter anywhere; a fixed-string search for `"resource"` and `RFC 8707` over `token-sheriff-client/src/main/java` and for `8707` over `doc/client` returned nothing — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java` § request parameters (orchestrator search 2026-11-23).
- OBSERVED: per-call authorization options exist for scopes, response mode and prompt — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestOptions.java` § fields `scopes`, `responseMode`, `prompt`.
- OBSERVED: `RefreshFlow` already has a per-call scope override `refresh(ProviderMetadata, String, List<String>)` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java` § line 241.
- OBSERVED: front-channel endpoints pass an https-only scheme policy that never echoes the untrusted value — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/EndpointSchemePolicy.java` § class Javadoc.
- OBSERVED: Audit Notary's gateway concept requires `resource` on every authorization and token request, citing MCP revision 2026-07-28 — read at `Audit-Notary/doc/concepts/local-mcp-gateway.adoc` § 4.3 "Resource and issuer" (line 179).
- HYPOTHESIS: the token-error mapping can carry `invalid_target` as one more typed refusal, without a new exception hierarchy — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java` § error mapping (see `TokenEndpointClientErrorMappingTest`) (verify-at-outline).
- HYPOTHESIS: the Quarkus client configuration maps per-client lists the same way it maps scopes — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java` § scopes mapping (verify-at-outline).

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestOptions.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ClientCredentialsFlow.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientRuntimeConfig.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`
- OBSERVED: `doc/client/specification/token-handling.adoc`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first). Otherwise none. No ADR-0032 question touches it, so it may run before PLAN-01 lands.
- Overlaps with:
  - PLAN-02: the flow tests directory, the requirements and the test mapping.
  - PLAN-03: the requirements, `retrieval-flow.adoc` and the test mapping.
  - PLAN-04: the requirements, `token-handling.adoc` and the test mapping.

  Two or more shared files mean the operator is asked at `next`; the expected answer is to run it in
  sequence with those plans.
- Adjacent to: `lifecycle/TokenLifecycleManager.java` (per-resource caching is out of scope);
  `dpop/` (a DPoP proof binds a token to a key, not a resource, so it is unchanged).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-11-resource-indicators.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
