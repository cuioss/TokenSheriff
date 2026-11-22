# Landing Analysis: PLAN-02 — Client authorization-request seam

epic: api-sheriff-seams
workstream: WS-01
pr: #901 (https://github.com/cuioss/TokenSheriff/pull/901), merged as a60a9a484d79e804babb7a8839b94511db2b98d8

> Landing record for one shipped plan. Lives at `landings/PLAN-02.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `client-authorization-request-seam-007.md` (lane `plan-marshall`, plan id
`client-authorization-request-seam`), plus six candidate-lesson messages (`-001` to `-006`). PR state read through
`ci pr view` (`state: merged`, merge commit as above, base `main`). `inbox landing-check`: `complete: false` (missing
`unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, all `unknown` in the landing:
the plan's manifest has no `review-gate` step); `surface_delta` is `unmeasured` (the landing supplied neither the
declared nor the realized surface), so the declared-vs-realized comparison below was made by hand from the 27-file merge
diff.

## Deliverable Fidelity vs Spec

The spec lists six deliverables; the landing reports `deliverables_total=5`, `deliverables_done=5`, which the spec does not
bear out (the outline may have regrouped them, and it was not read). Every one of the spec's six traces to code and a test.
The five operator decisions at outline were all taken, and the diff shows how.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Authorization parameters as a map | shipped-as-specified | `AuthorizationRequestBuilder.parameters:154-159` returns `assemble(...)`, an unmodifiable `LinkedHashMap` (`:197-221`); `build` form-encodes the same map (`:100-105`, `:125-130`); the checks are shared in `validatedEndpoint:166-186`. |
| 2. Opt-in `response_mode` | shipped-as-specified | `ResponseMode` closed enum `{FORM_POST, QUERY}`, default `FORM_POST`; `of(String)` exact match. Operator decision "form_post and query only" honoured. |
| 3. Opt-in `prompt` | shipped-modified | `Prompt` closed enum `{none, login, consent, select_account}` — the full OIDC set, not `none` only (the spec's candidate list; an operator decision). Emitted at most once (`:216-219`). |
| 4. Per-call scope override | shipped-modified | `AuthorizationRequestOptions` (scopes validated at construction) and `RefreshFlow.refresh(metadata, token, scopes):236`; reconciliation baseline is the per-call list through the new private `redeem:250`. The empty override is **refused** in both entry points (`RefreshFlow:238-241`). Per-call overloads, not `toBuilder`. |
| 5. Pushed authorization request path | shipped-as-specified (operator approved) | `AuthorizationCodeFlow.authorizePushed:235-256`: PAR endpoint read first and refused before any request (`:240-242`), pushed map is `parameters()` unmodified, redirect carries exactly `client_id` and `request_uri` (`:251-254`). |
| 6. Documentation and requirement text | shipped-as-specified, with two unflagged consequences | `CLIENT-27` added, `CLIENT-10` rewritten, `retrieval-flow.adoc`, `threat-model.adoc`, `test-strategy.adoc`, both READMEs updated. See L2-3 and L2-4. |

Added unplanned or outside the Expected Surface (in the 27-file diff, not declared): `doc/client/architecture.adoc`,
`doc/client/best-practices.adoc`, `doc/developer/test-impl-mapping.adoc`, `token-sheriff-client/README.adoc`
(consistency follow-ups to the pushed path and `CLIENT-27`, justified), `ClientConfiguration.java` and
`ClientConfigurationTest.java` (the `validateScopes` rewrite, explained only by the CodeRabbit thread, not by the spec — L2-1),
`WireValues.java`, `ResponseMode`, `Prompt`, `AuthorizationRequestOptions` (the closed sets and per-call options, planned at
outline), and seven new test classes plus `PushedAuthorizationFlowSpecIT`. Declared but untouched: `ParClient`, `FlowContext`,
`EndpointQuery`, `FormEncoder`, `ClientConfigMapper`, `ClientRuntimeConfig`, `quarkus-config-doc.adoc`, `doc/LogMessages.adoc`
(correct: no new field, no new `LogRecord`).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze`) — reason: reading merged code is the depth the Dispatch Decision Rule reserves for a leaf, and this landing's read shares no context with any other.
- The orchestrator re-read these `checked` points at their file:line on the merge commit before recording them: `ClientConfiguration.validateScopes:536-621` and the pre-merge version at `a60a9a48^1` (L2-1, L3-3), `AuthorizationCodeFlow:170-264` (L2-5, ADR-0006, the pushed path), `AuthorizationRequestBuilder:96-222` (L2-5, L3-5, D1), `RefreshFlow:196-265` (D4, `@since`, `redeem`), the line counts of `ClientConfigurationTest` (904, was 811), the raw U+00A0 positions (`ClientConfigurationTest:666`, `AuthorizationRequestOptionsTest:102`), the missing `@since` tags, the history-framed wording search, `requirements.adoc` CLIENT-14 old and new text, the `Security exclusions` row at `:561-563` and the traceability row order at `:676-684`, and the `review-gate` and Sonar re-reads. The remaining `checked` points — the golden-output hash-order recomputation, the concurrency tests, the injection attack-database test, `FormEncoder` and `EndpointQuery`, `StepUpHandler`, the `ParkingRefreshFlow` override, `ParClient.parse`, `RefreshFlow:444-450` and the README lines — are carried as the leaf's reading and were not individually re-read.
- **Verdict:** layer 1 findings (7, none above minor); layer 2 findings (all minor or info; two unrequested behaviour changes, L2-1 and L2-4); **layer 3 findings (4 minor, the rest info), no resource-exhaustion or injection defect**, and not shown by reading (2). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The Standing Constraint "never gate on `test-compile`" rests on a premise main no longer holds (`AGENTS.md:118-131`: the fixtures resolve in the reactor through `<type>test-jar</type>`). Already on the Watch list from PLAN-05; the lane's own residue (lesson 2026-11-13-06-005 recurrence) shows the tooling workaround behind it. | checked | refutes | `AGENTS.md:118-131`; `token-sheriff-client/pom.xml:143` |
| L1-2 | The spec's line anchors held at `a60a9a48^1`; at the merge `RefreshFlow.refresh` is `:203`, `classifyScopeDelta` `:408`, `reportScopeDelta` `:444`. | checked | confirms | `RefreshFlow.java:203` (re-read) |
| L1-3 | "What `ParkingRefreshFlow` overrides was not read" in the spec: it overrides the 2-arg `refresh` and calls `super` of the same signature, like `ScopeRefusingRefreshFlow`. Both doubles keep working only because the shipped code left the 2-arg method as the lifecycle entry point. | checked | confirms | `RefreshSingleFlightSpecIT.java:175-182` (leaf's reading) |
| L1-4 | "Next free `CLIENT-N`" held: `CLIENT-26` was the last id, `CLIENT-27` is correct, and `README.adoc:52,79` (folded from PLAN-01) were both updated. | checked | confirms | `requirements.adoc:411` |
| L1-5 | The spec asks for "a typed exception" for a missing PAR endpoint without fixing the type; the shipped refusal is the generic `ClientProtocolException` with a message, so a caller cannot tell it from the S256 or non-TLS refusals except by text (API Sheriff had `Reason.NO_PAR_ENDPOINT`). The spec left this under-determined. | checked | refutes | `AuthorizationCodeFlow.java:240-242` (re-read); `requirements.adoc:193-203` |
| L1-6 | The spec says CLIENT-10 is rewritten and the docs "updated to match" but never says `CLIENT-14` or the `Security exclusions` table must change; the opt-in `query` mode contradicts both (L2-3, L2-4). An unanticipated consequence of deliverable 2. | checked | refutes | `requirements.adoc:264-275`, `:561-563` (re-read) |
| L1-7 | The Standing Constraint "test classes at or under 500 lines" is stricter than `AGENTS.md:172-180` (a guideline; the PR body gives the reason). | checked | refutes | `AGENTS.md:172-180` |

No verdict is persisted onto a spec by this step: L1-1 is a Standing Constraint, not a labelled claim; L1-2 changes line anchors in
the staged PLAN-03 and PLAN-04, which is recorded as a queue annotation, not a claim verdict (see Routing and Reconciliation).

**Layer 1 verdict:** findings — 7 (L1-1..L1-7); not shown by reading — 0

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `AuthorizationRequestBuilder.parameters:154`, `assemble:197` | `AuthorizationRequestBuilderParametersTest` (names, values, order, absence, immutability, exception equality) | none | checked |
| 2 | `ResponseMode`, `AuthorizationRequestOptions` | `AuthorizationRequestOptionsTest`, `AuthorizationRequestInjectionAttackDatabaseTest` | none | checked |
| 3 | `Prompt`, `assemble:216-219` | `ParametersTest` (once, absent), `OptionsTest`, injection DB | none | checked |
| 4 | `AuthorizationRequestOptions`, `RefreshFlow.refresh:236`, `redeem:250` | `RefreshFlowScopeOverrideTest` (baseline, WARN text, strict refusal and acceptance, no mutation, a latch-rendezvous concurrency test) | none | checked |
| 5 | `AuthorizationCodeFlow.authorizePushed:235` | `AuthorizationCodeFlowPushedTest` (redirect equals exactly two keys, body equals `parameters()`, no PAR endpoint refused with zero server requests) | none | checked |
| 6 | `requirements.adoc` CLIENT-10/-27, `retrieval-flow.adoc:43`, `threat-model.adoc`, `test-strategy.adoc` | n/a | `CLIENT-14` weakened, security-exclusions row stale (L2-3, L2-4) | checked |

- **L2-1 — unrequested behaviour change in existing validation.** `ClientConfiguration.validateScopes` was widened from "no `Character.isWhitespace` code point" (`a60a9a48^1`) to the RFC 6749 §3.3 set `%x21 / %x23-5B / %x5D-7E` (`:576-620`, re-read) and made `public static`. A configured scope holding NUL, DEL, other controls, a double quote, a backslash, U+00A0 or any non-ASCII character now fails `ClientConfiguration.build`; it was accepted before. The message no longer echoes the entry. The change is strictly narrowing, so safe, and it is the CodeRabbit fix; it is beyond spec D4(a) ("same rules") and the lane's advisory list does not name it. A consumer with an unusual configured scope will see a new build-time refusal on the 0.9.10 bump.
- **L2-2 — test size.** `ClientConfigurationTest` grew from 811 to 904 lines (it was already over 500); the spec's constraint says grown classes stay at or under 500, `AGENTS.md:175-177` asks for the reason in the PR body (third-party text, not read) and a sibling topic class. All eight new test classes are within 500 (largest `RefreshFlowScopeOverrideTest` 422).
- **L2-3 — normative self-contradiction.** `requirements.adoc` `Security exclusions` (`:531-563`) still lists "Tokens / codes in URL query or fragment — Use `form_post`" as something the engine MUST NOT require or implement, while `CLIENT-27` (`:411`) and the code ship an opt-in `response_mode=query`. Docs only.
- **L2-4 — `CLIENT-14` weakened.** The requirement read "Tokens **and authorization codes** MUST NOT be transmitted in URL query or fragment components; … the authorization response uses `form_post`" (`a60a9a48^1`); it now reads "Tokens MUST NOT …; `form_post` by default; `response_mode=query` is an explicit per-call opt-in" (`:264-275`, re-read). The weakening is explicit and cross-referenced to `CLIENT-27`/`T-URL-LEAK`, but it is a normative change the spec did not call for.
- **L2-5 — the narrative's advisories, all confirmed in the merged code, re-read where marked.**
  (1) the security-exclusions table (L2-3); (2) the 4-arg `build` Javadoc says "identical to `build(3-arg)`" (`AuthorizationRequestBuilder:110-111`, re-read) but the two overloads are independent paths — a subclass overriding the 3-arg `build` is not consulted by `authorize(metadata, options)` or the pushed path (L3-5); (3) `authorizePushed:247-249` re-reads the authorization endpoint `parameters()` already guaranteed, so its `orElseThrow` lambda is unreachable, and the redirect-URI guard exists three times (`authorize(metadata):177-180`, `requireRedirectUri:258-264`, `StepUpHandler`) (re-read); (4) `@since` is missing on `RefreshFlow.refresh(metadata, token, scopes):236` and on the now-public `ClientConfiguration.validateScopes:576` while every sibling carries one (re-read); (5) history-framed wording in production Javadoc (`AuthorizationRequestBuilder:191` "has always been emitted"), `retrieval-flow.adoc:85`, and tests (`ParametersTest:49,163,178,182,199`, `OptionsTest:163,172`, `StepUpHandlerOptionsTest:138`) — `AGENTS.md:424-430` forbids it pre-1.0 (re-read by search); (6) raw U+00A0 in two test literals (`ClientConfigurationTest:666`, `AuthorizationRequestOptionsTest:102`) (re-read); (7) closed-set refusals assert only the exception type, never the message; (8) `WireValues:68` sanitises through `LogSanitizer` but no test asserts the escaped form, so removing the sanitiser stays green (leaf's reading). The landing counts nine; the list has eight composite items.
- **L2-6 — narrative omissions.** The narrative does not name L2-1, L2-2, L2-4, L1-5, nor the out-of-order traceability row (`CLIENT-27` inserted between `CLIENT-25` and `CLIENT-15`, `requirements.adoc:680`, re-read). Its `sonar_open_new_issues=0`, `ci-verify done` and the refresh-path coverage floor are not settled by reading.
- **L2-7 — the narrative's "functional-review deliverables_met 5" against the spec's six** is not contradicted by the diff (all six trace); the outline that set five was not read.

**Layer 2 verdict:** findings — 7 (L2-1..L2-7), minor and info

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `ClientConfiguration.java:576-620` (re-read) | a configured scope holding a character outside the RFC set (NUL, DEL, double quote, backslash, U+00A0, non-ASCII) | the whole `ClientConfiguration.build` is refused where it was accepted before; strictly narrowing, nothing admitted that was refused | minor (compatibility, not security) |
| L3-2 | `AuthorizationCodeFlow.java:240-242`, `:247-249` (re-read) | a provider without a PAR endpoint, an AS without TLS or `S256` | all three refusals are one exception type (`ClientProtocolException`), told apart only by message text; the endpoint lookup after `parameters()` is a dead branch no test can cover | minor |
| L3-3 | `AuthorizationRequestBuilder.java:100-130` (re-read) | an application subclass overriding one `build` overload | 3-arg and 4-arg `build` both call `assemble` directly and neither calls the other, so a subclass override of the 3-arg `build` (API Sheriff's `QueryResponseModeAuthorizationRequestBuilder`) is bypassed by `authorize(metadata, options)`, the options step-up and `authorizePushed` (which calls `parameters`, never `build`), and an override of the 4-arg is bypassed by `authorize(metadata)`. Existing callers are unaffected by construction. The 4-arg Javadoc claims identity with the 3-arg only for an un-overridden builder. A design hazard for any consumer that subclasses, not a break. | minor |
| L3-4 | `RefreshFlow.java:444-450` (leaf's reading) | a per-call scope list | the requested scopes are interpolated unsanitised into the scope-delta WARN; safe only because `validateScopes` restricts them to printable ASCII, so the safety depends on the shared rule staying strict and is not asserted at this site | minor |
| L3-5 | `AuthorizationCodeFlow.java:203`, `StepUpHandler.java:119-141` | a callback delivered as a query (`response_mode=query`) | the callback side (`CallbackHandler`, `CallbackParameters.parse(rawQuery)`) is unchanged; no test drives an exchange from a query-delivered callback. The spec hypothesis "callback is indifferent to arrival form" rests on the parse entry points existing, by reading only. | minor |
| L3-6 | `AuthorizationRequestBuilder.java:197-221` | `response_mode` / `prompt` / scope input | no free string reaches the request: both are enums emitted through constants, the map is keyed by constants (a duplicate parameter name is structurally impossible), scopes pass `validateScopes` and both key and value go through `FormEncoder` (`URLEncoder` UTF-8), `EndpointQuery` puts the query before any `#fragment`. No injection path found. | info |
| L3-7 | `RefreshFlow.java:250-265` (re-read) | two concurrent calls with different scope sets | no shared mutable state: `requestedScopes` is an immutable copy, the form is a per-call `HashMap`, `classifyScopeDelta` is static, `AuthorizationRequestOptions` is `@Value`. The "configured `ClientConfiguration` not mutated" claim holds. | info |
| L3-8 | `AuthorizationCodeFlow.java:243-255` (re-read) | the pushed path | `FlowContext` is minted per call (state, nonce and the PKCE verifier stay server-side), the PAR body carries the challenge only, `request_uri` is form-encoded and checked non-blank in `ParClient.parse`, no `LOGGER` call was added, the client secret goes on the wire only through `clientAuthentication.decorate`. `request_uri` has no length cap beyond the parser ceiling and its `expires_in` is ignored (the AS catches a stale one). No defect with a triggering exploit. | info |
| L3-9 | `AuthorizationCodeFlow.java:163` | every flow construction | `new ParClient(configuration)` adds one parser and back-channel allocation per flow (HTTP clients are lazy); matters only to a caller that builds a flow per scope set. No behaviour change for existing callers beyond L3-1. | info |

**Layer 3 clean points (re-read):** the three default entry points keep dispatching — `authorize(ProviderMetadata)` still calls the 3-arg
`build(configuration, metadata, context)` (`AuthorizationCodeFlow:182`), the 2-arg `refresh` calls the private `redeem`, not the new
overload (`RefreshFlow:203-205`), and `TokenLifecycleManager` is not in the diff and still calls the 2-arg method; both `RefreshFlow`
test doubles are unedited (ADR-0006). The pushed path refuses a missing PAR endpoint before any request (`:240-242`), there is no
fallback branch, and the redirect map holds exactly `client_id` and `request_uri` (`:251-254`). The empty per-call scope override
is refused in both entry points (`AuthorizationRequestOptions`, `RefreshFlow:238-241`). Default output stays byte-identical: the
existing `AuthorizationRequestBuilderTest`, `AuthorizationCodeFlowTest`, `RefreshFlowTest`, `ParClientTest` are not in the diff, and
golden URLs and a golden refresh form exist; the leaf independently recomputed the old `HashMap` iteration order and found it equals
the `LinkedHashMap` order at `AuthorizationRequestBuilder:202-215` (leaf's computation, not re-run here).

**Layer 3 not shown by reading:** the Done-when gates (refresh-path per-class floor, `-Ppre-commit` plus `clean install`, ArchUnit over the
new public surface, Sonar `0`) and the real-Keycloak behaviour of `PushedAuthorizationFlowSpecIT` (336 lines, capability-gated) need a run.

**Layer 3 verdict:** findings — 9 (L3-1..L3-9: 5 minor, 4 info); not shown by reading — 2

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md:424-430 | Pre-1.0: no transitional comments, no `@Deprecated` | deviates | no `@Deprecated`; history-framed wording at `AuthorizationRequestBuilder:191`, `retrieval-flow.adoc:85`, five test sites (L2-5(5), re-read by search). |
| AGENTS.md:139-152 / ADR-0006 | Preferred overloads: ask two questions before adding an overload production prefers | conforms | the 3-arg `build` and 2-arg `refresh` stay the dispatch targets (re-read); the refresh Javadoc answers the question. Residual: the new options paths bypass an overridden 3-arg `build` (L3-3). Whether the PR body answered the two questions is third-party text, not read. |
| AGENTS.md:157-161 | Logging: `%s` only, `LogRecord` for INFO/WARN/ERROR, documented | conforms | no new `LOGGER` call or `LogRecord`; `doc/LogMessages.adoc` untouched is therefore correct. |
| CLAUDE.md / AGENTS.md | No attacker-influenced value logged unsanitised | conforms | `WireValues:68` sanitises; scope rejections never echo the entry; one WARN interpolates only validated scopes (L3-4). The sanitiser's output is not asserted by a test (L2-5(8)). |
| AGENTS.md:166-168, 189-193 | JUnit 5, AAA, parameterized for 3+, no Mockito/Hamcrest | conforms | by reading the new classes; not a build run. |
| AGENTS.md:172-176 | New or grown test classes at or under 500 lines | deviates | `ClientConfigurationTest` 904 (L2-2); all new classes within 500. |
| AGENTS.md:495 | `@since` on public API | deviates | missing on `RefreshFlow.refresh(…, scopes):236` and `ClientConfiguration.validateScopes:576` (re-read). |
| AGENTS.md Javadoc checklist | `@param`/`@return`/`@throws` on public methods | conforms | the new public methods carry full Javadoc (re-read in `AuthorizationCodeFlow`, `AuthorizationRequestBuilder`, `RefreshFlow`). |
| CLAUDE.md / AGENTS.md | No raw invisible characters in literals | deviates | raw U+00A0 at `ClientConfigurationTest:666` and `AuthorizationRequestOptionsTest:102` (re-read); lesson 2026-11-11-12-005 already names this class of defect. |
| spec Standing Constraints | Default behaviour unchanged and proven | conforms | unedited existing tests plus golden tests per flow (see clean points). Execution not shown. |
| spec Standing Constraints | Every new input validated against a closed set or the scope rules, encoded through `FormEncoder` | conforms | L3-6. |
| spec Standing Constraints | Adversarial tests: delimiter, CR/LF, duplicate parameter, oversize list, embedded whitespace | conforms | `AuthorizationRequestInjectionAttackDatabaseTest` (leaf's reading). |
| spec Standing Constraints | New `ClientConfiguration` field has Quarkus exposure decided | conforms | no new field, `ClientConfigMapper`, `ClientRuntimeConfig` and `quarkus-config-doc.adoc` untouched, no orphaned property. |
| spec Standing Constraints | No new dependency | conforms | no pom in the 27-file diff. |
| spec D6 | Next free `CLIENT-N`, README range, CLIENT-10 rewritten | conforms | `CLIENT-27`; `README:52,67,79`; `CLIENT-10`. The traceability row sits out of order (L2-6). |
| `requirements.adoc:531-563` | Security exclusions: the engine MUST NOT implement excluded items | deviates | L2-3. |
| spec Done-when | Refresh-path floor, `-Ppre-commit` plus `clean install`, Sonar 0 | not_shown_by_reading | needs a run; see Routing for the Sonar re-read. |

## Metrics and Anomalies

- Tokens: 42,298,314 total (no per-phase split in the landing; PLAN-05 was 39,315,801, PLAN-01 13,310,080).
- Duration: 123,375 s wall (about 34.3 h), against 84,055 s for PLAN-05 and 21,615 s for PLAN-01.
- Anomalies: the branch was rebased onto `origin/main` and force-pushed once, at the operator's conflict-gate answer
  (`overlap_no_content_conflict` under `no_overlap_only`), which invalidated every head-dependent verdict and cost a full re-review
  round (lesson 2026-11-13-14-003); the CI-verify record carried stub logs, dangling finding paths and a Sonar quality-gate failure filed
  as a build failure (lesson 2026-11-13-14-001); a functional-review verdict graded `D2.C5` against the renumbered outline while the
  frozen snapshot's `D2.C6` stayed ungraded (lesson 2026-11-13-14-004); npm and Jest console noise filed as `build-error`
  (lesson 2026-11-13-14-002); the triage proposed a suppression on new code (lesson 2026-11-13-14-005); the reactor-root `module-tests` /
  `test-compile` canonical needed a local patch in the operator's plugin cache that is lost on the next plugin update (a
  recurrence of lesson 2026-11-13-06-005, with the added fact that cached 2114 and 2115 both carry the defect); the post-merge CI wait timed out and was
  warn-and-continue.

## Routing and Merge Behavior

- Review: `review-gate --pr-number 901` on the merged head returns `unresolved_review_threads: 0`, `review_body_findings_total: 3`,
  `review_body_findings_handled: 0`, `merge_allowed: false`, `reason: body_findings_undisposed` (CodeRabbit `nitpick-1`, `inline-1`,
  `inline-2`). The landing carries all three review keys as `unknown` (the manifest has no `review-gate` step), so there is no claim to
  contradict; the provider's three undisposed body findings are the operator's to settle on the PR. The lane narrative says CodeRabbit's single
  inline thread (`validateScopes` character set) was fixed by TASK-14 and replied to and resolved; the provider shows no unresolved thread,
  which agrees, and it does not say which of the three body findings that was. `handled` is not re-verified.
- CI/merge: merged through the merge queue as `a60a9a48` (`ci pr view`: `state: merged`). The branch was rebased onto `origin/main`
  and force-pushed once at the operator's conflict-gate answer; the recorded branch-cleanup detail "rebase deferred to queue" describes only the last firing
  (lesson 2026-11-13-06-009). The post-merge CI wait timed out with checks pending; the landing treats it as warn-and-continue.
- Sonar: `sonar_open_new_issues=0` claimed; the re-read returns `count_status: undecidable`, `count_status_reason: head_revision_unavailable`
  (`analysis_revision` `093e4cac`, not the merge head). Recorded as **unknown, not 0** — the same state as PLAN-01 and PLAN-05.
- Collision cost: PLAN-02 and PLAN-06 ran in parallel, accepted by the operator at the overlap prompt; the shared files were
  `doc/developer/test-impl-mapping.adoc` and `doc/LogMessages.adoc`. This landing edited `test-impl-mapping.adoc` and did not touch `LogMessages.adoc`, so the
  remaining exposure is PLAN-06's own `test-impl-mapping.adoc` edit; the watch stays on PLAN-06's landing.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-02 --status shipped`
- [x] row `pr` stamped — `#901`
- [x] row `landing` stamped — `landings/PLAN-02.md`
- [x] row `plan_marshall_plan_id` stamped — `client-authorization-request-seam`
- [x] epic.md narrative reconciled (Decisions: PLAN-02 shipped / inbox drain; Open Defects: L2-1..L2-4 and the L3-2/L3-3 residuals, the three undisposed body findings on #901; Watches: the `test-compile` constraint extended to PLAN-02, the build-maven recurrence, the API Sheriff re-measure list; Queue annotations: PLAN-03 and PLAN-04 now say PLAN-02 shipped and their anchors moved)
- [x] six candidate-lesson messages dispositioned and archived with the landing (see the decision log)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- **PLAN-02's minor residuals, nothing staged:** `validateScopes` public `@since` (L2-5(4)); history-framed wording (L2-5(5)); raw U+00A0 literals (L2-5(6)); the dead endpoint lookup and the triplicated redirect-URI guard (L2-5(3)); the stale security-exclusions row and the weakened `CLIENT-14` (L2-3, L2-4); the out-of-order traceability row; `ClientConfigurationTest` at 904 lines; message and sanitiser assertions (L2-5(7), (8)); the generic PAR refusal type (L3-2); the 3-arg/4-arg `build` independence (L3-3). They are all in `token-sheriff-client` and `doc/client`. PLAN-03 shares `ClientConfiguration`, `requirements.adoc` and `test-strategy.adoc`; becomes a fix plan, or folds into PLAN-03, only on the operator's word.
- **The three undisposed CodeRabbit body findings on PR #901** (`nitpick-1`, `inline-1`, `inline-2`), together with the ten on #899 and the two on #898, are the operator's to settle on the PRs.
- **API Sheriff must re-measure on the 0.9.10 bump** (PLAN-34): `ClientConfiguration.validateScopes` is stricter (a configured scope with a non-RFC character now fails build); an empty per-call scope override is refused, not "no `scope` parameter"; a missing PAR endpoint is a generic `ClientProtocolException`, with no dedicated reason; `QueryResponseModeAuthorizationRequestBuilder` is bypassed by the options and pushed paths (L3-3); the default `response_mode` is unchanged; `CLIENT-14` now permits an opt-in `query`.
- **PLAN-03 and PLAN-04 inherit a changed neighbourhood.** `ClientConfiguration`: a new constant `SCOPE_TOKEN_MESSAGE_PREFIX` at `:70-72` shifts everything after `:67` by +4; the all-args constructor moved from `:365` to `:369`, `this.scopes = validateScopes(scopes)` from `:376` to `:380`, `strictScopeReconciliation` from `:255` to `:258-259`, `validateScopes` from `:552` to `:576` (now `public static`, +24 lines, plus the private helpers `isScopeTokenCharacter:600` and `describeRejectedCharacter:606`); no new field and no Quarkus exposure. `RefreshFlow`: 2-arg `refresh` is `:203`, `classifyScopeDelta` `:408` (now `static` with a `requestedScopes` parameter), `reportScopeDelta` `:444`, and the exchange body moved into the private `redeem(...)` at `:250-300`, shared by both overloads — PLAN-04's verifier around the validated result attaches inside `redeem`. `RefreshFlowTest`, `TokenLifecycleManagerApplyRefreshTest` and `RefreshPathCoverageGuardTest` are untouched by this plan; only the new `RefreshFlowScopeOverrideTest` (422 lines) joins the shared refresh-path coverage. `doc/client/requirements.adoc` (new `CLIENT-27`, rewritten `CLIENT-10`, edited `CLIENT-14`) and `test-strategy.adoc` moved under PLAN-03.
- Both PLAN-03 and PLAN-04 carry the stale `test-compile` premise (L1-1), already on the Watch list.
