# PLAN-02: Client authorization-request seam

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-02-client-authorization-request-seam.md` and is queued as one row file, `queue/PLAN-02.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Give `token-sheriff-client` the seams a gateway needs to drive the authorization request and the refresh
without re-implementing the engine's request building: the authorization parameters as a map instead of only
a finished URL, an opt-in `response_mode`, an opt-in `prompt` parameter, an optional pushed (PAR) request path,
and a per-call scope override for authorize and refresh. Today API Sheriff rewrites the engine's finished URL
string, parses it back into a map to push it, and builds a fresh `ClientConfiguration` plus flow per scope set
just to change the requested scope. Every new behaviour is opt-in; with nothing configured the engine emits
the same `response_mode=form_post` request and the same refresh form it emits today.

## Deliverables

1. **Authorization parameters as a map.** `AuthorizationRequestBuilder` publishes the validated request
   parameters (everything `build` puts into the query: `response_type`, `client_id`, `redirect_uri`,
   `scope`, `state`, `nonce`, the PKCE pair, `response_mode`, `acr_values`, `max_age`, and the new optional
   parameters) as an immutable, ordered map, and `build` is that map form-encoded onto the endpoint. The same
   checks run for both: TLS authorization endpoint, advertised PKCE `S256`, present authorization endpoint.
   Verification, one criterion per element of what a caller sees: (a) the map holds exactly the parameter
   names `build` emits, with identical values; (b) `build(...)` output is byte-identical to today's for every
   existing `AuthorizationRequestBuilderTest` fixture; (c) an absent optional parameter is absent from the
   map (no empty value); (d) the returned map cannot be modified; (e) a missing authorization endpoint, a
   non-TLS endpoint and a missing `S256` each raise the same exception type and message as `build` today.
2. **Opt-in `response_mode`.** The builder can request `query` in addition to the default `form_post`. The
   value is a closed set (an enum or a validated literal set), never a free string, so a caller cannot inject
   a delimiter or a second parameter. Verification: the default remains `form_post`
   (`AuthorizationRequestBuilderTest` assertion at its `response_mode=form_post` test unchanged); `query`
   yields `response_mode=query`; an unsupported, blank or delimiter-bearing value (`&`, `#`, CR/LF, space,
   a second `=`) is refused at configuration or call time, never emitted. The documentation states that
   `query` places `code`, `state` and `iss` in the redirect URL and re-opens `T-URL-LEAK`, and that the caller
   owns the mitigation.
3. **Opt-in `prompt`.** An authorization request can carry `prompt` with a closed value set. Verification: an
   absent `prompt` leaves the request unchanged; a supported value appears exactly once; an unsupported or
   delimiter-bearing value is refused; `prompt` already present from another source is never duplicated (the
   map form makes a duplicate impossible, and a test asserts it).
4. **Per-call scope override on authorize and refresh.** `authorize` and `RefreshFlow.refresh` accept a
   per-call scope collection that replaces `ClientConfiguration.getScopes()` for that call only, without
   building a new configuration. Verification: (a) the override is validated by the same rules as
   `ClientConfiguration` scope validation (no `null`, no blank, one token per entry); (b) on refresh, the
   scope-reconciliation baseline (`classifyScopeDelta`, `reportScopeDelta`) uses the per-call scopes, not the
   configured ones, so a narrowed or broadened grant is judged against what was actually requested, and the
   strict posture refuses a broadened grant against the per-call baseline; (c) an absent override leaves
   today's behaviour (configured scopes) byte-for-byte; (d) an empty override is either refused or treated as
   "no scope parameter" as decided at outline, with a test for whichever is chosen; (e) the configured
   `ClientConfiguration` is not mutated and two concurrent calls with different scopes do not interfere.
5. **Pushed authorization request path (decision-gated, see Operator decisions).** If the operator approves
   it: a flow method that builds the parameters, pushes them with `ParClient.pushAuthorizationRequest`, and
   returns the authorization endpoint with exactly `client_id` and `request_uri`, together with the
   `FlowContext`. Verification: the redirect carries no other parameter (no `state`, `nonce`, PKCE, `scope`,
   `response_mode`, `prompt`; they travel only in the pushed body); an authorization server that advertises no
   PAR endpoint is refused with a typed exception and never silently falls back to a non-pushed request; a
   PAR response without a `request_uri` is refused (existing `ParClient` behaviour, asserted through the new
   path); the pushed parameter map equals the deliverable-1 map. If the operator declines it, this deliverable
   is dropped and the plan ships deliverables 1-4 and 6 only.
6. **Documentation and requirement text.** One requirement or amendment records the new seams. Verification:
   the requirement id is the next free `CLIENT-N` at write time (or an amendment of the existing client
   requirement that owns the text); `doc/client/requirements.adoc` CLIENT-10 is rewritten if deliverable 5
   ships (it currently states the built-in flow does not use PAR) and left unchanged otherwise;
   `doc/client/specification/retrieval-flow.adoc` (the parameter table naming `response_mode`),
   `doc/client/threat-model.adoc` (`T-URL-LEAK`) and `doc/client/specification/test-strategy.adoc` are updated
   to match.

Done when: all criteria pass, the module's refresh-path coverage gate (`RefreshPathCoverageGuardTest`, the
per-class floor on `RefreshFlow`) still passes with the new refresh overload covered, the full
`-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green, and the Sonar open-issue read at the PR
head is `0` or every remaining issue is dismissed with a recorded reason (`unknown` for a failed read, never
`0`).

**Operator decisions needed at outline**

These are not guessed here. The outline must put each to the operator and record the answer as an epic
decision before scoping.

- **Which `response_mode` values to support.** API Sheriff needs `query`. Candidates: `form_post` (default,
  unchanged) plus `query`; `fragment` (never reaches a server-side callback for a confidential client, so
  it has no consumer here); the JARM family (`query.jwt`, `form_post.jwt`), which would need response-JWT
  verification TokenSheriff does not have. The spec's default proposal is `form_post` and `query` only, with
  the closed-set check of deliverable 2.
- **Which `prompt` values to support.** API Sheriff needs `none`. Candidates: `none` only, or the full
  OpenID Connect set (`none`, `login`, `consent`, `select_account`).
- **Pushed request path: ship it, or publish only the parameter map.** The map of deliverable 1 plus the
  existing `ParClient` is enough for a caller to compose PAR itself, which is what CLIENT-10 states today
  ("a caller that needs PAR composes the PAR client itself"). Deliverable 5 changes that documented posture
  in exchange for removing the caller's push-and-rebuild code. The operator decides whether TokenSheriff owns
  the pushed path.
- **Where the per-call options live.** Per-call overloads on the flows (`authorize(metadata, scopes)`,
  `refresh(metadata, token, scopes)`, the finding's proposal), or a `ClientConfiguration` copy-with
  (`toBuilder`) so the caller varies a configuration and keeps the flows as they are. The `refresh` overload
  interacts with the overridability constraint below.
- **Empty per-call scope override**: refuse, or mean "send no `scope` parameter".

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments.
- **Default behaviour stays unchanged and is proven so.** With no new option set, `authorize` and `refresh`
  emit exactly today's request. Proof: the unchanged existing assertions of `AuthorizationRequestBuilderTest`,
  `AuthorizationCodeFlowTest`, `RefreshFlowTest` and `ParClientTest` pass without edit, plus one
  golden-URL / golden-form test per flow that fixes the byte-exact default output.
- **Overridability (AGENTS.md "Preferred overloads", ADR-0006).** `AuthorizationRequestBuilder` is a
  non-final public class whose public `build` is overridden by API Sheriff
  (`QueryResponseModeAuthorizationRequestBuilder`); `RefreshFlow` is subclassed by test doubles
  (`ParkingRefreshFlow`, `ScopeRefusingRefreshFlow`). `AuthorizationCodeFlow.authorize(metadata)` must keep
  calling `build(...)`, and `TokenLifecycleManager` must keep calling `refresh(metadata, refreshToken)`;
  neither may be re-routed through a new overridable method, or an overrider's override silently stops being
  called. A new overload that production code would prefer needs the two questions of that section answered
  in the PR (is the existing method overridable, does anything override it) before it is added.
- Security: every new input (response mode, prompt, scope collection) is validated against a closed set or
  the existing scope rules before it reaches a URL or a form body, and is encoded through `FormEncoder`.
  No attacker-influenced value is logged unsanitised (`LogSanitizer`); logging follows `AGENTS.md` (`%s`
  only, `LogRecord` for INFO/WARN/ERROR, new identifiers the next free in range at write time, documented in
  `doc/LogMessages.adoc`). Adversarial tests are required: delimiter, CR/LF and duplicate-parameter
  injection through the response mode, prompt and scope inputs; an oversize scope list; scope values with
  embedded whitespace.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest; parameterized for three or more similar variants;
  attack-database style for the injection inputs, in the manner of the existing
  `CallbackParametersAttackDatabaseTest`; new or grown test classes at or under 500 lines.
- If a new field is added to `ClientConfiguration`, its Quarkus exposure (`sheriff.client.*`,
  `ClientRuntimeConfig`, `ClientConfigMapper`, `quarkus-config-doc.adoc`) is decided at outline; an
  unexposed field must not leave an orphaned property (ADR-0026). Absent configuration means today's
  default.
- No new dependency without operator approval. Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`, `AGENTS.md` names the SonarCloud
  new-code gate the coverage authority): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.

## Claim Labels

- OBSERVED: `AuthorizationRequestBuilder.build` returns a URL string only, hard-codes
  `response_mode=form_post` (`params.put(PARAM_RESPONSE_MODE, RESPONSE_MODE_FORM_POST)` at line 115; the
  findings' ":70, :85" cites the constants and the method header, which sits at line 85), and reads the scope
  only from `configuration.getScopes()` (lines 108-110) — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`
  § `build`
- OBSERVED: `AuthorizationRequestBuilder` is `public class` (not final) and `build` is its only public
  method — read at the same file § `AuthorizationRequestBuilder`
- OBSERVED: `AuthorizationCodeFlow` has one entry point for authorization, `authorize(ProviderMetadata)`
  (:154), which mints `FlowContext.create(redirectUri)` and calls `authorizationRequestBuilder.build(...)`; it
  has no PAR path and no `ParClient` reference — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java` §
  `authorize`. Asserted absence verified by the constructor and field lists (:65-143), which hold no
  `ParClient`
- OBSERVED: `ParClient.pushAuthorizationRequest(String parEndpoint, Map<String,String>,
  ClientAuthentication)` exists and returns a `ParResponse` whose `request_uri` is checked present — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java` §
  `pushAuthorizationRequest`, `parse`
- OBSERVED: `RefreshFlow.refresh(ProviderMetadata, String)` (:196) puts `scope` from
  `configuration.getScopes()` into the form (:208-210), and the same configured list is also the baseline of
  the scope reconciliation (`classifyScopeDelta` :354, `reportScopeDelta` :395). The findings cite only the
  form site (~:208); the two reconciliation sites are additional and are why a form-only override would be
  wrong — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`
  § `refresh`, `classifyScopeDelta`, `reportScopeDelta`
- OBSERVED: `ClientConfiguration` is a Lombok `@Value @Builder` with no `toBuilder` (no occurrence of
  `toBuilder` in the file) and validates scopes in `validateScopes` (null, blank and multi-token entries are
  refused) — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java` §
  `validateScopes`, class-level `@Value @Builder`
- OBSERVED: the consumer rewrites the finished URL string — `QueryResponseModeAuthorizationRequestBuilder`
  extends `AuthorizationRequestBuilder`, overrides `build` and rewrites `response_mode` to `query` on the URL
  (178 lines, most of it Javadoc) — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/QueryResponseModeAuthorizationRequestBuilder.java`
  § `build`, `withQueryResponseMode`
- OBSERVED: `PushedAuthorizationRequests.push` splits the finished URL back into a map
  (`parametersOf`, refusing duplicate or undecodable pairs), calls `ParClient.pushAuthorizationRequest`,
  and builds `endpoint?client_id=...&request_uri=...`; a provider without a PAR endpoint is refused
  (`Reason.NO_PAR_ENDPOINT`), not downgraded — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/PushedAuthorizationRequests.java` § `push`,
  `parametersOf` (231 lines in total; the parse-and-push body is lines 123-200 as the findings say)
- OBSERVED: `ScopedEngineFlows` (295 lines) builds a `ClientConfiguration` through a factory function plus a
  new `AuthorizationCodeFlow` or `RefreshFlow` per canonical scope set, caches the authorization flows in a
  `ConcurrentMap`, and appends `prompt=none` to the finished URL by string rewriting in `withPromptNone` — read
  at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/ScopedEngineFlows.java` § `authorize`,
  `widen`, `refresh`, `withPromptNone`
- OBSERVED: two test doubles subclass `RefreshFlow` — `ParkingRefreshFlow` and `ScopeRefusingRefreshFlow` —
  so `refresh` is overridden in the TokenSheriff test tree; read by search for `extends RefreshFlow` and
  confirmed at
  `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/RefreshSingleFlightSpecIT.java`:161
  and `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerApplyRefreshTest.java`:288;
  `ScopeRefusingRefreshFlow` overrides the two-argument `refresh(ProviderMetadata, String)` (:297-298), and
  `TokenLifecycleManager` calls exactly that method (`refreshFlow.refresh(metadata, presentedRefreshToken)`,
  :843) — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`
  § the refresh call site. What `ParkingRefreshFlow` overrides was not read
- OBSERVED: `doc/client/requirements.adoc` CLIENT-10 states that the built-in authorization-code flow does not
  use PAR and that a caller composes the PAR client itself (:183-194), and CLIENT-1 / `T-URL-LEAK` name
  `form_post` as the mitigation — read at `doc/client/requirements.adoc` § `CLIENT-10`, `CLIENT-1` and
  `doc/client/threat-model.adoc` § `T-URL-LEAK`
- OBSERVED: `FlowContext` is a final class with a private constructor and three `create` factories (`acrValues`
  and `maxAge` ride on it, set at creation); a consumer cannot construct one directly — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java` § `create`
- HYPOTHESIS: API Sheriff's Lax binding-cookie design depends on `response_mode=query` (the findings' claim),
  so the default `form_post` must stay the unchanged TokenSheriff default and `query` must be reachable by the
  consumer without subclassing — confirm/refute at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/QueryResponseModeAuthorizationRequestBuilder.java`
  § class Javadoc and the callback binding code under `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/pending/`
  (verify-at-outline)
- HYPOTHESIS: the engine's callback side (`CallbackHandler`, `CallbackParameters`, `IssValidator`) is
  indifferent to how the callback parameters arrived, so a `query` response needs no engine-side callback
  change — confirm/refute at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CallbackHandler.java` § `handle` and
  `.../flow/CallbackParameters.java` § parsing entry points (verify-at-outline)
- HYPOTHESIS: the PAR request must not carry `request_uri`-incompatible parameters and the authorization
  server may require `client_id` in the redirect only; RFC 9126 §4 governs — confirm against the real
  authorization-server fixture of `ParSpecIT` at
  `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/ParSpecIT.java`
  (verify-at-outline)
- OBSERVED: `StepUpHandler` holds an `AuthorizationRequestBuilder` (default-constructed or injected) — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpHandler.java` § constructors
  (:41-59)
- HYPOTHESIS: `StepUpHandler` therefore emits authorization requests through the same builder and inherits
  whatever `response_mode` default or option this plan adds; whether it should honour the opt-in mode was not
  read — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpHandler.java`
  § the method that calls the builder (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) which TokenSheriff
  components call `AuthorizationRequestBuilder.build` and `RefreshFlow.refresh` (re-run the caller and
  subclass search at HEAD, including tests, because a new overload that is preferred over an overridden
  method is a behavioural break for the overrider), (3) whether any other component threads configured
  scopes (a search of `getScopes()` in `token-sheriff-client/src/main` found only `RefreshFlow`,
  `AuthorizationRequestBuilder` and `ClientCredentialsFlow` outside the config class; `TokenLifecycleManager`
  has no `getScopes()` call), and (4) the line numbers above against HEAD. A refutation loops
  back to re-scope.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`:85 — `build`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java`:154 — `authorize`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`:196 — `refresh`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`:354 — `classifyScopeDelta`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpHandler.java`:59 — `StepUpHandler(AuthorizationRequestBuilder)` (read; changed only if step-up honours the new option)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java`:96 — `pushAuthorizationRequest` (read; changed only if the pushed path needs it)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java`:108 — `create` (the `prompt` value may ride here, following `acrValues` and `maxAge`)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`:255 — `strictScopeReconciliation` (precedent for a new opt-in field; `toBuilder` or a response-mode field would land here)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/EndpointQuery.java`:1 — `EndpointQuery` (read; used to append the query before a `#fragment`)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/FormEncoder.java`:1 — `FormEncoder` (read; encodes every new value)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilderTest.java`:113 — `response_mode=form_post` test (must stay green unedited)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlowTest.java`:1 — `AuthorizationCodeFlowTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowTest.java`:1 — `RefreshFlowTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/ParClientTest.java`:1 — `ParClientTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/RefreshPathCoverageGuardTest.java`:1 — `RefreshPathCoverageGuardTest` (refresh-path coverage gate the new overload must satisfy)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java`:1 — `ClientArchitectureTest` (ArchUnit rules over the new public surface)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerApplyRefreshTest.java`:288 — `ScopeRefusingRefreshFlow` (an existing `RefreshFlow` subclass; must keep dispatching)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/ParSpecIT.java`:1 — `ParSpecIT` (real-IdP PAR fixture)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/AuthCodePkceSpecIT.java`:1 — `AuthCodePkceSpecIT`
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/` — new test classes for the response-mode/prompt injection attack set and the per-call scope override (to be created; names chosen at outline) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientConfigMapper.java`:89 — `strictScopeReconciliation` mapping (touched only if a new `ClientConfiguration` field is exposed as `sheriff.client.*`) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/config/ClientRuntimeConfig.java`:124 — `STRICT_SCOPE_RECONCILIATION` (same condition) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc`:35 — `sheriff.client.strict-scope-reconciliation` row (same condition) (verify-at-outline)
- OBSERVED: `doc/client/requirements.adoc`:183 — `CLIENT-10` (rewritten if the pushed path ships)
- OBSERVED: `doc/client/README.adoc`:52 — the Document map states the requirement range (`CLIENT-1`–`CLIENT-26` after PLAN-01 landed) and :79 names the covered requirements in the Test strategy row; both are updated in the same deliverable that adds a new `CLIENT-N` (folded from inbox message client-native-image-registration-001: a new requirement id makes every restated range stale)
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`:43 — the `response_mode` row of the request parameter table
- OBSERVED: `doc/client/threat-model.adoc`:165 — `T-URL-LEAK`
- OBSERVED: `doc/client/specification/test-strategy.adoc`:343 — the CLIENT-1 / `T-URL-LEAK` row
- OBSERVED: `doc/LogMessages.adoc`:1 — only if a new LogRecord is added

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`).
- `parallelization_scope` is 1: plans of this epic run sequentially, one at a time, in ordinal order. This
  plan runs after PLAN-01.
- Depends on: PLAN-01 (order only; no code dependency).
- Overlaps with: PLAN-03 and PLAN-04, all three in `token-sheriff-client`. PLAN-04 and this plan both edit
  `RefreshFlow` (PLAN-04 adds the refresh-result verifier around the validated result; this plan adds the
  per-call scope overload and the reconciliation baseline), so whichever runs second rebases on the other's
  shape; the same two plans also share `RefreshFlowTest`, `TokenLifecycleManagerApplyRefreshTest` and the
  refresh-path coverage gate. PLAN-03 shares `ClientConfiguration` (its validators) and
  `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc`. Requirement ids are taken
  as next free at write time by each plan, never pre-assigned.
- Adjacent to: `ClientCredentialsFlow` also reads `configuration.getScopes()` (:127) and is deliberately
  left unchanged; `StepUpHandler` (which owns an `AuthorizationRequestBuilder`) is changed only if the outline
  decides step-up honours the new `response_mode` option.
- API Sheriff's matching deletion (`QueryResponseModeAuthorizationRequestBuilder`, the URL round-trip in
  `PushedAuthorizationRequests`, and the per-scope-set configuration factories of `ScopedEngineFlows`) is its
  own plan, AS PLAN-34, which waits for a released 0.9.10. It is NOT part of this spec.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-02-client-authorization-request-seam.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
