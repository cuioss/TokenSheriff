# Corpus analysis — 2026-11-23

Ground-truth read of the addendum (`sources/client_addendum.md`) against four repositories, done
at `decompose` Step 2 by four read-only `execution-context-level-3` leaves (one per repository).
The orchestrator itself checked again every claim marked ✔ below. The other claims are leads from a leaf report.
Repository HEADs: TokenSheriff `main` after `9c242518` (0.9.10 released, 0.9.11-SNAPSHOT);
API-Sheriff, Audit-Notary, pm-mcp-clients at their local `main` checkouts on 2026-11-23.

## 1. Corrections to the addendum

| Addendum claim | Finding | Evidence |
|---|---|---|
| §8.1: the deployment module must register the client's DSL-JSON converters | **Already done in 0.9.10** (CLIENT-26, PR #898, api-sheriff-seams PLAN-01). The client jar ships `META-INF/native-image/.../reflect-config.json` with the 8 converters and 8 DTOs. Audit Notary's `TokenClientNativeRegistration` (64 lines) is now redundant. Its removal is consumer work; a native-build proof is still owed. | ✔ `token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/reflect-config.json` (8 converter entries) |
| CLIENT-35: add a bounded `jti` memory | **The memory already exists** as `LogoutTokenReplayGuard` (VALIDATION-13.5), together with `LogoutTokenValidator` (VALIDATION-13.4). Both are in token-sheriff-validation. The guard is package-private and owned by the validator. The only missing parts are the session selector (`sid` alone, or `sub` for all sessions) and possibly a public way to set the guard's capacity and window. | ✔ `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenReplayGuard.java:56` (`final class`, package-private) |
| §8.3: the engine could publish "claim as a list of strings" | **Present for access tokens.** `ClaimValue.asList` and `AccessTokenContent.getGroups()` exist, and `JsonCollectionMapper` keeps each array element whole. **Missing:** `IdTokenContent` has no groups accessor. Audit Notary reads groups from the ID token. | ✔ `ClaimValue.java:82`; IdTokenContent gap is a leaf lead |
| CLIENT-32: a new refresh-disposition table | **Partly refuted.** `TokenLifecycleManager` already switches on `RefreshFailureClassification.Kind` (`REDEEMED`, `CREDENTIAL_REJECTED`, …) internally (ADR-0005/0010). API Sheriff's `TokenRefreshCoordinator.disposeRefusal` (ADR-0046) has a richer table: it adds persist-failure and session-terminated, and revokes the successor or the presented token. CLIENT-32 = publish one table that reconciles the two. | ✔ `TokenLifecycleManager.java:1006,1020` |
| SESSION-1: tokens held "through TokenLifecycleManager", extracted from API Sheriff | **Re-modelling, not extraction.** API Sheriff uses none of `TokenLifecycleManager`, `TokenStore`, `RefreshResultVerifier`, `StepUpResultVerifier`, `CachingDiscoveryResolver`; its `SessionRecord` holds raw tokens. | ✔ 0 references to `TokenLifecycleManager` in `API-Sheriff/api-sheriff/src/main/java` |
| SESSION-7: session-ended event with a cause | **New design.** API Sheriff's `SessionEndListener` has only `sessionEnded(String sessionId)`. | ✔ `SessionEndListener.java:40` |
| §10.4: Audit Notary drops "about 1,300 of 1,700 lines" | **Not reproducible.** security+web = 2,335 lines with `wc -l`. The generic part is about 1,330 lines (57 %), or 590 of 921 code lines (64 %). Only about 148 lines go without a new module: `QueryResponseModeRequestBuilder` and `TokenClientNativeRegistration`. | leaf count |
| Part C has a precedent in API Sheriff | **No precedent.** API Sheriff has no `SecurityIdentity` and no `quarkus-oidc`; it uses its own `PipelineRequest` stages. Audit Notary has no `SecurityIdentity` either (it puts the session in the Vert.x `RoutingContext`). Its step-up, pseudonym and first-login gate are specified only (AUN-AUTH-4, UC-ACC-3/-4), not built. Part C would be designed ahead of its first consumer for those parts. | leaf reads |
| pm-mcp-clients is a third consumer | **It is not a consumer.** It is not an OAuth/OIDC client: it uses no cookies and holds no sessions, and it pairs devices with a bearer device secret. The enforcer bans Quarkus, Token Sheriff and OIDC dependencies. The only link is a future item, L-2 in `plan-marshall-documentation/doc/later-improvements.adoc`, which leaves open whether OAuth for the web adapter takes the BFF route or the token-in-memory route. That repo's daemon (`plan-marshall-mcp`) would need token-sheriff-validation only. | leaf read |

## 2. Version state of the consumers

| Consumer | Pinned | Consequence |
|---|---|---|
| API Sheriff | **0.9.9** ✔ (`API-Sheriff/pom.xml:63`) | Still carries copies of `ResponseMode` (`QueryResponseModeAuthorizationRequestBuilder`, 178 lines) and of `LogoutTokenValidator` + `LogoutTokenReplayGuard` (459 lines). Its own `kidicap-gateway-requirements` PLAN-34 (staged, blocked until 0.9.10 is pinned) is the vehicle for deleting them. |
| Audit Notary | **0.9.10** ✔ (`Audit-Notary/pom.xml:42`) | Can drop `QueryResponseModeRequestBuilder`, `TokenClientNativeRegistration`, its hand-made metadata cache (`CachingDiscoveryResolver` is opt-in via `ClientRuntimeConfig.DISCOVERY_CACHE_TTL`). |

## 3. What is really extractable (API Sheriff `bff`, 66 files / 16,413 lines)

The `bff` package is almost free of Quarkus. Its only container import is `jakarta.inject.Singleton` in `GatewayJson` ✔. The wiring sits outside the package, in `quarkus/BffRuntimeProducer` (1,591 lines).

| Body | Lines (main) | Class | Gateway coupling to cut |
|---|---|---|---|
| `session/` SessionBinding, SessionStore, InMemorySessionStore, ServerSessionBinding, SessionRecord, SessionCookieCodec, SessionEndListener, SessionRelayRegistry | ~2,200 | GENERIC | `SessionRecord.activeScopes/grantedScopes` (scope widening) |
| `pending/` InMemoryPendingAuthorizationStore, PendingAuthorizationStore, PendingAuthorizationRecord, BindingCookieCodec | 851 | MIXED | `LocationPathReview`, `Widening` marker, `GatewayException`/`EventType` |
| `logout/` RpInitiatedLogout, BackchannelLogoutReceiver | ~735 | GENERIC | none beyond log ids; the validator and guard copies are superseded by the engine |
| `refresh/` TokenRefreshCoordinator (single-flight, back-off, disposition) | ~600 of 1,141 | MIXED | the scope-driven leg (`refreshForScopes`) |
| `csrf/` CsrfDefence (Origin / Sec-Fetch-Site) | ~250 of 357 | MIXED | `PipelineRequest`, `forCookieForwardingRoute` |
| `cookie/` sealed-cookie mode | 2,253 | GENERIC, wanted? | open question 9.3 |
| `login/` LoginFlow | 214 | GENERIC | `ScopedEngineFlows`, `ReturnTargetScopes`, `SessionWidening` stay |
| Test surface | ~9,000 lines (~12,900 with cookie mode) | | |

Gateway-specific parts that stay: step-up as API Sheriff implements it (driven by an upstream 401 challenge), the reserved-path endpoints, `SessionAuthenticationStage`, the config rules for `gateway.yaml`, `PendingQuotaKeys`/`ForwardedTrust`, and per-route scope widening.

Must not break: sealed-cookie mode (ADR-0018 iii–v, ADR-0043); the ApiSheriff-150 boot message; the latched WARN of ApiSheriff-147; and the behavioural pins that `persist` never creates a session, that a re-issued handle keeps the session identity, and that no WebSocket relay outlives its session. The threat model is `API-Sheriff/doc/security-threat-model.adoc` (BFF-01…BFF-21, at :2955–:4694); BFF-20 and BFF-21 move. The ADRs to amend, never delete: 0018, 0043, 0046, 0058, 0080.

## 4. Divergences between the two consumers (each one an ADR decision)

| Item | API Sheriff | Audit Notary |
|---|---|---|
| Pending-login TTL | 5 min fixed (`PendingAuthorizationRecord.FIXED_TTL`) ✔ | 10 min |
| Per-source share | 256 (`MAX_PENDING_PER_SOURCE`) ✔ | none |
| Logout revokes | refresh only (ADR-0080) | refresh + access |
| Revocation-failure log | first WARN (latched), later DEBUG | every one WARN |
| Session cookie | handle ≠ internal id; `SameSite=Lax` fixed | internal id in cookie; `SameSite=Strict`; no `Max-Age` |
| CSRF | Origin or `Sec-Fetch-Site`, no token | `X-CSRF-Token` per session + Origin |
| Activity | only requests let through | every `/api/v1` request, refused ones included (renews before refusal) |
| Session defaults | TTL 3600 s, idle 1800 s, 10,000 total, 10 per subject | idle 30 min, absolute 8 h, unbounded |
| Callback answer | 302 | same-origin meta-refresh page (works with `Strict`) |

## 5. Side findings (not this epic's code)

- HYPOTHESIS (API Sheriff, security): the step-up re-drive is an ordinary login pending record. The callback destroys the old session and binds a new one with no subject check, so a different subject could complete a step-up and replace the session. The widening path compares the subject with plain `String.equals`, which is not constant-time. Confirm/refute at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/CallbackEndpoint.java` § the `:415` subject comparison and the `:516` destroy-then-bind.
- Audit Notary's activity rule renews a session on refused requests, which contradicts SESSION-2.
- Audit Notary has no threat model for this layer; its security requirements exist only as normative text (AUN-AUTH-1, AUN-AUTH-2, AUN-AUTH-4, AUN-WEB-8, AUN-WEB-9).
- Token Sheriff docs: `doc/client/architecture.adoc:22` says "We do not build a BFF here", and the threat model puts BFF concerns out of scope (`threat-model.adoc:494-496`). New modules need an ADR (next number 0032) and edits to these docs. The highest requirement id is CLIENT-30; the next free one is CLIENT-31.
