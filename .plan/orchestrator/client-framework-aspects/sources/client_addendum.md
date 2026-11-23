# Client Addendum: a Session Layer Above the Engine (Proposal)

| | |
|---|---|
| Status | Proposal, not accepted; nothing here is a requirement yet |
| Date | 2026-11-23 |
| Affects | `token-sheriff-client`, two new modules, the consumers API Sheriff and Audit Notary |
| Decision needed | Accept the new modules (an ADR), then turn the outlines below into `CLIENT-*` and new requirement sets in AsciiDoc |

## 1. Summary

Two consumers build the browser-facing layer of a backend-for-frontend (BFF) on top of the
confidential-client engine:

- API Sheriff: the `bff` package, 66 files, about 16,400 lines;
- Audit Notary: about 1,700 lines.

They have built the same parts twice: the session store, the store of pending logins, logout followed
by revocation, the return-address check, the CSRF check and the `response_mode=query` builder. They
differ where they should not. Audit Notary lacks back-channel logout, refresh, session bounds and
fairness between sources of pending logins, all of which API Sheriff has hardened over several
decisions.

The proposal has three parts:

- **Part A, engine seams.** Five additions to `token-sheriff-client` in the existing "published
  seam" style of CLIENT-27 to CLIENT-30. Neither consumer needs to re-implement a protocol rule.
- **Part B, `token-sheriff-session`.** A new framework-neutral module with the server-side session
  lifecycle. It is extracted from API Sheriff's hardened code, not designed afresh.
- **Part C, `token-sheriff-bff-quarkus`.** A new Quarkus extension with the endpoints, cookies, CSRF
  and the `SecurityIdentity` on top of Part B. Audit Notary is the first consumer.

CLIENT-19 stays as it is: the *engine* implements neither BFF binding shape. Parts B and C are layers
above the engine, not part of it.

## 2. Motivation

### 2.1 What both consumers built themselves

| Part | API Sheriff | Audit Notary |
|---|---|---|
| Server-side session store, idle and absolute deadline | `SessionBinding` server mode (ADR-0018) | `SessionStore`, `SessionJanitor` |
| Pending logins (`state`, `nonce`, PKCE verifier by opaque reference) | Pending-authorization store: 10,000 in total, 256 per source, 5 min (PLAN-SEC-03, threat BFF-20) | `LoginTransactions`: 10,000 in total, no share per source, 10 min |
| `response_mode=query` | `QueryResponseModeAuthorizationRequestBuilder` (ADR-0018) | `QueryResponseModeRequestBuilder` |
| Logout: destroy, revoke, end-session redirect | ADR-0080 (destroy first, then revoke the refresh token) | `LoginService.logout` (destroy first, then revoke refresh and access token) |
| Disposition of a refused refresh | Per-kind table, one refresh per session at a time, back-off (ADR-0046) | none: Audit Notary never refreshes |
| Back-channel logout receiver, `jti` once | yes, per process | none |
| Session bounds: total, per subject, a new login ends the browser's session | yes | none |
| Return address: same-origin, fallback | `default_return_url` | `ReturnAddresses` (canonical addresses) |
| CSRF | `Origin` / `Sec-Fetch-Site`, `SameSite=Lax` | Anti-CSRF token and `Origin`, `SameSite=Strict` |
| Long-lived connections end with the session | WebSockets closed with `1008` | the event stream is not bound to a session |
| What counts as activity | only requests let through | every `/api/v1` request, refused ones included |

### 2.2 Consequences of the duplication

- **The second consumer starts behind.** Each control API Sheriff settled in an ADR has to be found
  again, decided again and tested again in Audit Notary. The gap list in 2.1 is exactly that backlog.
- **The two differ without a reason.** Examples are the lifetime of a pending login (5 vs 10 minutes)
  and revoking the access token at logout (no vs yes).
- **The threat surface is described twice.** API Sheriff's threat model carries the BFF threats.
  Audit Notary has none for this layer.

## 3. What Token Sheriff already provides

The proposal builds on these. It does not replace them.

| Already there | Where |
|---|---|
| Token store per session, refresh with rotation, `revokeAndClear` | `lifecycle/TokenLifecycleManager`, `TokenStore`, `InMemoryTokenStore` (CLIENT-17) |
| Classification of a refused refresh: `PRE_REDEMPTION`, `SCOPE_REFUSED`, `CREDENTIAL_REJECTED`, `REDEEMED` | `flow/RefreshFailureClassification`, `RefreshFlow.classify` |
| Check of a refresh result, optionally bound to a subject | `flow/RefreshResultVerifier` (CLIENT-28) |
| Check of a step-up result (`acr`, `auth_time` within `max_age`) | `flow/StepUpResultVerifier` (CLIENT-12); it binds no subject |
| Revocation (RFC 7009) | `lifecycle/RevocationClient` |
| Per-call `response_mode`, `prompt`, scopes | `flow/AuthorizationRequestOptions`, `ResponseMode` (CLIENT-27) |
| Discovery cache | `CachingDiscoveryResolver` (CLIENT-29) |
| Logout-token validation | VALIDATION-13.4 |

## 4. Part A: engine seams (`token-sheriff-client`)

These are candidate requirements CLIENT-31 to CLIENT-35. None of them involves a cookie or a session
store.

1. **CLIENT-31: Bounded pending-authorization store.**
   - It holds the `FlowContext` of an unfinished login under a random opaque reference, and only
     once: taking it removes it.
   - It has a total bound and a bound per source key.
   - When a source reaches its bound, that source's oldest entry is dropped.
   - When the total bound is reached, a start is refused and the logins already started are kept.
   - The source key is an input: the application derives it, for example from its trusted proxies.
   - The lifetime and the bounds are configurable within limits; the defaults follow API Sheriff.
   - A start whose authorization request fails, such as a refused PAR, frees its place at once.
2. **CLIENT-32: Refresh disposition.** This builds on `RefreshFailureClassification`, which is
   already published. A published table maps each kind, plus the two cases that arise when the
   session is written, to three outcomes:
   - the fate of the session: kept or ended;
   - the token to revoke: none, the presented one, or the successor;
   - the log record.

   ADR-0046 of API Sheriff holds the table today. Each consumer then applies the same table.
3. **CLIENT-33: Logout step.** One operation takes the token record the caller has *already removed*
   from its session, so the order (local destruction first) follows from the signature. It does
   three things:
   - it revokes best effort and never throws;
   - it records the first failure at `WARN` and every later one at `DEBUG`;
   - it returns the end-session address, or none.

   Open: whether the access token is revoked as well (section 9).
4. **CLIENT-34: Step-up result bound to the session's subject.** This extends `StepUpResultVerifier`.
   When the caller names the issuer and `sub` of its session, the step-up result is refused for any
   other issuer or `sub`. The comparison is constant-time. It mirrors CLIENT-28's subject binding.
5. **CLIENT-35: Back-channel logout handling.**
   - The logout token is validated (VALIDATION-13.4).
   - A `jti` memory accepts each token once. It is bounded and keeps a `jti` only for the token's
     window.
   - The result is the session selector: `sid` alone, or `sub` for every session of the user.
   - The HTTP endpoint and the lookup of the sessions stay with the caller.

## 5. Part B: `token-sheriff-session` (new, pure Java)

This is the server-side session lifecycle, without HTTP. Its outline, as SESSION-1 to SESSION-9:

1. **SESSION-1: Session record and store.**
   - The cookie holds a random handle, never the internal session identity. The handle can be
     re-issued while the identity stays the same.
   - The session is indexed by `sid` and by `sub`.
   - The store is bounded in total. Each subject is bounded too; on overflow its oldest session ends.
   - The record holds the identity values a consumer needs (`iss`, `sub`, `acr`, `amr`, `auth_time`,
     groups as a list from the JSON payload) and the tokens through `TokenLifecycleManager`.
2. **SESSION-2: Deadlines.** A session ends at its absolute lifetime, or earlier after the idle time.
   - Only an explicit `recordAccess` moves the idle deadline.
   - Resolving a session renews nothing. A refused, challenged or redirected request is not an access.
   - Expired sessions leave the store when next looked up, and otherwise through a periodic sweep.
3. **SESSION-3: Create versus update.**
   - `bind` creates a session and is refused at the bound.
   - `persist` replaces an existing session and never creates one. It reports a session that is gone,
     so a logout always wins over a refresh or a step-up that is still in flight.
4. **SESSION-4: A login ends the browser's session.** A completed login ends the session whose handle
   the browser presented before it creates the new one.
5. **SESSION-5: Refresh.**
   - A refresh runs near expiry, as one refresh per session at a time.
   - Its disposition follows CLIENT-32.
   - The groups and identity values are taken from the refreshed ID token when there is one, so a
     change of group at the provider reaches the session.
6. **SESSION-6: Logout and termination.**
   - RP-initiated logout destroys the session first, then runs CLIENT-33.
   - A back-channel logout (CLIENT-35) ends the selected sessions and revokes nothing.
   - The store is per process: a consumer with more than one instance needs a shared store or
     accepts that limit, which API Sheriff states at boot with `ApiSheriff-150`.
7. **SESSION-7: Session-ended notification.** Every end of a session publishes one framework-neutral
   event with the session identity and the cause: logout, back-channel, expiry, bound, replaced by a
   login, or refresh. It lets a consumer close long-lived connections (WebSockets, server-sent events)
   bound to the session.
8. **SESSION-8: Step-up into the same session.**
   - A verified step-up (CLIENT-34) updates the session with `persist`; the new `acr`, `amr` and
     `auth_time` are kept.
   - The step-up transaction carries an opaque *pending action* that the consumer supplies, such as
     "release document X at revision Y". On return the consumer gets that action back together with
     the verified values, and checks it again itself.
9. **SESSION-9: Binding seam.** Server mode is the implementation this module ships. A `SessionBinding`
   seam keeps API Sheriff's sealed-cookie mode possible without this module carrying it. Whether the
   cookie mode moves here is open (section 9).

## 6. Part C: `token-sheriff-bff-quarkus` (new extension)

The browser-facing layer on Part B. Its outline, as BFF-1 to BFF-8:

1. **BFF-1: Endpoints.** Login start, callback, logout, back-channel logout and step-up, under a
   configurable root such as `/auth`. The callback answers a same-origin page that navigates to the
   checked return address, not a `302`, so a `SameSite=Strict` session cookie also works.
2. **BFF-2: Cookies.** All cookies are `__Host-`, `HttpOnly` and `Secure`.
   - The pending-login cookie is `SameSite=Lax` and is taken once.
   - The session cookie is `Lax` or `Strict`, configurable.
   - A response that sets or clears one of these cookies is `Cache-Control: no-store`.
3. **BFF-3: Flow defaults.**
   - `response_mode=query` with the four conditions of API Sheriff's ADR-0018.
   - PKCE `S256` and PAR.
   - Whether DPoP and `private_key_jwt` are defaults or optional is decided in the ADR. A consumer
     that relays no token gains little from DPoP.
4. **BFF-4: CSRF.** It applies to unsafe methods and cannot be turned off.
   - The check is `Origin` against the trusted origins, or `Sec-Fetch-Site: same-origin`.
   - An anti-CSRF token can be added: its value comes with the session view, and the request carries
     it in a header.
   - Logout either requires that proof or asks for confirmation (API Sheriff's confirmation page).
5. **BFF-5: Security identity.**
   - A filter resolves the session and sets the Quarkus `SecurityIdentity`: the principal, the groups
     as roles, and the identity values as attributes.
   - It calls `recordAccess` only for requests it lets through (SESSION-2).
   - A request without a session gets `401` with the login address in the body, or a redirect for a
     navigation.
6. **BFF-6: Hooks the application supplies.**
   - `ReturnAddressPolicy`: which same-origin addresses are allowed, and the fallback. The default is
     same-origin paths.
   - `SessionAugmentor`: values added at login, such as Audit Notary's pseudonym or its first-login
     gate.
   - `StepUpRequirement`: an API that returns the step-up address for a pending action (SESSION-8).
7. **BFF-7: Limits and answers.**
   - The pending-login bounds of CLIENT-31.
   - `503` with `Retry-After` when the store is full.
   - `502` when the provider cannot be used.
   - Problem bodies follow COMMONS-10.
8. **BFF-8: Configuration.**
   - The keys sit under `sheriff.bff.*`.
   - Every limit is checked at boot. A key without its feature fails the start (ADR-0026).
   - The extension registers its own classes for the native image (section 8).

## 7. What stays in the application

| Stays | Why |
|---|---|
| Rights from groups (Audit Notary's mapping to repositories and actions) | Domain authorisation |
| Which addresses are canonical | Domain routing, behind the `ReturnAddressPolicy` hook |
| The session view the web app reads (`/api/v1/session`) | Domain content: which claims are shown |
| First-login gate, directory, commit trailers | Domain, behind `SessionAugmentor` |
| Re-checking a pending action after step-up | Domain: for example "still the reviewed revision" |
| Closing its own streams on session end | Listener on SESSION-7 |
| Proxying, token relay, scope widening per route, cookie mode, reserved-path pipeline | API Sheriff's gateway concerns |

## 8. Small fixes, independent of the decision

1. **Native-image registration.** Audit Notary's `TokenClientNativeRegistration` lists the engine's
   generated DSL-JSON converters by hand (`_ProviderMetadata_DslJsonConverter`, `_ParResponse_…`,
   `_TokenResponse_…` and others). `token-sheriff-client-quarkus-deployment` registers none of them.
   The registration belongs in the deployment module. As it stands, every consumer breaks when those
   internal names change.
2. **Consumers should drop what the engine already has.**
   - Audit Notary's `QueryResponseModeRequestBuilder` duplicates `ResponseMode.QUERY` (CLIENT-27).
   - Audit Notary caches the provider metadata by hand and never refreshes it, which
     `CachingDiscoveryResolver` (CLIENT-29) covers.

   Check that the pinned release contains both before removing the copies.
3. **Groups as a list.** Audit Notary reads the group claim from the raw JSON payload, so that a
   group name containing a comma stays one group. The engine could publish "claim as a list of
   strings, as the provider wrote it".

## 9. Open questions

1. **Quarkus' own OIDC extension.** Part C invites the question why the Quarkus OIDC extension in
   web-app mode is not used. No document of Token Sheriff, API Sheriff or Audit Notary weighs this
   today. The ADR needs a sourced comparison before Part C is accepted.
2. **Access token at logout.** API Sheriff revokes only the refresh token (ADR-0080; revoking the
   access token too is a rejected alternative). Audit Notary revokes both. CLIENT-33 should fix one
   rule.
3. **Cookie mode.** Should API Sheriff's sealed-cookie binding move into Part B, or stay in API
   Sheriff behind the SESSION-9 seam?
4. **More than one instance.** Part B is per process, like both consumers today. A shared store adds
   an external dependency, which API Sheriff's feature analysis rules out.
5. **Defaults.** The pending-login lifetime is 5 minutes in API Sheriff and 10 in Audit Notary. The
   session bounds, and whether the session cookie defaults to `Lax` or `Strict`, are also open.
6. **Threat model.** API Sheriff's BFF threats (`BFF-*`) would move to the new modules' threat model.
   API Sheriff would then keep only the gateway-specific ones.
7. **Naming and identifiers.** Module names, and the requirement prefixes (`SESSION-*`, `BFF-*`, or
   continuing `CLIENT-*`).

## 10. Proposed order

1. Section 8, and Part A (CLIENT-31 to CLIENT-35) with tests. Both consumers can adopt these
   independently.
2. An ADR in Token Sheriff accepting Parts B and C, with the comparison of question 9.1.
3. Part B, extracted from API Sheriff's `bff` package (server mode, ADR-0018, ADR-0046, ADR-0080, the
   pending-authorization store). API Sheriff moves onto it first and keeps its tests green.
4. Part C, with Audit Notary as its first consumer. Its own login and session code (about 1,300 of its
   1,700 lines) is removed, and its use cases UC-ACC-1 to UC-ACC-3 rest on the extension.

## 11. Sources

Internal, as of 2026-11-23:

- Token Sheriff: `doc/client/requirements.adoc` (CLIENT-12, -13, -17, -19, -27 to -30),
  `doc/client/architecture.adoc` (BFF binding shapes, boundaries), `doc/client/threat-model.adoc`
  (out of scope: BFF application concerns), `doc/validation/requirements.adoc` (VALIDATION-13.4);
  code in `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/{flow,lifecycle}`.
- API Sheriff: `doc/architecture/authentication-and-bff.adoc`, `doc/variants/02-bff-session.adoc`,
  `doc/user/bff-session.adoc`; ADR-0018, ADR-0046, ADR-0058, ADR-0080;
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff`.
- Audit Notary: `doc/adr/0001-…`, `doc/specification/authentication.adoc` (AUN-AUTH-1 to 4),
  `doc/specification/web-app.adoc` (AUN-WEB-8, AUN-WEB-9), `doc/use-cases/01-access.adoc`;
  `audit-notary/src/main/java/de/cuioss/notary/audit/{security,web}`.

External standards: the documents above cite and check them. This proposal adds no claim of its own
about them.

- RFC 7009 (revocation), RFC 9470 (step-up), RFC 9126 (PAR), RFC 9449 (DPoP), RFC 9700 (security
  best current practice).
- OpenID Connect RP-Initiated Logout 1.0, OpenID Connect Back-Channel Logout 1.0.
- RFC 10017 (OAuth 2.0 for Browser-Based Applications, BCP 212), as cited in Audit Notary's ADR-0001.
