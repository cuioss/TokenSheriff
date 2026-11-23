# PLAN-01: ADR-0032 — session and BFF layers above the engine

epic: client-framework-aspects
workstream: WS-01

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Author ADR-0032 in `doc/adr/`, deciding whether Token Sheriff ships two new layers above the
confidential-client engine:

- a framework-neutral session module, `token-sheriff-session`;
- a Quarkus BFF extension, `token-sheriff-bff-quarkus`.

Base the decision on a sourced comparison with the Quarkus OIDC extension in web-app mode. Settle in the
same ADR the open questions the engine seams and both modules depend on. Then update the client docs that
today say "we do not build a BFF here", so they state the boundary the ADR draws. The plan writes
documentation only, no code.

Two inputs for the plan, both in the epic tree and readable from the main checkout:

- the proposal at `.plan/orchestrator/client-framework-aspects/sources/client_addendum.md`;
- the code-grounded corrections at `.plan/orchestrator/client-framework-aspects/sources/corpus-analysis-2026-11-23.md`.

With `orchestrator.use_worktree` on, both sit under `.plan/local/worktrees/_orchestrator/` in this
repository. They are read-only inputs; never edit them.

## Deliverables

1. **ADR-0032.** Next free number; verify `doc/adr/` holds no 0032 before naming it. Use the existing
   `NNNN-Title_with_underscores.adoc` convention. It records a decision with rationale and rejected
   alternatives for each of these questions; put each question to the operator where the evidence does
   not decide it:
   1. **Accept or reject the two modules.** Option (a): Parts B and C as proposed. Option (b): the
      Quarkus OIDC extension in web-app mode plus only the engine seams. Option (c): the session module
      only. Weigh each against both consumers' constraints.
   2. **Access token at logout.** Revoke the refresh token only (API Sheriff ADR-0080), or the refresh and
      the access token (Audit Notary). This is the rule CLIENT-33 (PLAN-04) implements.
   3. **Pending-login defaults** (CLIENT-31, PLAN-03): lifetime 5 or 10 minutes, the per-source share
      (256), the total (10,000), and the configurable limits.
   4. **Session defaults:** absolute lifetime, idle time, total bound and per-subject bound; whether the
      session cookie defaults to `SameSite=Lax` or `Strict`; whether the callback answers with a
      same-origin page or a `302`.
   5. **Token custody in a session:** through `TokenLifecycleManager`/`TokenStore` as the addendum
      proposes, or in a session-owned record as API Sheriff does. API Sheriff uses neither type.
   6. **Cookie mode.** Does API Sheriff's sealed-cookie binding (2,253 lines; ADR-0018 iii–v, ADR-0043)
      move into the session module, or stay in API Sheriff behind the SESSION-9 binding seam?
   7. **More than one instance:** a per-process store only, with a boot notice like ApiSheriff-150, or a
      shared-store SPI.
   8. **CSRF model:** `Origin` / `Sec-Fetch-Site` mandatory, with an optional anti-CSRF token or a
      mandatory one; and the logout proof.
   9. **DPoP and `private_key_jwt`:** defaults or optional in the BFF.
   10. **Names and identifiers:** module names, requirement prefixes (`SESSION-*`, `BFF-*`, or continued
       `CLIENT-*`), the documentation sets (`doc/session/`, `doc/bff/`), the log-message identifier
       ranges, and where the BFF threats live (API Sheriff's BFF-20 and BFF-21 move; API Sheriff keeps
       the gateway-specific ones).
   11. **The session-info endpoint, which is also the logged-in sentinel.** The BFF extension ships a
       configurable endpoint that the browser client polls to learn whether it is logged in and who the
       user is. The model is API Sheriff's `UserInfoEndpoint` at `oidc.user_info.path`:
       - an operator claim allowlist (`allowed_claims`) and a `default_view`;
       - session metadata (`expires_at`, `auth_time`, `acr`);
       - claims in their native JSON types;
       - `401` problem JSON when there is no session, never a redirect;
       - `Cache-Control: no-store`;
       - a poll does NOT renew the idle deadline.

       The addendum §7 left "the session view" entirely in the application. **The operator has already
       set the direction (2026-11-23): reuse the same aspect.** The generic identity and session view is
       ONE framework-neutral component, SESSION-10 in the session module, reused by API Sheriff's
       pipeline and by the BFF extension's endpoint alike. Domain content (Audit Notary's rights,
       pseudonym, first-login flag) is added through a contributor hook. The ADR records this decision
       and its rationale. What it still decides is whether the anti-CSRF token (question 8) is delivered
       through this view.
   12. **Repository placement.** The operator decided on 2026-11-23, after an adversarial review whose
       full record is `.plan/orchestrator/client-framework-aspects/sources/repo-placement-review-2026-11-23.md`.
       The decision is the SAME repository: new modules in this reactor.

       The ADR records the decision and its rationale: ADR-0001's precedent, the co-evolution with
       CLIENT-31..35, and fragile snapshot deploys. It records the strongest counter-argument as an
       accepted cost: shared versioning means a session or BFF fix forces a validation release.

       It also records split-out triggers in ADR-0001's style, with measurable thresholds, re-checked at
       1.0 or whenever one fires:
       - 3 or more releases in which only session/BFF code changed (check with
         `git log -- <module dirs>` between tags);
       - under 20 % of session/BFF PRs touching engine paths, measured after the first two releases;
       - a dependency that must stay out of validation's supply chain, such as a session-store client;
       - main PR CI above about 20 minutes after the modules land;
       - a distinct maintainer set or security-advisory process for the web-facing layer.

       Finally it records the binding mitigations (ArchUnit boundaries, opt-in artifacts with shared
       versioning stated in the BOM docs, separate threat models, CI scoping, PRs of at most 100 files).
       It also amends ADR-0001's Risks section with a cross-reference.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
2. **Quarkus OIDC comparison.** Write it as a section of the ADR, or as a document the ADR links. It
   covers session storage, the token-state manager, PKCE/PAR/DPoP support, back-channel logout, session
   bounds and fairness, CSRF, step-up and native image. Every statement about the Quarkus extension cites
   the Quarkus guide or source version it was read from.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
3. **Scope statements updated to the ADR's outcome.** `doc/client/architecture.adoc` (":22 We do not
   build a BFF here" and the BFF binding-shapes section at :101-136), `doc/client/threat-model.adoc`
   (out-of-scope note at :494-496), `doc/client/README.adoc` and `doc/README.adoc`. CLIENT-19 keeps its
   meaning: the *engine* still implements neither binding shape. Only the layer above it changes.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- ADR-0032 is merged on `main`. Each of the twelve questions carries a decision, a rationale and its
  rejected alternatives. Question 12 states its split-out triggers with measurable thresholds.
- The comparison cites a Quarkus version.
- The four scope documents no longer contradict the ADR.
- The AsciiDoc build of `doc/` is clean (no broken xrefs).
- The pull request's open Sonar read at the head lists zero new-code issues and zero security hotspots
  left to review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Documentation only: no file under any `src/` and no `pom.xml` changes.
- The ADR records decisions. It does not author the requirement texts: CLIENT-31 to CLIENT-35 belong to
  PLAN-02 to PLAN-04, and SESSION-* / BFF-* to PLAN-05 to PLAN-09.
- Quote consumer facts from `sources/corpus-analysis-2026-11-23.md` or re-read them in
  `/Users/oliver/git/API-Sheriff` and `/Users/oliver/git/Audit-Notary`. Never from the addendum alone,
  since several of its figures were refuted.
- Where the project analyses its pull requests with Sonar, an open Sonar finding is fixed in a pushed
  commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION. The question tool names each finding key. The
  progress record records the selection as `operator-selected: {issue-key}: {reason}`. The chosen key is
  transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key}
  --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker.

## Claim Labels

- OBSERVED: the highest ADR is 0031 (`doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc`), so the next number is 0032 — read at `doc/adr/` listing, 2026-11-23.
- OBSERVED: ADR-0001 keeps every capability in one multi-module repository and names the conditions for promoting a module to its own repository — read at `doc/adr/0001-Validation_and_Client_Module_Split_with_a_Commons_Base_Layer.adoc` § Decision and § Risks (orchestrator read 2026-11-23).
- OBSERVED: snapshot deploys were cancelled at the 30-minute cap from 2026-09-04, and none was published after build 47 — read at `.github/project.yml` § `snapshot-deploy-timeout` comment.
- OBSERVED: `TokenStore` is documented as the SPI a BFF plugs a persistent, shared store into — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenStore.java` § class Javadoc.
- OBSERVED: `doc/client/architecture.adoc` states "We do not build a BFF here" — read at `doc/client/architecture.adoc` § line 22 (leaf report, 2026-11-23).
- OBSERVED: CLIENT-19 requires the engine not to preclude either BFF binding shape and leaves cookies, CSRF and proxying to the application — read at `doc/client/requirements.adoc` § CLIENT-19 (lines 559-570, leaf report).
- OBSERVED: no Token Sheriff, API Sheriff or Audit Notary document weighs the Quarkus OIDC extension against a BFF of their own; Audit Notary's only mention is about its MCP gateway — read at `Audit-Notary/doc/concepts/local-mcp-gateway.adoc` § line 368 (leaf report).
- OBSERVED: API Sheriff uses neither `TokenLifecycleManager` nor `TokenStore` — read at `API-Sheriff/api-sheriff/src/main/java` (0 matches, orchestrator grep 2026-11-23).
- OBSERVED: the consumers diverge on pending-login lifetime (5 vs 10 min), logout revocation (refresh only vs refresh and access), and session cookie (`Lax` vs `Strict`) — read at `API-Sheriff/.../bff/pending/PendingAuthorizationRecord.java` § `FIXED_TTL` and `Audit-Notary/.../security/LoginTransactions.java` § line 45.
- OBSERVED: API Sheriff serves a curated identity and session view at `oidc.user_info.path`, with an operator allowlist and default view, `401` problem JSON without a session, `no-store`, and native JSON claim types — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/reserved/UserInfoEndpoint.java` § class Javadoc and `ClaimAllowlistFilter.java` (orchestrator read 2026-11-23).
- OBSERVED: polling API Sheriff's user-info path does not keep the session alive — read at `API-Sheriff/doc/user/bff-session.adoc` § line 647; the endpoint calls only `SessionBinding.resolve` — read at `UserInfoEndpoint.java` § lines 143, 191.
- OBSERVED: Audit Notary's session view `/api/v1/session` returns name, rights, CSRF token and first-login flag, and sits behind a request gate that renews the session on every call — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/web/SessionResource.java` § `session` and `web/RequestGate.java` § `session()` (orchestrator read; leaf report).
- HYPOTHESIS: the Quarkus OIDC web-app mode lacks per-source pending-login fairness and a published refresh-disposition rule — confirm/refute at the Quarkus `quarkus-oidc` source for the pinned Quarkus version § `CodeAuthenticationMechanism` (verify-at-outline).

## Expected Surface

- OBSERVED: `doc/adr/` — new file `0032-…adoc`
- OBSERVED: `doc/client/architecture.adoc`
- OBSERVED: `doc/client/threat-model.adoc`
- OBSERVED: `doc/client/README.adoc`
- OBSERVED: `doc/README.adoc`

## Dependencies and Sequencing

- Depends on: none.
- Overlaps with: none of the staged specs. PLAN-02 to PLAN-04 touch `doc/client/requirements.adoc`, not
  these files.
- Adjacent to: `doc/client/requirements.adoc` (requirement texts stay with the seam plans), and API
  Sheriff's ADRs 0018, 0043, 0046, 0058 and 0080, which the ADR cites but this plan does not edit
  (another repository).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-01-adr-session-and-bff-modules.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
