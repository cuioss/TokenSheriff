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

## Consumer hand-off notes

Nothing here is staged. Each note goes to the consumer's own repository once the named release exists.

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
- **Compare-and-set persist** (carried over from `api-sheriff-seams` history item 8): API Sheriff's
  refresh persist checks presence only. SESSION-3 `persist` must decide whether it compares and sets.
  Re-check at PLAN-05 outline.
