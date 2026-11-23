# Epic: Client framework aspects — session and BFF layers above the engine

slug: client-framework-aspects

> Hand-written narrative for one epic under `.plan/orchestrator/client-framework-aspects/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug client-framework-aspects`
> on the merged tree, and `git add` the result.

## Vision

Two consumers of the confidential-client engine have each built the browser-facing layer of a
backend-for-frontend (BFF) themselves:

- **API Sheriff:** the `bff` package, 66 files, 16,413 lines; it pins Token Sheriff 0.9.9.
- **Audit Notary:** about 2,300 lines across its `security` and `web` packages; it pins 0.9.10.

Both built a session store, a store of pending logins, logout with revocation, CSRF and a return-address
check, and they differ in ways no decision accounts for. Audit Notary also lacks back-channel logout,
refresh, session bounds and per-source fairness, all of which API Sheriff hardened.

This epic gives Token Sheriff those framework aspects, so that no consumer re-implements a protocol or
session rule:

- **Part A:** five engine seams in `token-sheriff-client` (CLIENT-31 to CLIENT-35).
- **Part B:** a framework-neutral `token-sheriff-session` module.
- **Part C:** a Quarkus extension, `token-sheriff-bff-quarkus`.

A decision record (ADR-0032) gates Parts B and C. It must weigh the Quarkus OIDC extension in web-app
mode, which no project document has compared so far.

**Done** means three things:

- Parts A, B and C are released, or the ADR has declared a part out of scope.
- Each consumer has a hand-off note naming what it can delete against which release.
- The client docs no longer say "we do not build a BFF here" without the ADR's qualification.

**The consumer side is not in this epic** (operator decision, 2026-11-23). API Sheriff and Audit
Notary delete their copies in their own repositories, after pinning the release that contains the
replacement. The source proposal is `sources/client_addendum.md`. Its corrections against the
code are in `sources/corpus-analysis-2026-11-23.md`; read that first, since several addendum
claims did not hold.

## Queue annotations

- **PLAN-01:** docs-only; it settles the ADR. **Every Part B and Part C spec (PLAN-05 to PLAN-10)
  depends on it.** After it lands, re-ground those specs against the accepted ADR with `cleanup` before
  emitting them. The operator chose to stage everything now, knowing the ADR may reshape B and C.
- **PLAN-02:** independent of the ADR, because no open question touches step-up binding or the
  back-channel selector. It can run beside PLAN-01 under scope 2.
- **PLAN-03, PLAN-04:** each waits for PLAN-01. PLAN-03 needs the pending-login defaults (5 or 10
  minutes; question 9.5); PLAN-04 needs the access-token-at-logout rule (question 9.2).
- **PLAN-11:** independent of the ADR, like PLAN-02, so it may run before PLAN-01 lands. It shares the
  flow tests, `requirements.adoc`, the specification documents and the test mapping with PLAN-02 to
  PLAN-04, so expect the operator overlap question at `next` and run it in sequence with them. It is
  staged last but is not gated behind Parts B and C.
- **PLAN-05 to PLAN-07:** the new module `token-sheriff-session/` does not exist yet, so the surface
  reader reports its entries as unresolved. The gate cannot see overlaps between these three inside the
  new module, so they are sequenced by their `Depends on:` lines rather than by the disjointness gate.
  Never run two of them in parallel.
- **PLAN-08 to PLAN-10:** run strictly in order, for the same reason; they resolve inside
  `token-sheriff-quarkus-parent/`.

## Decisions

- 2026-11-23 — Epic created from the operator's request. The source proposal `doc/client_addendum.md`
  (untracked, never committed) was moved into `sources/client_addendum.md`.
- 2026-11-23 — The corpus read ran as four read-only leaves, one per repository: API Sheriff, Audit
  Notary, pm-mcp-clients and the Token Sheriff baseline. One envelope would have exceeded a leaf's
  context. Every load-bearing claim was spot-checked in the orchestrator before it was recorded.
- 2026-11-23 — `parallelization_scope = 2` (operator).
- 2026-11-23 — Stage everything now: A, the ADR, B and C (operator). Alternatives considered:
  "A plus the ADR now, B and C after" (recommended at the ask), and "A only". Accepted risk: B and C
  may need re-scoping once the ADR lands; the PLAN-01 annotation records that obligation.
- 2026-11-23 — The consumer side is out of scope and recorded as hand-off notes (operator), as in
  `api-sheriff-seams`.
- 2026-11-23 — pm-mcp-clients is not a consumer: it is not an OAuth/OIDC client, and its enforcer
  bans Quarkus, Token Sheriff and OIDC. It is recorded as a Watch.
- 2026-11-23 — Part A was regrouped from five seams into three plans by the code each touches.
  - CLIENT-34 and CLIENT-35 go together, with the ID-token groups accessor, as PLAN-02.
  - CLIENT-31 is alone, as PLAN-03.
  - CLIENT-32 and CLIENT-33 go together, as PLAN-04, both in `lifecycle`/`logout`.
  - Addendum §8.1 is dropped: it shipped in 0.9.10 as CLIENT-26.
  - CLIENT-35 is narrowed to the session selector, because the validator and the `jti` guard exist
    (VALIDATION-13.4 and 13.5).
- 2026-11-23 — Part B was split into three plans and Part C into three, each well under the
  six-deliverable split guard.
- 2026-11-23 — Operator observation: API Sheriff has a client-specific, configurable endpoint that
  tells the browser client about the user and acts as a sentinel for whether the user is logged in.
  - **Verified:** it is `bff/reserved/UserInfoEndpoint` at `oidc.user_info.path`, with
    `ClaimAllowlistFilter` and `IdTokenClaimProjection`, 650 lines plus tests.
    - It returns an allowlist-capped claim view in native JSON types, plus `expires_at`, `auth_time`
      and `acr`.
    - Without a session it answers `401` problem JSON, never a redirect, and every answer is `no-store`.
    - Polling it does not renew the idle deadline (`API-Sheriff/doc/user/bff-session.adoc:647`).
  - Audit Notary's `/api/v1/session` is its domain-laden counterpart (rights, CSRF token, first-login
    flag). It renews the session on every call.
  - **Disposition: fold, no new plan.**
    - PLAN-01 gains ADR question 11, the generic-versus-domain split of the session view.
    - PLAN-08 gains BFF-9, the session-info endpoint, inside its endpoints deliverable.
    - PLAN-09 gains the `SessionViewContributor` hook inside its hooks deliverable.
    - PLAN-10 gains the sentinel IT flow.
  - No deliverable count changed, and the expected surfaces are unchanged: every addition falls inside
    paths already declared.
- 2026-11-23 — The operator confirmed that `UserInfoEndpoint` is the aspect meant, and directed "reuse
  the same aspect". The view moves down a layer:
  - SESSION-10 in `token-sheriff-session` (PLAN-07, deliverable 3, now 5 deliverables) is the one
    framework-neutral implementation, extracted from API Sheriff's already framework-agnostic endpoint.
  - BFF-9 (PLAN-08) becomes a thin Quarkus edge over it.
  - `SessionViewContributor` (PLAN-09) becomes the CDI adapter of SESSION-10's contributor SPI.
  - ADR question 11 is recorded as decided.
  - Both API Sheriff and Audit Notary reuse the same code.
- 2026-11-23 — Repository placement, decided by the operator after an adversarial review
  (`sources/repo-placement-review-2026-11-23.md`; same-repo side 78/100, separate side 55/100): **the
  same repository, with split-out triggers.**
  - **Deciding grounds:** ADR-0001's precedent (no promotion trigger has bitten); the seams and the
    session layer co-evolve during design; snapshot deploys are unreliable, which makes a
    cross-repository build phase fragile.
  - **Accepted cost:** shared versioning means a session or BFF fix forces a validation release.
  - **Rejected alternatives:** a new repository now (`cuioss/session-sheriff`); deferring to the ADR.
  - **Recorded as** ADR-0032 question 12 (PLAN-01).
- 2026-11-23 — Native public-client login (Audit Notary's local MCP gateway, §4.3). The gap analysis
  found the engine has PKCE, `iss`, the endpoint scheme policy, DPoP and rotation, but lacks `none`,
  loopback, RFC 8707, the device grant and the Client ID Metadata Document.
  - **Operator chose option A:** stage only RFC 8707 resource indicators now, as PLAN-11 in WS-02.
    Every client shape benefits from them, the MCP 2026-07-28 revision requires them, and they let one
    refresh token yield audience-restricted access tokens for several services.
  - **Rejected:** a native public-client workstream now (B); deferring everything to a separate gateway
    epic (C).
  - PLAN-11 has 5 deliverables, under the split guard. The mitigations are binding in PLAN-05 (ArchUnit,
    opt-in artifact) and PLAN-08 (CI scoping, a CI-time measurement).

## Consumer hand-off notes

Nothing here is staged. Each note goes to the consumer's own repository once the named release exists.

- **Delivered to Audit Notary (2026-11-23):** `Audit-Notary/doc/tokensheriff.md` collects every finding
  below, the planned modules, the ADR state and the local-MCP-gateway gap analysis, and states the
  directive "for further changes, assume this epic is implemented".
  - Written at the operator's explicit request. This is a deliberate exception to the orchestrator's
    write boundary: one documentation file, with no git operation in that repository. Committing it is
    the operator's.
  - Keep the file in step: when a plan here changes a requirement id, a default or the module layout,
    note that Audit Notary's copy is stale.

- **Audit Notary, deletable against 0.9.10 now:**
  - `security/QueryResponseModeRequestBuilder` (84 lines) and its test → `ResponseMode.QUERY`
    (CLIENT-27).
  - `security/TokenClientNativeRegistration` (64 lines) → the client jar's own `reflect-config.json`
    (CLIENT-26). Prove it with one native build first; `NativeRegistrationTest` also covers JGit enums,
    so keep that part.
  - The hand-made metadata cache in `LoginService.metadata()` → `CachingDiscoveryResolver` (CLIENT-29;
    opt-in through the discovery-cache config).
- **Audit Notary, not a deletion:** a refused request renews the session (`RequestGate.api` → `use()`
  before CSRF and 401), which contradicts SESSION-2. Its fix comes with the adoption of Part C, or
  earlier in its own repository.
- **API Sheriff, deletable against 0.9.10 (its own PLAN-34 in `kidicap-gateway-requirements`):**
  - `bff/login/QueryResponseModeAuthorizationRequestBuilder` (178 lines).
  - `bff/logout/LogoutTokenValidator` and `LogoutTokenReplayGuard` (459 lines), replaced by the
    validation module's (VALIDATION-13.4 and 13.5).
- **API Sheriff, after Part B (PLAN-07):** `bff/reserved/UserInfoEndpoint`, `ClaimAllowlistFilter` and
  `IdTokenClaimProjection` (650 lines) and their tests are replaced by SESSION-10. The gateway keeps only
  its reserved-path registration and renders SESSION-10's framework-neutral outcome in its own pipeline.
  The `oidc.user_info.*` keys map onto SESSION-10's configuration.
- **Audit Notary, after Part C:** `web/SessionResource` shrinks to a `SessionViewContributor` (rights,
  pseudonym, first-login flag). The polled session view then stops renewing the session.
- **API Sheriff, later:** move onto `token-sheriff-session` once Part B is released. ADRs 0018, 0043,
  0046, 0058 and 0080 are amended, never deleted. Threats BFF-20 and BFF-21 move to the new threat
  model as the ADR decides.
- **API Sheriff, security lead to verify there (HYPOTHESIS):** the step-up re-drive replaces the
  session without a subject check, and the widening path compares the subject with `String.equals`.
  Confirm or refute at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/CallbackEndpoint.java`
  § subject comparison (:415) and destroy-then-bind (:516). CLIENT-34 (PLAN-02) gives it the
  constant-time primitive.

## Open Defects

- None owned yet.

## Watches

- **pm-mcp-clients / plan-marshall-mcp L-2** (`plan-marshall-documentation/doc/later-improvements.adoc`):
  OAuth for the web adapter, with BFF versus a token-mediating backend still undecided. Two things to
  re-check: the BFF branch would make it a third consumer of Part C, and the daemon side needs
  token-sheriff-validation only. Trigger: L-2 moves out of "later".
- **Native build on a real consumer** (carried over from `api-sheriff-seams`): the client jar's
  `reflect-config.json` has not yet been proven in a consumer's native image. Re-check when Audit
  Notary deletes `TokenClientNativeRegistration`.
- **Native public-client login: Audit Notary's local MCP gateway** (`Audit-Notary/doc/concepts/local-mcp-gateway.adoc`
  §4.3; operator question of 2026-11-23). It is NOT covered by this epic, which is confidential-client and
  server-side. The engine was checked at `9c242518`:
  - **Present:**
    - PKCE `S256`;
    - RFC 9207 `iss` validation (`flow/IssValidator`);
    - an https-only policy for the authorization endpoint, with the untrusted URL never echoed
      (`internal/EndpointSchemePolicy`);
    - DPoP with a refresh binding that cannot be downgraded (`dpop/ConstraintBinding`);
    - refresh-token rotation (`token/RefreshTokenFamily`);
    - the `TokenStore` SPI for a vault;
    - per-issuer configuration.
  - **Absent:**
    - a public client: `ClientAuthMethod` has no `none`;
    - a loopback redirect with any port: `ClientConfiguration.redirectUri` is one exact string;
    - RFC 8707 `resource` on the authorize and token requests (no match in `token-sheriff-client`);
    - the RFC 8628 device grant;
    - the Client ID Metadata Document, which is still a draft.

  **Operator decision (2026-11-23): option A.**
  - RFC 8707 `resource` is staged now as PLAN-11.
  - Parked until the gateway spike: the public client (`none`), the loopback redirect, the device grant
    and the Client ID Metadata Document.

  Trigger to revisit the parked items: the gateway spike (stage 1) lands, or a second native consumer
  appears.
- **Repository split-out triggers** (ADR-0032 question 12). Re-check at every release after the
  session and BFF modules first ship, and at 1.0.
  - Fires when: 3 or more releases changed only session/BFF code; under 20 % of session/BFF PRs touch
    engine paths; a dependency must stay out of validation's supply chain; main PR CI exceeds about
    20 minutes; or a distinct maintainer or security-advisory process is wanted.
  - When one fires, stage a promotion plan (move the module directories, add a new BOM).
- **Compare-and-set persist** (carried over from `api-sheriff-seams` history item 8): API Sheriff's
  refresh persist checks presence only. SESSION-3 `persist` must decide whether it compares and sets.
  Re-check at PLAN-05 outline.
