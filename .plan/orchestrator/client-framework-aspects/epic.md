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

- **PLAN-12 → PLAN-13 → PLAN-14 RUN FIRST** (WS-05, documentation baseline; operator decision of
  2026-11-23). They are staged last only because queue order follows staging order. Every other spec's
  `Depends on:` line names PLAN-14, so `next` cannot emit any of them earlier.
  - After PLAN-12 lands, run `cleanup` to re-ground every other spec's `## Expected Surface`, because
    documentation paths move.
  - After PLAN-14 lands, run `cleanup` again: the requirement ids the specs cite change. The new ids are
    appended after the renumbered sets, so "CLIENT-31..36" in these specs becomes "the next free ids".
  - Scope 2 buys nothing during WS-05: each of the three plans claims `doc/` and they run strictly in
    sequence.
  - **PLAN-15 shipped as #954 (2026-11-24, `0545cffd`)**, before PLAN-13: it closed PLAN-12's layer-3 findings and
    dispositioned the 23 CodeRabbit comments (16 files, no id change; the ADR set is unchanged at 17, next free
    number 0032). It shares 15 files with PLAN-13 and 10 with PLAN-14, so PLAN-13's spec names it in `Depends on:`,
    now satisfied. Order: PLAN-13 -> PLAN-14. Residual documentation defects are in Open Defects; they do not gate
    PLAN-13, which changes ids only.
  - **PLAN-12 shipped as #952 (2026-11-24, `66cecbf4`).** The ADR set is now 17 files (0001, 0003, 0006-0010,
    0012, 0014, 0015, 0017, 0022, 0024, 0026, 0029-0031; the next free number stays 0032) and four Quarkus guides are
    gone. Run `cleanup` before emitting PLAN-13 so the staged specs are re-grounded against the new layout.
- **Residuals follow the standing rule in Decisions (2026-11-24).** PLAN-03 gained deliverable 5 (the no-shim
  decision points in `retrieval-flow.adoc`) and PLAN-04 gained deliverable 6 (refresh-classification documentation;
  ADR-0010 added to its surface). **PLAN-16** is the container for the validation, benchmarking and client-discovery
  modules, 5 deliverables, `Depends on: PLAN-14`; it shares 8 files with PLAN-13 and 4 with PLAN-14, so it runs
  after them, and shares only ADR-0017 with PLAN-01 (one file, not a block). Nothing else depends on it.
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
  - PLAN-11 has 5 deliverables, under the split guard.
- 2026-11-23 — Documentation baseline, the new FIRST work (operator). It covers a full review of every
  document and diagram with the documentation skills (the operator's review prompt), a requirement
  renumbering to monotonic ids (pre-1.0; the module split had broken the order), an ADR review (decision
  versus implementation definition, and reduce), and a drastic compression of the document count.
  - **Measured at `9c242518`:** about 22,800 documentation lines (`doc/` 14,285; module docs 6,098;
    READMEs about 2,400) against about 53,000 lines of main Java. 31 ADRs total 4,982 lines.
  - **Requirement ids are out of order everywhere.** CLIENT runs 1–14, 23–25, 27, 29, 15–20, 28, 30, 21,
    22, 26. VALIDATION lacks 11 and has 12–14 before 9. COMMONS has 17 after 4 and 16 before 13.
  - **The renumbering touches about 171 files**, beyond the 100-file PR ceiling. The operator first said
    "2 PRs", then chose 3 PRs cut by prefix when asked. Rejected: 2 PRs with more than 100 files, and
    dropping requirement ids from the code.
  - **Staged as WS-05:**
    - PLAN-12: review, consolidation and ADR reduction, 5 deliverables. The operator approves the
      target structure before anything moves, and ids are frozen.
    - PLAN-13: COMMONS and VALIDATION, plus an id-integrity guard.
    - PLAN-14: CLIENT, and the guard completed.
  - All 11 earlier specs now depend on PLAN-14. The mitigations are binding in PLAN-05 (ArchUnit,
    opt-in artifact) and PLAN-08 (CI scoping, a CI-time measurement).
- 2026-11-24 — **Standing rule for residuals (operator): fold, and open a container only when nothing fits.**
  A finding, declined review comment or follow-up a landing leaves behind (layer-2 and layer-3 findings of
  `analyze`, and the follow-ups of a plan's finding message) goes into the staged plan that fits it BY CONTENT,
  as a further deliverable, and the Expected Surface is updated in the same edit. Only when the fitting plan
  would exceed 10 deliverables, or no staged plan fits, is a follow-up plan staged as the CONTAINER for the
  module(s) concerned; later residuals of those modules are appended to that container until it reaches 10
  deliverables. Details:
  - A plan that only renumbers ids (PLAN-13, PLAN-14) is never a host; "only ids change" is its constraint.
  - A residual that reverses a decision the operator accepted is a Watch, never a deliverable.
  - A layer-3 defect is still reported to the operator at once and is never fixed inline. This rule decides WHERE a
    residual goes, not WHETHER it is staged: that stays the operator's word, and the rule was given for PLAN-15's
    residuals first.
  - First application, PLAN-15's landing (`landings/PLAN-15.md`): refresh-classification documentation (the
    `RefreshFlow.classify` domain, the refusal-kind table, ADR-0010's nonce-retry conditions) folded into PLAN-04
    as deliverable 6, with ADR-0010 added to its surface; the missing no-shim decision points folded into PLAN-03
    as deliverable 5 (`retrieval-flow.adoc` was already in its surface); everything else — validation cache and
    architecture text, ADR-0017, benchmarking documentation and runner, the `DiscoveryResolver` endpoint set — no
    staged plan fits, so PLAN-16 is the container for the validation, benchmarking and client-discovery modules,
    5 deliverables, after PLAN-14. Largest staged plan now: PLAN-04, 6 deliverables.

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
  - ⚠ **Known staleness:** PLAN-13 and PLAN-14 renumber every requirement id, so that file's ids
    (CLIENT-26, -27, -29, VALIDATION-13.4/13.5 and the planned CLIENT-31..36) and its `doc/` paths will
    change. Update it from the "Former identifiers" tables once PLAN-14 lands. API Sheriff's references
    to Token Sheriff ids translate the same way.

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

- **Incomplete landing: `plan-12-documentation-review-consolidation-009.md`** (PLAN-12, 2026-11-24).
  `inbox landing-check` reported `complete: false`; missing keys: `unresolved_review_threads`,
  `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (all `unknown`).
  The landing was reconciled as far as it goes. Re-reads at analysis (`review-gate`: `undecidable_read` on the
  23-comment CodeRabbit body; Sonar `read_open_issues`: `undecidable`, `head_revision_unavailable`) are
  `unknown` as well, so the figures stay unmeasured; the Done-when "zero Sonar issues and hotspots" is not
  evidenced.
- **PLAN-12's layer-3 findings are closed by PLAN-15** (shipped as #954, `0545cffd`, 2026-11-24;
  `landings/PLAN-15.md`): the `AccessTokenCache` Javadoc, ADR-0013's general rule (restored at
  `retrieval-flow.adoc:154-159`, thinner than the ADR, see below), ADR-0010's `supersedes` line, and the advisory
  leftovers. The Sonar and review-body numbers of PLAN-12 stay `unknown` (entry above).
- **PLAN-15 residual documentation defects** (2026-11-24; `landings/PLAN-15.md`; none fixed inline; staged by the
  operator's direction under the standing residual rule in Decisions; retire each item when its plan lands):
  - Destinations: L3-1, L3-2, L3-4, L3-5 and L2-2 -> PLAN-16 (deliverables 1-3); L3-3 -> PLAN-04 (deliverable 6);
    L2-1 -> PLAN-03 (deliverable 5).
  - L3-1 `AccessTokenCache.java:44-45` and `:70` keep "raw token verification on cache hits" and "verifies token
    content on each hit", which describe only the hash-collision guard and read as a signature guarantee.
  - L3-2 `doc/validation/architecture.adoc:36`: "the window ends when the token expires or is evicted" omits maximum
    token age and DPoP or custom-rule rejection.
  - L3-3 ADR-0010 `:63-66`: the DPoP nonce retry is stated without its conditions (DPoP sender constraint, non-blank
    clean header, never after `invalid_scope`; `TokenEndpointClient.java:253-265`, `:592-599`).
  - L3-4 `doc/validation/architecture.adoc:262` (and the `maxPayloadSize` table row): "bounds token parts only"
    contradicts `security-settings.adoc:56`.
  - L3-5 ADR-0017 `:21-27`: one run-on em-dash sentence.
  - L2-1 `retrieval-flow.adoc:154-159` omits two decision points of the deleted ADR-0013: "source compatibility is
    not the acceptance test" and "a deprecation window only dates the exposure".
  - L2-2 `benchmarking/doc/local-testing.adoc:28-33`: the new path sits below a Quick Start that already does
    `cd benchmarking/scripts`.
  - Not shown by reading: the Asciidoctor render of the `token-type-validation-rfc-9068` anchor.
- **PLAN-15 finding `-001`, archived** (declined review comments and code-side follow-ups; the decline was
  procedural, because the files lie outside PLAN-15's Expected Surface, and each still applies at HEAD).
  Destinations: `token-handling.adoc` x2 -> PLAN-04 (deliverable 6); `performance-scoring.adoc:136`,
  `benchmarking/README.adoc:43`, `JfrBenchmarkRunner` and `DiscoveryResolver` -> PLAN-16 (deliverables 3-5); the
  ADR-0031 item -> Watch below:
  - `doc/client/specification/token-handling.adoc:114-115` (verifier refusals belong in the documented domain of
    `RefreshFlow.classify`; line 114 still says "exactly two sources") and `:118-120` (the refusal-kind table
    hardcodes four kinds with no control against `RefreshFailureClassification.Kind`).
  - `benchmarking/doc/performance-scoring.adoc:136` (the claim that one anomalous run does not flip the trend).
  - `benchmarking/README.adoc:43` (`serve-reports.sh library` shown as the way to view `benchmark-jfr` results; the
    script has no JFR target).
  - Product-code follow-ups, never hosted by PLAN-13 or PLAN-14: the ADR-0031
    deployment scope check evaluates a default method's scopes alone, not the merged set; `JfrBenchmarkRunner
    .createConfiguration()` overrides the `jmh.*` values with fixed ones; `DiscoveryResolver.validateEndpointSchemes`
    lists the HTTPS-checked endpoints by hand.
- **Twelve candidate-lesson messages held in `inbox/`**, undispositioned: PLAN-12's `-001` to `-005`, `-007`, `-008`
  and PLAN-15's `plan-15-plan-12-residual-fixes-002` to `-006`. Filing `plan-marshall:*` or other bundle-prefixed
  lessons into this repository's store is refused (`wrong_store`); the `--allow-foreign-store` override was not used
  without the operator's say-so, and a bundle prefix was not stripped to get past the guard. Drafts for the PLAN-12
  ones are in `.plan/temp/lesson-*.md`; the PLAN-15 messages already carry the corpus body shape.
  PLAN-12 `-006` was folded into lesson `2026-11-21-13-003` as a recurrence and is not held. PLAN-15 `-005` record 3
  independently confirms the `--changed-paths-file` drift of PLAN-12 `-007`.

## Watches

- **ADR-0031 deployment scope check on interface default methods** (2026-11-24, from PLAN-15's finding `-001`): the
  check evaluates a default method's scopes on their own, so a merged set (class-level `@BearerAuth` plus the
  inherited default method) over the 64-scope limit fails at request time with a 500 instead of at deployment.
  `doc/adr/0031-...adoc:48` records this limit as ACCEPTED BY THE OPERATOR, so extending the check reverses an
  accepted decision and is not a residual: it is not staged. Re-open only if the operator reverses the limit;
  then it belongs to a plan for `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/`.
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
- **Java baseline stays 21** (operator question, 2026-11-23).
  - **Why:** nifi-extensions uses `token-sheriff-validation` (plus its `generators` test-jar) and runs
    inside NiFi. The official `apache/nifi` image ships JDK 21: `docker run apache/nifi:2.8.0 java
    -version` gives 21.0.10 (BellSoft), and the Docker Hub 2.10.0 layer shows 21.0.11. A validation jar
    built for Java 25 (class-file version 69) would fail to load in NiFi with
    `UnsupportedClassVersionError`. Apache NiFi documents Java 21 as its minimum, and Java 25 is only in
    CI work (NIFI-14981).
  - **Not a reason to move:** Token Sheriff's JDK-specific test branches split 21/25 from 27, because
    the JDK `HttpClient` changed in 27 (`BackChannelHttpMalformedResponseTest:133`,
    `BackChannelHttpBodyPhaseTest:104`). They depend on the runtime and would survive a baseline change.
  - **Consequence for PLAN-05 to PLAN-07:** extracted API Sheriff code that uses the unnamed variable
    `_` (Java 22+) is rewritten with named variables.
  - **Trigger to revisit:** NiFi's system requirements list Java 25, or the `apache/nifi` image ships
    JDK 25, or nifi-extensions stops consuming Token Sheriff.
- **Repository split-out triggers** (ADR-0032 question 12). Re-check at every release after the
  session and BFF modules first ship, and at 1.0.
  - Fires when: 3 or more releases changed only session/BFF code; under 20 % of session/BFF PRs touch
    engine paths; a dependency must stay out of validation's supply chain; main PR CI exceeds about
    20 minutes; or a distinct maintainer or security-advisory process is wanted.
  - When one fires, stage a promotion plan (move the module directories, add a new BOM).
- **Compare-and-set persist** (carried over from `api-sheriff-seams` history item 8): API Sheriff's
  refresh persist checks presence only. SESSION-3 `persist` must decide whether it compares and sets.
  Re-check at PLAN-05 outline.
