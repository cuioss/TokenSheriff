# Code Review — `token-sheriff-client` (PR #550 + #553)

**Scope reviewed:** the new `token-sheriff-client` Maven module introduced by
[#550 *feat(client): implement OIDC/OAuth confidential client engine*](https://github.com/cuioss/TokenSheriff/pull/550)
(merge `c2b0dfff`) and its follow-up
[#553 *chore(client): clean up all client Sonar findings*](https://github.com/cuioss/TokenSheriff/pull/553)
(merge `59265ade`). ~5,300 LOC production, ~5,700 LOC test, ~40 main files.

**Lenses:** correctness, completeness, consistency; full semantic review against the project's
own requirements and specifications (`doc/client/**`) and the RFCs those documents make
normative (RFC 6749/7636/7523/8705/9126/9207/9449/9470, OIDC Core & RP-Initiated Logout,
RFC 7009). Security-first bar, as requested.

**Method:** five independent deep reviews (spec conformance, auth/flow security, token-lifecycle
security, cross-cutting consistency, test completeness), each reading the real code, then
cross-verification of every high/critical finding against the source. Findings below were
confirmed against the actual code, not inferred from names.

---

## Executive summary

The **per-component cryptographic mechanics are genuinely strong and often exceed the letter of
the spec**: PKCE S256 (32-byte `SecureRandom`, correct RFC 7636 length/charset, `plain`
unrepresentable), 256-bit `state`/`nonce`, constant-time comparisons everywhere they matter,
exact-match redirect validators, an RFC 7638-correct DPoP JWK thumbprint with a fail-closed
`jti`-reuse LRU, RFC 6749 §2.3.1-correct Basic-auth form-encoding, a `private_key_jwt` assertion
with fresh `jti` and a strict no-`none`/no-HMAC algorithm allow-list, and an atomic
`computeIfPresent`/`remove` token store that closes the refresh-after-logout race. The test suite
is disciplined — AAA structure, no sleeps, seeded generators, latch-gated concurrency, log
assertions matched to the real message catalog.

**The dominant defect class is *unwired security components*.** Several defenses that the
requirements and threat model state as unqualified `MUST`s exist as well-written classes that
**no production code path ever invokes**:

| Advertised defense | Class exists? | Wired into a flow? | Requirement |
|---|---|---|---|
| RFC 9207 `iss` mix-up defense | `IssValidator` ✓ | **No** — `AuthorizationCodeFlow.exchange()` never calls it | CLIENT-8 |
| PAR (push authz params) | `ParClient` ✓ | **No** — zero production callers; no `request_uri` redirect builder | CLIENT-10 |
| DPoP / sender-constraining | `DpopProofGenerator`, `SenderConstraint` ✓ | **No** — no flow attaches a proof | CLIENT-11 / 18 |
| Refresh-reuse → family revocation | `RefreshTokenFamily` ✓ | **No** — orphan; shipped refresh path has no reuse detection | CLIENT-17 |
| `response_mode=form_post` | — | **Not implemented at all** | CLIENT-1 / 14 |
| Step-up `acr`/`auth_time` verification | — | **Not implemented at all** | CLIENT-12 |
| mTLS client auth | `MtlsClientAuth` ✓ | **Non-functional** — no `SSLContext` ever plumbed, yet ranked *strongest* | CLIENT-4 / 11 |

Because the threat-model and test-strategy documents describe these as covered ("the `iss`
response parameter is cross-checked before exchange", "the front-channel redirect carries only
the `request_uri`"), **the documentation currently overstates what the engine enforces**, and
several tests certify controls the engine does not run. Some classes carry javadoc markers like
"deliverable 6" / "increment 3", indicating this was intended as staged work — but the specs
state the behavior as present-tense MUSTs and no document under `doc/` records the staging.

**This is not a set of isolated missing tests — it is a gap in the test strategy itself.** Each
orphaned defense is "covered" by a unit test and, in several cases, a real-Keycloak integration
spec (`MixUpSpecIT`, `ParSpecIT`, `DpopBoundSpecIT`, `StepUpSpecIT`) — but those tests exercise the
security component *in isolation*, or verify the *authorization server's* capability, and **never
assert that the engine's own flow orchestrator, called as an application would call it, actually
runs the check**. The suite passes while the defense is unreachable. Because this blind spot spans
at least five requirements and the Quarkus edge as well, it is written up as its own root-cause
finding, **[S0](#s0--systemic-the-verification-strategy-tests-components-not-the-wired-flow-root-cause)**,
which the individual flow findings reference.

There is also a **material architecture-boundary contradiction**: `architecture.adoc` states "the
engine issues *no* HTTP of its own … adds no SSRF/TLS logic," but five classes issue their own
`java.net.http`/cui-http calls with only TLS-scheme enforcement and no commons SSRF control. This
was decided in an archived plan ("design fork 1") and never reflected back into `doc/client`.

### Severity tally

- **Systemic (root cause): S0** — the verification strategy tests components in isolation, not the wired flow; it must gain a mandatory wired-flow / negative-path tier before any of CLIENT-8/10/11/12/17 can be marked covered.
- **Critical: 2** — `form_post` missing (code in URL); `iss` mix-up defense unwired.
- **High: 8** — step-up verification missing; DPoP unwired + no `ath`/nonce; mTLS non-functional & mis-ranked; refresh-reuse detection orphaned; no single-flight refresh; `client_credentials` capped at shared secret; token `toString()` leaks; architecture "no HTTP" boundary contradicted.
- **Medium: ~14** — token_type unvalidated, no-`state`-return-verification on logout, signed userinfo unsupported, `at_hash` unvalidatable, JDK exceptions instead of typed hierarchy, refresh ID-token/sub consistency skipped, response buffered before size cap, inconsistent sanitization/timeouts/exception-types, and their test counterparts.
- **Low/info: many** — doc rot, traceability ID errors, `@NullMarked` gap, header-append vs set, config validation gaps.

**Recommendation:** the module is a strong foundation but is **not yet spec-conformant for a
security-first release**. Either (a) wire and test the four orphaned defenses + implement
`form_post` and step-up verification before claiming CLIENT-8/10/11/12/17, or (b) if staging is
intended, downgrade the affected requirements to explicitly-deferred status in `doc/client` and
correct the threat-model/test-strategy claims so they do not assert coverage that does not exist.

---

## S0 — Systemic: the verification strategy tests components, not the wired flow (root cause)

This is the single most important finding, and it is **not "a test is missing" — it is a hole in
the test strategy itself.** Every orphaned defense in this review (C2 mix-up, H3 DPoP, H5
refresh-reuse, plus PAR and step-up) is "covered" on paper — unit tests, and even real-Keycloak
integration specs, carry its name — yet the engine's executable path never invokes it. The tests
pass while the defense is unreachable, because **the strategy verifies each security component in
isolation (or verifies the authorization server's capability) but never asserts that the engine's
own flow orchestrator, as an application would call it, actually runs the check.** That is a
category of blind spot, and it currently spans at least five requirements.

Concretely, the verification is structured so that a defense can be fully deleted from the
executable path without a single test going red:

| Requirement | What the test suite exercises | What it never asserts (the wiring) |
|---|---|---|
| **CLIENT-8** mix-up | `MixUpSpecIT` does `new IssValidator()` and calls `validate()` directly against real Keycloak issuers; unit `MixUpDefenseTest` likewise | That `AuthorizationCodeFlow.exchange()` invokes `IssValidator` and refuses to redeem the code on `iss` mismatch (dispatcher call-count == 0) |
| **CLIENT-10** PAR | `ParSpecIT` **hand-rolls a raw `HttpClient` POST** to Keycloak's PAR endpoint — it does not import `ParClient` | That the engine builds a `request_uri`-only front-channel redirect; `ParClient` has zero production/edge callers |
| **CLIENT-11** DPoP | `DpopBoundSpecIT` imports only `DpopProofGenerator` (standalone proof); unit `SenderConstraintTest` checks a bare `HashMap` | That any flow attaches a `DPoP` header to a real token/refresh/userinfo request |
| **CLIENT-12** step-up | `StepUpSpecIT` drives `StepUpHandler.initiate()` (request side only) | That the returned ID token's `acr`/`auth_time` are verified — no verifier exists to test |
| **CLIENT-17** refresh-reuse | `RefreshConcurrencyTest` proves a correct 16-thread exactly-one-winner race **on the `RefreshTokenFamily` object** | That `RefreshFlow`/`TokenLifecycleManager` route through the family and drive RFC 7009 revocation on reuse |

Three independent facts make this systemic rather than incidental:

1. **The production orchestrators are not driven end-to-end.** Grepping the integration suite,
   only `StepUpSpecIT` references any flow orchestrator (and only `StepUpHandler`). No IT imports
   `AuthorizationCodeFlow` or `RefreshFlow` at all — even `FullFlowE2EIT`, the "full flow" test,
   assembles logout/userinfo pieces by hand rather than driving the code-exchange flow. So the
   one place the wiring would be observable is never invoked by a test.
2. **The edge wiring is not verified against the flow either.** The Quarkus CDI producer
   (`TokenSheriffClientProducer`) produces the transport/lifecycle beans but **no**
   `AuthorizationCodeFlow`, `RefreshFlow`, `IssValidator`, `ParClient`, `SenderConstraint`, or
   `RefreshTokenFamily` — and no test asserts the produced bean graph can actually perform a
   guarded exchange. A "does the wired engine reject a mix-up?" test would have failed to even
   compile a subject.
3. **The test-strategy document already claims these as covered** (its traceability matrix asserts
   CLIENT-8 "rejected before exchange", CLIENT-10 "front-channel redirect carries only the
   `request_uri`", CLIENT-11 "DPoP proof present on the token request", CLIENT-17 rotation reuse →
   family revocation). Those rows are backed by isolated-component or server-capability tests, not
   by flow-level assertions — so the matrix reports green for behavior the engine does not perform.

**Additional strategy-level weaknesses reinforcing the same theme** (detailed in the Low section):
"tampered token" tests re-sign the token so **signature integrity is never actually exercised**
(rejection comes only from unknown-issuer lookup); the callback attack-database feeds
`CallbackParameters.of(Map)`/the constructor and **never the real `parse(rawQuery)`** decode path,
so encoding-evasion payloads never reach the code under test; every HTTP unit test uses
`allowInsecureHttp(true)` so the **TLS path is never exercised** despite the strategy mandating
HTTPS mocks; and the security-critical `internal` package (`LogSanitizer`, `FormEncoder`,
`JsonEscaper`) has **zero tests**. Each is the same failure mode: the test targets a stand-in for
the real trust boundary rather than the boundary itself.

**Required strategy change (not just added cases):** the test strategy must add a mandatory
**"wired-flow / negative-path" tier** whose contract is *drive the engine (and the produced
Quarkus bean graph) exactly as an application would, then assert the attack is refused before any
side effect* — e.g. on `iss` mismatch the token endpoint is never called; on PAR the front-channel
URL contains only `request_uri`; on a DPoP-requested flow a `DPoP` header reaches the recorded
request; on rotated-token reuse the family path fires RFC 7009 revocation. A defense that has no
such flow-level negative test must not be marked covered in the traceability matrix. Until that
tier exists, isolated-component green does not imply the requirement is met — and the matrix
should say so.

---

## Critical

### C1 — `response_mode=form_post` is never sent; the code/state/iss ride in the URL
*CLIENT-1, CLIENT-14, T-URL-LEAK · `flow/AuthorizationRequestBuilder.java:86-99`*

The authorization request never emits `response_mode=form_post` (the parameter appears nowhere in
the module). The AS therefore defaults to `response_mode=query`, so the authorization `code`,
`state`, and `iss` are returned in the redirect URL query — precisely the exposure
(browser history, server logs, `Referer`) that the requirement and the exclusion table
("*Tokens / codes in URL query or fragment* — refused") mandate against. `retrieval-flow.adoc §2`
states `form_post` "so the response … never rides in a URL"; the code does not implement it.
**Fix:** emit `response_mode=form_post` on the authorization request; add a test asserting it.

### C2 — RFC 9207 `iss` mix-up defense is dead code
*CLIENT-8, T-MIXUP · `flow/AuthorizationCodeFlow.java:143-156`, `flow/IssValidator.java`, `discovery/ProviderMetadata.java`*

`IssValidator` is instantiated by **no production code** (verified: only tests reference it).
`exchange()` runs the `state` check (`callbackHandler.handle`) and then immediately redeems the
code — the ordered `state → iss → exchange` sequence that `retrieval-flow.adoc §3` requires
("MUST be rejected before the code is exchanged") is not performed. Compounding it,
`ProviderMetadata` does not parse `authorization_response_iss_parameter_supported`, so even a
caller wiring `IssValidator` manually cannot derive its `requireIssuer` flag from discovery —
and with `requireIssuer=false`, an attacker who simply *omits* `iss` bypasses the check
(`IssValidator.java:63-69` returns silently). `CallbackParameters.java` openly notes the `iss` is
captured "so a later mix-up defence (deliverable 6) can check it." With two ASes configured (the
exact T-MIXUP scenario), the engine redeems a code from the wrong AS.

*Not closed by the integration suite (see S0):* `MixUpSpecIT` instantiates `new IssValidator()`
and calls `validate()` directly against real Keycloak issuers — it never drives
`AuthorizationCodeFlow.exchange()`, so a green mix-up IT coexists with the flow never invoking the
validator. The Quarkus producer also produces no `IssValidator`. **Fix:** wire
`IssValidator.validate(configuration.getIssuer(), callback, requireIss)` into `exchange()` before
redemption; parse the discovery flag to force `requireIss` when the AS advertises support; add a
flow-level test asserting the token endpoint is not called on `iss` mismatch.

---

## High

### H1 — Step-up result verification (`acr`/`auth_time`) is unimplemented
*CLIENT-12 · `flow/StepUpHandler.java`; no verifier class exists*

`step-up-and-logout.adoc §2.3` requires that after a re-driven authorization the engine "verifies
the resulting ID token's `acr` satisfies the requirement and `auth_time` satisfies `max_age` …
A response whose `acr` / `auth_time` does not satisfy the requirement MUST NOT be treated as a
successful step-up." No production code inspects `acr` or `auth_time` (grep confirms request-side
`acr_values` only). A step-up the IdP silently ignored (returns a low `acr`) is treated as a
successful elevation. **Fix:** add a post-exchange verifier asserting `acr` ∈ required set and
`auth_time` within `max_age`; fail closed otherwise.

### H2 — RFC 9470 `max_age` is parsed then discarded
*CLIENT-12 · `flow/StepUpHandler.java:75`, `flow/AuthorizationRequestBuilder.java`, `flow/FlowContext.java`*

`StepUpChallengeParser` extracts `max_age` (and accepts `max_age`-only challenges,
`StepUpChallengeParser.java:68-73`), but `StepUpHandler.initiate` forwards only `acrValues` into
`FlowContext.create`, and neither `FlowContext` nor `AuthorizationRequestBuilder` models a
`max_age` request parameter. A `max_age=300` challenge re-drives authorization **without** the
freshness constraint — or, for a `max_age`-only challenge, re-drives a request identical to the
original, looping forever without satisfying the resource server. **Fix:** carry `maxAge` in
`FlowContext` and emit the `max_age` authorization parameter.

### H3 — DPoP is proof-generation-only; no flow presents it, and it is non-conformant for resource requests
*CLIENT-11, CLIENT-18 · `dpop/SenderConstraint.java:86-91`, `dpop/DpopProofGenerator.java:136-156`, `flow/*`, `token/UserInfoClient.java:94`*

`SenderConstraint.apply` is on **no executable path** — `AuthorizationCodeFlow`, `RefreshFlow`,
`ClientCredentialsFlow`, and `TokenEndpointClient` build their header maps internally and never
attach a DPoP proof, so CLIENT-11 ("be able to *request* sender-constrained tokens at the token
endpoint") is unreachable through the engine's flow API. (`DpopBoundSpecIT` exercises only
`DpopProofGenerator` standalone, and no flow-level test asserts a `DPoP` header reaches a recorded
request — see S0.) Further:
- `generateProof(htm, htu)` emits only `jti`/`htm`/`htu`/`iat` — there is **no `ath` claim**
  (RFC 9449 §4.3, mandatory for protected-resource proofs), so a userinfo/resource DPoP request
  can never be conformant; `UserInfoClient` hardcodes `"Bearer "`.
- There is **no DPoP-Nonce support**: `TokenEndpointClient` throws a generic `TransportException`
  on any non-2xx and discards the `DPoP-Nonce` header, so a `use_dpop_nonce` challenge (§8) is
  unrecoverable — any AS that mandates server nonces (e.g. Okta default) permanently fails.
- `htu` is embedded verbatim, not stripped of query/fragment (RFC 9449 §4.2).

**Constraint-preservation is metadata-only (CLIENT-18):** `TokenLifecycleManager.applyRefresh` /
`StoredToken.refreshed(...)` copy the old `ConstraintBinding` onto the refreshed token, but the
refresh request carried no proof — so the store records a `cnf.jkt` binding for a token actually
issued as a plain bearer, misrepresenting a replayable token as sender-constrained. **Fix:** wire
`SenderConstraint` into the token/refresh/userinfo request paths; add `ath` and nonce-retry;
verify the refreshed token's `cnf` rather than copying the binding blindly.

### H4 — mTLS client auth is selectable and ranked *strongest* but cannot work
*CLIENT-4, CLIENT-11 · `auth/MtlsClientAuth.java:54-57`, `auth/ClientAuthenticationSelector.java`, `flow/TokenEndpointClient.java:89-94` (+ ParClient, RevocationClient, DiscoveryResolver)*

No code path ever passes an `SSLContext` to `HttpHandler.builder()` (verified against cui-http
2.0.0, which does expose `.sslContext(...)`); `ClientConfiguration` has no key-material field.
`MtlsClientAuth` only adds `client_id` to the form. Yet `ClientAuthenticationSelector` ranks
`tls_client_auth` at strength 3 and will **prefer** it over a working `client_secret_basic` when
both are advertised — selecting a method the transport cannot honor and producing an
unauthenticated request (or silently leaning on a process-global default SSLContext). **Fix:**
plumb an `SSLContext`/keystore through configuration into every endpoint client, or make
`MtlsClientAuth` construction fail fast until the transport supports it.

### H5 — Refresh-token reuse detection is an orphan and revokes nothing at the AS
*CLIENT-17, T-REFRESH-THEFT · `token/RefreshTokenFamily.java`, `flow/RefreshFlow.java`, `lifecycle/TokenLifecycleManager.java`*

`RefreshTokenFamily.rotate()` is called by **no executable path** (only a javadoc mention in
`RefreshFlow`, and a unit test — `RefreshConcurrencyTest` — that races the family object in
isolation, see S0). The shipped path — `RefreshFlow.refresh` → `TokenLifecycleManager.applyRefresh`
— performs rotation with **no reuse detection at all**. There is no session→family mapping, and
families are purely in-memory (lost on restart), unlike the pluggable `TokenStore`. Even when
reuse *is* detected, `RefreshTokenFamily.java:120-130` only sets a local `revoked` flag and
throws — it never calls `RevocationClient` (RFC 7009) to invalidate the still-live token at the
AS, nor clears the `TokenStore`. `token-handling.adoc §4` requires revocation "on detected
misuse." A thief holding the current rotated token keeps minting access tokens indefinitely;
only the legitimate client is locked out. (`supersededTokens`, `RefreshTokenFamily.java:48`, is
also a write-only, unbounded set of secret strings — never read, contradicting "minimal
storage.") **Fix:** wire the family into `RefreshFlow`, persist it alongside the store, and drive
RFC 7009 revocation + store-clear on detected reuse.

### H6 — No single-flight refresh; ordinary concurrency self-destructs the session
*CLIENT-22 · `lifecycle/TokenLifecycleManager.java:89-110`, `flow/RefreshFlow.java:94-121`*

Nothing serializes refresh per session: `needsRefresh` is a pure read and `RefreshFlow.refresh`
is stateless. Two threads that both observe `needsRefresh=true` both redeem the same refresh
token. Against a rotating AS with revoke-on-reuse (Keycloak), the second redemption trips
*AS-side* reuse detection and kills the grant; once client-side detection (H5) is wired, the
losing thread's rotation is classified as reuse and revokes the family — a benign thundering herd
is indistinguishable from theft. Two refreshes racing through `applyRefresh` can also land
last-writer-wins with the *older* result, storing a superseded refresh token. **Fix:**
per-session single-flight (mutex or shared in-flight future) spanning redeem + `applyRefresh`;
only presentations outside an in-flight rotation count as reuse.

### H7 — `client_credentials` is hard-capped at shared-secret auth
*CLIENT-4 · `flow/ClientCredentialsFlow.java:109-137`*

The flow reads `configuration.getAuthMethod()` directly and throws
`IllegalStateException("… not supported … before increment 3")` for `PRIVATE_KEY_JWT` /
`TLS_CLIENT_AUTH`; it neither accepts a `ClientAuthentication` strategy nor uses
`ClientAuthenticationSelector` (unlike `RefreshFlow`, `ParClient`, `RevocationClient`). It also
duplicates `ClientSecretBasicAuth`'s Basic-encoding byte-for-byte (drift risk). Service-to-service
acquisition therefore cannot use `private_key_jwt`/mTLS even when the AS advertises them — the
exact "weak client auth where private_key_jwt/mTLS is available" the exclusion table refuses.
**Fix:** give it a `ClientAuthentication` constructor like every other authenticated caller;
delete the duplicated encoding.

### H8 — Secret-carrying records leak tokens via default `toString()`
*Consistency / secure-logging · `lifecycle/StoredToken.java:45`, `token/RotationResult.java:41`, `flow/CallbackParameters.java:44`*

`StoredToken` (raw `accessToken`/`refreshToken`/`idToken`), `RotationResult` (raw
`refreshToken`), and `CallbackParameters` (`code`/`state`) are records with auto-generated
`toString()`. Any debug log, assertion failure, or exception that formats one writes live token
material to logs — directly contradicting the module's own policy, where
`ClientConfiguration.clientSecret` is `@ToString.Exclude`d precisely so "an incidental toString
can never leak it," and session ids are SHA-256-masked before logging. **Fix:** explicit masked
`toString()` on all three records; add a test asserting secrets are absent.

### H9 — Architecture boundary "engine issues no HTTP of its own" is contradicted
*Architecture/threat-model boundary; SSRF delegation (COMMONS-3) · `discovery/DiscoveryResolver.java`, `flow/TokenEndpointClient.java`, `flow/ParClient.java`, `token/UserInfoClient.java`, `lifecycle/RevocationClient.java`*

`architecture.adoc` ("Boundaries enforced") and the threat model's out-of-scope section state the
engine "issues *no* HTTP of its own" and delegates SSRF/TLS to commons (COMMONS-3: egress
allow-list, internal/link-local blocking). In fact five classes construct their own cui-http
`HttpHandler` + `java.net.http` calls; **no COMMONS-3 SSRF control** is applied to the
discovery-advertised `token_endpoint`/`userinfo_endpoint`/`revocation_endpoint`/PAR URLs before
they are fetched — protection is TLS-scheme-only. A hostile/compromised discovery document can
point credential-bearing back-channel POSTs at internal addresses. The javadoc calls this "design
fork 1," but **no document under `doc/` records the fork** — the delegation claim that excludes
SSRF from the client's threat model is, as written, false. **Fix:** either apply an SSRF/egress
control on advertised URLs in these clients, or amend `doc/client` (architecture + threat model +
requirements exclusion table) to state the client performs its own hardened HTTP and owns the
control.

---

## Medium

- **M1 — `token_type` never validated** (*CLIENT-14 · `flow/TokenEndpointClient.java:127-132`, `token/TokenResponse.java`*). `parse()` checks only `access_token` presence; RFC 6749 §5.1 makes `token_type` REQUIRED. A `token_type: DPoP` (or garbage) response is silently consumed as Bearer, and a DPoP-requested flow cannot detect the AS ignored the constraint.
- **M2 — Post-logout `state` return-verification has no seam** (*CLIENT-13 · `logout/EndSessionFlow.java`*). The engine builds the end-session redirect with a `state` but provides no component to verify the echoed `state` on the post-logout redirect (`CallbackHandler` is auth-code-specific). The mandated logout CSRF check is entirely unimplemented.
- **M3 — Signed (JWT) userinfo unsupported** (*CLIENT-16 · `token/UserInfoClient.java:117-135`*). Only `Accept: application/json` plain userinfo is handled; against an OP with `userinfo_signed_response_alg` the engine cannot operate, and there is no path to the signature validation the requirement describes.
- **M4 — `at_hash` unvalidatable** (*CLIENT-16 · `token/IdTokenValidationBridge.java:69`*). `IdTokenRequest.of(idToken)` never receives the access token, and no `at_hash`/`atHash` handling exists in client *or* validation. The ID-token↔access-token binding check the spec commits to cannot be performed.
- **M5 — Security rejections throw JDK exceptions, not the commons typed hierarchy** (*CLIENT-20, COMMONS-9 · `CallbackHandler`, `IssValidator`, `IdTokenValidationBridge`, `AuthorizationRequestBuilder`, `PostLogoutRedirectValidator`, `RefreshTokenFamily`*). State/nonce/PKCE-downgrade/mix-up/open-redirect rejections are `IllegalStateException`/`IllegalArgumentException`. The Quarkus/RESTEasy edge cannot map these to RFC 9457 via the typed hierarchy, so they surface as generic 500s.
- **M6 — Refresh skips OIDC §12.2 ID-token/sub consistency** (*`flow/RefreshFlow.java:113-121`, `token/RotationResult.java`, `lifecycle/StoredToken.java`*). An `id_token` returned on refresh is silently discarded; `RotationResult` has no field for it and `StoredToken` carries the pre-refresh ID token forward forever. Neither the refreshed ID token's `iss`/`sub`/`aud` consistency nor the access token's `sub` is checked — an AS bug/mix-up/store cross-wiring returning another user's tokens is accepted under the victim's session.
- **M7 — Response fully buffered before the size cap** (*`flow/TokenEndpointClient.java`, `token/UserInfoClient.java`, `flow/ParClient.java`, `discovery/DiscoveryResolver.java`*). `BodyHandlers.ofString()` reads the whole body into memory; `maxContentSize` is checked only afterwards, so the limit defends nothing — a hostile AS can OOM the client first. This path uses raw `java.net.http`, not a hardened commons fetcher. Use a bounded/streaming body handler.
- **M8 — Uneven log/exception sanitization of external input** (*`flow/CallbackHandler.java:57-59`, `flow/StepUpChallengeParser.java:97`, DSL-JSON `e.getMessage()` at parse sites, `auth/ClientAuthenticationSelector.java:71-73`*). `LogSanitizer` is applied to the callback `iss` and discovery issuer, but the browser-controlled callback `error`, the RS `max_age`, AS-controlled parse-error fragments, and advertised auth-method strings flow unsanitized into log/exception messages (CWE-117), inconsistent with the project's own discipline.
- **M9 — Transport-boundary exception type inconsistent** (*`flow/TokenEndpointClient.java:89` et al. vs `discovery/DiscoveryResolver.java:110-123`*). Discovery converts non-TLS/malformed URLs to `TransportException`, but the token/PAR/revocation/userinfo clients let `HttpHandler.build()` throw a raw `IllegalArgumentException` for an `http://` or malformed endpoint — despite javadoc declaring only `TransportException`. A discovery doc with an `http://` `token_endpoint` bypasses callers' `TransportException` handling.
- **M10 — Trailing-slash asymmetry breaks spec-compliant issuers** (*`discovery/DiscoveryResolver.java:125-127` vs `182-191`*). The slash is stripped when building the well-known URL, but `validateIssuer` compares against the *original* configured issuer — a configured `https://as.example/realms/x/` always fails against a spec-compliant AS. Fail-closed, so a correctness/usability defect.
- **M11–M14 (test)** — the medium test gaps that mirror the above: `form_post` / `acr`-`auth_time` / mix-up-at-exchange are claimed in the traceability matrix but have no backing test (**TEST-1/3/4**); "tampered token" tests validly re-sign the token so **signature integrity is never exercised**, rejection comes only from unknown-issuer lookup (**TEST-7**); the callback attack-database feeds `CallbackParameters.of(Map)`/the constructor and never the real `parse(rawQuery)` decode/duplicate-rejection surface, so encoding-evasion payloads never hit the code and the assertions cannot fail for any corpus-specific reason (**TEST-8**); the `ParClientTest` "only `request_uri`" assertion is vacuous against a hard-coded mock constant (**TEST-9**); the token-endpoint size guard, refresh-window clock edges, and the claimed refresh/logout atomicity race are untested (**TEST-12/13/14**).

---

## Low / informational

- **L1** — All 12 HTTP test classes use `.allowInsecureHttp(true)`; the test strategy mandates `@EnableMockWebServer(useHttps = true)` because the engine's outbound HTTP is TLS-only. The TLS path is never exercised at unit level, and non-TLS refusal is tested only for discovery, not for token/userinfo/revocation/PAR. (**TEST-5**)
- **L2** — Security-critical `internal` package (`LogSanitizer`, `FormEncoder`, `JsonEscaper`) has **zero tests**; a sanitizer/encoder regression would pass the suite. `ClientConfiguration.toString()` secret-exclusion is likewise untested. (**TEST-15/16**)
- **L3** — Mandated commons test-jar reuse is partial: `TokenDispatcher`, `RevocationDispatcher`, `EndSessionDispatcher`, `AdversarialResponses`, `JwtTokenTamperingUtil`, and `ShouldHandleObjectContracts` are unused; four near-identical token-endpoint dispatchers are hand-rolled and duplicated across flow tests. (**TEST-6**)
- **L4** — Vacuous/conditional assertions: `StepUpChallengeAttackDatabaseTest` silently `return`s for most corpus entries; `CallbackParametersAttackDatabaseTest` asserts `isPresent()` after `orElseThrow()`; `PostLogoutRedirectAttackDatabaseTest` guards `assertThrows` behind a null/blank check. (**TEST-10/11**)
- **L5** — `authorization_endpoint` (the user redirect target) gets no scheme/TLS check, unlike every back-channel URL. A tampered discovery doc (dev insecure mode / compromised AS) can point users at `http://` or an arbitrary scheme. Defense-in-depth gap. (`flow/AuthorizationRequestBuilder.java`)
- **L6** — `ClientAuthenticationSelector.java:84` `Set.copyOf(advertised)` NPEs on a JSON `null` element in `token_endpoint_auth_methods_supported` (AS-controlled → unclassified crash). `ProviderMetadata.supportsS256()` tolerates the same shape.
- **L7** — No one-time-use semantics for `state`/`FlowContext`; the handler is stateless and can validate a replayed callback repeatedly. Document the discard contract or add a consumed flag. (`flow/CallbackHandler.java`, `flow/FlowContext.java`)
- **L8** — `CallbackParameters.of(Map)` silently bypasses the duplicate-parameter injection defense that `parse(String)` enforces; an RP feeding `request.getParameterMap()` loses the RFC 9700 §4.7.3 defense with no javadoc warning.
- **L9** — `private_key_jwt` supports only `RS256/384/512` — no `PS256`/`ES256`, which FAPI 2.0 (cited as a bar-raiser) requires while banning RS256. (`auth/PrivateKeyJwtAuth.java`)
- **L10** — `StepUpChallengeParser` uses naive `split(",")`/`unquote`: breaks on quoted values containing commas, silently last-wins on duplicate params, ignores escaped quotes. Only reachable via a `WWW-Authenticate` header, but robustness matters.
- **L11** — `expires_in` is a primitive `long`; absent (`0`) is indistinguishable from a literal `0`, and downstream an unknown expiry (`expiresAt == null`) makes `isExpired`/`needsRefresh` **always false** — a token with unknown lifetime is used forever and never proactively refreshed. Negative/absurd values also pass unvalidated. Prefer nullable lifetime + conservative default window. (`token/TokenResponse.java`, `lifecycle/RefreshScheduler.java`, `lifecycle/StoredToken.java`)
- **L12** — `@NullMarked` appears nowhere in the client module, while `token-sheriff-validation` carries it; every `@Nullable` here sits in a null-unmarked context, so JSpecify tooling cannot enforce the non-null default.
- **L13** — Header collisions: caller/auth headers are applied via `HttpRequest.Builder.header()` (append) after `Content-Type` is set; a colliding `Content-Type`/second `Authorization` is duplicated, not replaced. Use `setHeader`. (`flow/TokenEndpointClient.java`, `flow/ParClient.java`, `lifecycle/RevocationClient.java`)
- **L14** — Config validation gaps: blank `issuer`/`clientId` accepted; `scopes` may contain null/blank (→ literal `"null"` in the `scope` parameter); no issuer URL-shape check at construction — inconsistent with `PostLogoutRedirectValidator`, which validates every entry. (`config/ClientConfiguration.java`)
- **L15** — Inconsistent timeouts (discovery 2s/3s; other clients 5s/10s), all hardcoded, no configuration knob — a slow IdP can fail discovery while token calls to the same host succeed. (`discovery/DiscoveryResolver.java` vs the endpoint clients)
- **L16** — New `HttpClient` built per request in every client, never reused/closed — selector/executor thread churn under load. (`flow/TokenEndpointClient.java`, `token/UserInfoClient.java`, `lifecycle/RevocationClient.java`)
- **L17** — `TokenLifecycleManager.revokeAndClear` logs `LOGOUT_TOKENS_REVOKED` when it has only *cleared the store* — AS revocation isn't guaranteed to have happened. Misleading audit log.
- **L18** — `LogSanitizer` escapes but never truncates; a megabyte-long attacker-controlled `iss`/`post_logout_redirect_uri` is written to WARN in full (log flooding). Cap output length.
- **L19 (traceability rot)** — Production javadoc cites wrong `CLIENT-N` IDs, corrupting the stable traceability the requirements declare: `RefreshFlow`/`RefreshTokenFamily` cite `CLIENT-5` (state CSRF) for rotation (`CLIENT-17`); `FlowContext` cites `CLIENT-2` for state/nonce (`CLIENT-5`/`CLIENT-6`); `IdTokenValidationBridge` cites `CLIENT-2`/`CLIENT-15` for nonce (`CLIENT-6`/`CLIENT-16`); `RevocationClient` cites `CLIENT-13` for `CLIENT-17`.
- **L20 (doc rot)** — Root `package-info.java` still says "no client behaviour lives here yet"; `ClientCredentialsFlow`/`ClientAuthMethod` reference plan-internal "increment 2/3"; `pom.xml` keeps `maven.javadoc.skip=true` "while the engine is under construction" though the public surface now exists.
- **L21** — `pom.xml` pins `version.archunit=1.3.0` locally while dependabot bumped validation to 1.4.2 (#552) — cross-module test-dependency drift hidden from future bumps.
- **L22 (info)** — Token material is held as immutable `String` throughout (`StoredToken`, `RefreshTokenFamily`, `TokenResponse`, `ClientConfiguration.clientSecret`); cannot be zeroed on clear/logout. An accepted Java trade-off, but worth stating explicitly in the store's security docs given "protect the at-rest store."

---

## Verified-good defenses (regression anchors — keep these)

- **PKCE S256** — 32-byte `SecureRandom` verifier → 43-char base64url (valid RFC 7636 length/charset); challenge = `B64URL(SHA-256(ASCII(verifier)))`; `plain` not implementable; builder fails closed when the AS doesn't advertise `S256`.
- **`state`/`nonce`** — 256-bit `SecureRandom` each; constant-time compare (`MessageDigest.isEqual`) in `CallbackHandler`, `IdTokenValidationBridge`, `IssValidator`, `SubBindingValidator`; callback order error → state → code, all before redemption.
- **Basic auth** — client id + secret form-urlencoded *before* base64 (RFC 6749 §2.3.1) in both `ClientSecretBasicAuth` and `ClientCredentialsFlow`.
- **`private_key_jwt`** — fresh `jti` (`UUID.randomUUID`), 60s lifetime, full `aud`/`iss`/`sub`/`iat`/`exp`, RFC 8259-complete JSON escaping, strict allow-list (no `none`/HMAC).
- **DPoP proof construction** — RFC 7638 lexicographic JWK thumbprint (`e`,`kty`,`n`), correct unsigned big-endian encoding, `typ: dpop+jwt`, RS256/384/512-only, per-call `Signature` instances, bounded fail-closed `jti`-reuse LRU. (Construction is correct; wiring is the gap — see H3.)
- **RFC 7009 revocation** — any 2xx = success (unknown token = success per §2.2), optional `token_type_hint`, client auth applied, TLS enforced.
- **Token store race** — `InMemoryTokenStore.update` uses `computeIfPresent`, `remove` is atomic take-and-clear; a refresh landing after logout is a no-op, not a resurrection — and the contract is documented on the SPI for custom stores.
- **Post-logout open-redirect** — `PostLogoutRedirectValidator` exact whole-string match against an immutable copied allow-list, no normalization/prefix/wildcard, fail-closed, CWE-117 sanitized.
- **Validation-pipeline monopoly** — access and ID tokens always pass through the bridges into the multi-issuer pipeline; no local signature/algorithm logic exists in the client (CLIENT-15).
- **Duplicate callback-parameter rejection** (`CallbackParameters.parse`, post-decode, RFC 9700 §4.7.3); `FormEncoder` is the single UTF-8 form-encoding path; `JsonEscaper` covers all mandatory control-char escapes.
- **`ClientLogMessages`** — unique identifiers, correct levels, no template interpolates a token/secret; session id SHA-256-masked before the INFO log.
- **ArchUnit boundaries** — `ClientArchitectureTest` (no CDI/MP/JAX-RS) and `CommonsTransportReuseTest` (no bespoke `HttpClient`) actually enforce the stated architecture.
- **Weak-auth refusal** — `WeakAuthRefusedTest` + `ClientAuthenticationSelector` genuinely fail closed when the AS advertises none of the configured methods and never downgrade below the strongest advertised method.
- **Rotation reuse race** — `RefreshConcurrencyTest` correctly proves exactly-one-winner across 16 threads on the family object (the object works; it's just not wired into the flow — H5).

---

## Suggested remediation order

1. **Fix the test strategy first (S0), because it is why the rest was not caught.** Add the
   mandatory wired-flow / negative-path tier: drive `AuthorizationCodeFlow`/`RefreshFlow`/the
   step-up path *and* the produced Quarkus bean graph as an application would, and assert the
   attack is refused before any side effect (token endpoint not called on `iss` mismatch;
   front-channel URL carries only `request_uri`; `DPoP` header reaches the recorded request;
   rotated-token reuse fires RFC 7009 revocation). Forbid marking a requirement "covered" in the
   traceability matrix without such a test. Adding this tier turns the following items into
   red tests, which is the point.
2. **Decide the staging question.** Confirm with the module owner whether CLIENT-8/10/11/12/17 were deliberately staged. If yes, correct `doc/client` (requirements status, threat-model coverage claims, test-strategy traceability) so the docs stop asserting coverage that does not exist — this is itself a security-documentation defect. If no, they are release blockers.
3. **Wire the four orphaned defenses** into the actual flow paths with the request-verifying tests from step 1: `IssValidator` (C2), `ParClient`/`request_uri` redirect (M-PAR), `SenderConstraint`+`ath`+nonce (H3), `RefreshTokenFamily`→`RevocationClient` + single-flight (H5/H6).
4. **Implement the two missing behaviors**: `response_mode=form_post` (C1) and step-up `acr`/`auth_time` verification + `max_age` (H1/H2).
5. **Fix mTLS** (H4) — plumb `SSLContext` or fail fast + stop ranking it strongest.
6. **Secure-logging pass**: masked `toString()` on the three records (H8); uniform `LogSanitizer` on all external input (M8).
7. **Reconcile the HTTP/SSRF boundary** (H9) — apply the control or amend the docs.
8. **Harden the remaining test surfaces**: real signature tampering via `JwtTokenTamperingUtil` (TEST-7), point the callback corpus at `parse()` (TEST-8), add HTTPS-mock coverage (TEST-5), and cover the `internal` package (TEST-15).
9. Sweep the low/info items (typed exceptions, `token_type`, trailing-slash, `@NullMarked`, doc/traceability rot, config validation).
