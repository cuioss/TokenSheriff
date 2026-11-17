# Epic: API Sheriff separation seams (0.9.10)

slug: api-sheriff-seams

> Hand-written narrative for one epic under `.plan/orchestrator/api-sheriff-seams/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug api-sheriff-seams`
> on the merged tree, and `git add` the result.

## Vision

API Sheriff (the gateway, `/home/oliver/git/API-Sheriff`) must not duplicate functionality that
TokenSheriff provides, and generic functionality that lives in API Sheriff but belongs in a token
library must move here. A first duplicate (`BoundTokenEndpointClient`, 354 lines) was removed when
API Sheriff adopted 0.9.9. A follow-up audit (2026-11-12, four read-only reads of API Sheriff main
`26e34cd4` against this repository at 0.9.9 plus the release commit) found that the remaining
overlap is mostly a **missing or private TokenSheriff seam**: the code cannot be deleted on the
API Sheriff side until TokenSheriff publishes the seam. This epic ships those seams in **0.9.10**.
It is too large for one plan because it spans three modules (client, validation, the Quarkus
extensions) and mixes a security-sensitive change (refresh-result verification, the JWKS flood
bound) with small hygiene work. "Done" is: every seam below is released in 0.9.10 (or explicitly
declared out of scope by the operator), and API Sheriff's own plan PLAN-34 in epic
`kidicap-gateway-requirements` can delete the corresponding code against the released version.

**The consumer side is not in this epic.** API Sheriff deletes its copies in its own plan, after a
released 0.9.10 is pinned (the release workflow opens the bump PR). This epic only adds
TokenSheriff API. Everything new is additive and opt-in unless a spec states otherwise; the
fail-closed posture is never loosened.

## Queue annotations

- PLAN-01 — smallest and safest; run first. Closes a fail-open trap in native image for every
  TokenSheriff consumer, not only API Sheriff.
- PLAN-02, PLAN-03, PLAN-04 — all in `token-sheriff-client`; PLAN-02 and PLAN-04 both touch
  `RefreshFlow`. PLAN-02 has shipped (#901, `landings/PLAN-02.md`), so PLAN-03 and PLAN-04 now sequence on each other, not on it.
  Their anchors moved under them: `ClientConfiguration` (`validateScopes` `:552` to `:576`, now `public static`; constructor `:365`
  to `:369`; `strictScopeReconciliation` `:255` to `:258`), `RefreshFlow` (`refresh` `:203`, `classifyScopeDelta` `:408` now static,
  `reportScopeDelta` `:444`, the exchange body in a new private `redeem:250`, which is where PLAN-04's verifier attaches), and
  `doc/client/requirements.adoc` (new `CLIENT-27`, rewritten `CLIENT-10`, edited `CLIENT-14`) and `test-strategy.adoc`. Each outline
  re-reads them at HEAD.
- PLAN-04 — shipped (#907, `landings/PLAN-04.md`): the refresh-result verifier is now public (`RefreshResultVerifier` in package
  `flow`, with `RedeemedIdentityRefusalException`) and `TokenLifecycleManager` is built on it; an opt-in `CachingDiscoveryResolver`
  with a status was added and wired as an opt-in Quarkus bean. PLAN-03 inherits moved anchors: `TokenLifecycleManager.doRefresh`
  (the verifier call at `:880`, the private helpers gone), `TokenSheriffClientProducer` (two new producers at `:171` and `:193`, the
  later ones moved down about 60 lines), `ClientRuntimeConfig` (the `discovery-cache` keys from `:168`), `ClientConfigMapper` (`:120`,
  `:178`, `:209`), `quarkus-config-doc.adoc`, `doc/client/requirements.adoc` (`CLIENT-28` and `CLIENT-29` taken; the next free is
  `CLIENT-30`), `doc/client/README.adoc` (range `CLIENT-1`–`CLIENT-29`), `test-strategy.adoc`, `doc/LogMessages.adoc` (INFO `4`, WARN
  `115` and `116` taken) and the `token-sheriff-client/pom.xml` coverage includes. Each outline re-reads them at HEAD.
- PLAN-08 — shipped (#912, merge dcbdcfb6, `landings/PLAN-08.md`): the discovery cache's Quarkus startup validation (one `checkSettings` rule list,
  a structured `Setting` identity, a build step that fails a disabled-cache injection at runtime init), its failure and interrupt handling (a
  non-`TransportException` is a recorded failure, an interrupted caller aborts without state, a 365-day bound), the test gaps, the logging and
  configuration text, and the verifier's `sub` decision (keep and document; `RefreshResultVerifier`, `StoredToken` and `TokenLifecycleManager` are
  untouched). A 26-file merge (+2864/-205) for five deliverables. PLAN-03 inherits moved anchors: `ClientConfigMapper` (`propertyOf` `:126`, the
  duration parse `:283`, `validateDiscoveryCache`), `TokenSheriffClientProducer` (`cachingDiscoveryResolver` `:188`, `validateDiscoveryCacheOnStartup`
  `:207`), `TokenSheriffClientProcessor` (a new `requireDiscoveryCacheForInjection` build step `:144` and a new `TokenSheriffClientRecorder`),
  `ClientRuntimeConfig`, `quarkus-config-doc.adoc`, `doc/client/requirements.adoc` (`CLIENT-29` edited), `doc/client/specification/test-strategy.adoc`,
  `retrieval-flow.adoc`, `threat-model.adoc`, `doc/LogMessages.adoc` (INFO `4` row text only; no new record) and `AGENTS.md` (a new approved broad-catch
  site). Each outline re-reads them at HEAD.
- PLAN-10 — staged on the operator's word from PLAN-08's analysis ("one follow-up plan for the residuals and other left-overs and open issues"): the
  discovery cache's residual text errors and test-rule deviations, PLAN-01's and PLAN-02's minor residuals, PLAN-04's two test-hardening nitpicks, and
  a recorded disposition for every unsettled review-bot body finding on the shipped PRs. Five deliverables, workstream WS-01. It shares surface with
  PLAN-03 (`ClientConfiguration`, `ClientConfigMapper`, `requirements.adoc`, `test-strategy.adoc`, `LogMessages.adoc`) and sequences after it; with
  PLAN-09 it shares `LogMessages.adoc` only.
- PLAN-09 — staged on the operator's word from PLAN-07's analysis: an invalid `@BearerAuth` scope escaping the scope-violation path, the
  unknown-`kid` claim path's guard order and burned window against what ADR-0030 says, the helpers' own work bounds (builder, body
  handler, `Content-Length`), stale hub docs and the ADR-0031 itemisation, and one health-check test that cannot fail. Five deliverables,
  workstream WS-02, ahead of the 0.9.10 release. It shares surface with PLAN-03 (`IssuerConfigResolver`, `HttpJwksLoaderConfig`,
  `doc/LogMessages.adoc`, `quarkus-config-doc.adoc`) and PLAN-08 (`doc/LogMessages.adoc`).
- PLAN-05 — contains a back-channel logout-token deliverable that is **gated on an operator
  decision at outline** (TokenSheriff owns it, or declares it out of scope as the client docs do
  today: CLIENT-19).
- PLAN-06 — shipped (#906, `landings/PLAN-06.md`): a medium-high-risk flood bound on an unauthenticated path, opt-in only.
  PLAN-03 and PLAN-07 inherit moved anchors in `HttpJwksLoaderConfig` (private constructor `:265`, `withIssuerIdentifier`
  `:433`, `allowedEgressHost` `:615`, `build()` `:1015`; PLAN-03's new field goes into all three constructor call sites),
  `IssuerConfigResolver`, `JwtPropertyKeys`, `quarkus-config-doc.adoc`, `openid-discovery.adoc`,
  `doc/validation/requirements.adoc` (`VALIDATION-4.6` and `4.7` taken), `doc/LogMessages.adoc` (INFO `10` and WARN `173`
  taken) and ADR numbering (ADR-0030 is PLAN-06's; the next free is `0031`). Each outline re-reads them at HEAD.
- PLAN-07 — shipped (#908, merge 80bc2b73, `landings/PLAN-07.md`): the four public helpers (`BearerCredentials`, `BearerChallenge`,
  `ErrorTextSanitizer.escapeNonPrintableAscii`, `BoundedBodyHandler`) and its two folded defect fixes (deliverable 6, the
  `numericDate` bound; deliverable 7, the hand-off guard release). A 69-file merge (+7181/-2479) for seven spec deliverables, kept whole
  as the spec recorded. PLAN-03 and PLAN-08 inherit moved anchors: `HttpJwksLoaderConfig` and `IssuerConfigResolver` (the window check at
  `:584-590`), `quarkus-config-doc.adoc`, `openid-discovery.adoc`, ADR numbering (`0031` is taken; the next free is `0032`),
  `doc/validation/requirements.adoc` (`VALIDATION-14`), `doc/commons/requirements.adoc` (`COMMONS-16` and `COMMONS-17`),
  `doc/developer/test-impl-mapping.adoc` and `doc/LogMessages.adoc` (the `173` row). `BoundedBodyHandlers` and the client's
  `BoundedContentBodyHandler` no longer exist. Each outline re-reads them at HEAD.

## Decisions

- 2026-11-12 — Epic created from the API Sheriff operator's request. Seven plans in two workstreams
  (WS-01 client seams: PLAN-01..04; WS-02 validation and shared-helper seams: PLAN-05..07), cut by
  module and by risk, not one plan per finding. `parallelization_scope` = 1 (the project default, not
  asked of the operator): most plans share `token-sheriff-client`. Target release 0.9.10 (the
  repository is at `0.9.10-SNAPSHOT`).
- 2026-11-12 — PLAN-01 shipped (PR #898, merge c23e2a85). Three-layer analysis: no blocker or major, layer 3
  clean. Inbox drain: 7 candidate-lessons dispositioned (3 promoted as lessons 2026-11-12-18-001..003; 001/002
  recurrences of lesson 2026-11-11-12-001 and 005/006 of 2026-11-11-12-003); the `doc/client/README.adoc`
  requirement-range line is now declared in the surfaces of PLAN-02, PLAN-03 and PLAN-04 (folded from 001).
- 2026-11-13 — PLAN-05 shipped (PR #899, merge fff5befe; `landings/PLAN-05.md`). Three-layer analysis: no blocker or
  major; layer 3 carries a resource-exhaustion defect in the new `LogoutTokenValidator.numericDate` (reported to the
  operator, nothing staged). Branch A (publish the logout validator and replay guard) was the OD-8 outcome. Inbox
  drain: 23 candidate-lessons dispositioned — 9 promoted as lessons 2026-11-13-06-001..009 (the rest aggregated into
  them or recurrences of 2026-11-02-14-001 / 2026-11-05-23-007 / 2026-11-11-12-003), the five `script_failure`
  clusters (017-020 and the `verdict_currency` one folded into 06-008) recorded as a Watch, not as lessons.
- 2026-11-13 — PLAN-02 shipped (PR #901, merge a60a9a48; `landings/PLAN-02.md`). Three-layer analysis: no blocker or
  major, no injection or resource-exhaustion defect; layer 3 carries minor findings only. Operator decisions at outline, read
  from the diff: `response_mode` `{form_post, query}`, `prompt` the full OIDC set, the pushed path shipped, per-call overloads
  (not `toBuilder`), an empty scope override refused. Inbox drain: 6 candidate-lessons dispositioned — 5 promoted as lessons
  2026-11-13-14-001..005, one a recurrence of 2026-11-13-06-005. PLAN-02 and PLAN-06 ran in parallel on the operator's
  accepted docs-only overlap.
- 2026-11-15 — PLAN-06 shipped (PR #906, merge fbd531d8; `landings/PLAN-06.md`). Three-layer analysis: no blocker or major;
  layer 3 carries an availability defect in the new claim path (L3-1: the in-flight guard is set before a hand-off that catches only
  `RejectedExecutionException`, so a caller-supplied executor that fails any other way stalls all refresh for good), reported to the
  operator, who chose to **fold it into PLAN-07** as its deliverable 7 together with the lane's residuals. Operator decisions at
  outline, read from ADR-0030: non-blocking `getKeyInfo`, a flag with fixed 1 s to 30 s retry constants, bounds `5..3600 s`
  with no default window, Quarkus wiring, a discarded fetch leaves `lastSuccessfulLoad` untouched. Inbox drain: 13
  candidate-lessons dispositioned — 4 promoted as lessons 2026-11-15-11-001..004, 4 recurrences of 2026-11-02-14-001 (a document
  states a rule more broadly than the code gives it; 001 also an incomplete sweep, cf. 2026-11-05-23-007), 3 argparse rejections
  and the `manage-status read --field` half of 011 recurrences of 2026-11-11-12-003, one `no_sanction` commit a recurrence of
  2026-11-12-18-003. PLAN-06 ran in parallel with PLAN-02 and PLAN-04 on the operator's accepted docs-only overlap.
- 2026-11-15 — PLAN-04 shipped (PR #907, merge 1be3867b; `landings/PLAN-04.md`). Three-layer analysis: no blocker; the layer-3 read
  found one major defect (L3-1: invalid Quarkus discovery-cache values such as `ttl=PT0S` do not fail at startup, only at first use) and
  one major test gap (L1-5: no test serves a changed endpoint after a refresh), plus minor findings in the cache's failure, interrupt
  and overflow handling and several doc statements that overstate the code. The operator chose to **stage a new PLAN-08** for them.
  Operator decisions at outline, read from the diff and the docs: the verifier in package `flow`, no default TTL, a bounded grace
  period with fail closed after it (Quarkus default `PT0S`), minimum refresh interval and jitter defaulting to zero, a defensive copy
  of `ProviderMetadata`, its own status record plus a `LoadingStatusProvider` mapping, and the cache wired as an opt-in Quarkus bean.
  The spec's HYPOTHESIS about the refreshed ID token's `sub` was settled in code for a bound session and left open, unwritten, for an
  unbound one; the leaf rated it major, the orchestrator re-read it as a documentation gap and put it in PLAN-08 deliverable 5. Inbox
  drain: 9 candidate-lessons dispositioned — 6 promoted as lessons 2026-11-15-18-001..006, three recurrences (of 2026-11-15-11-001,
  2026-11-11-12-003 and, for the third time, 2026-11-12-18-003). PLAN-04 ran in parallel with PLAN-06 and PLAN-07 with no shared file
  beyond `doc/LogMessages.adoc` (PLAN-06).
- 2026-11-16 — PLAN-07 shipped (PR #908, merge 80bc2b73; `landings/PLAN-07.md`). Three-layer analysis: no blocker or major; layer 3 carries
  five minor defects (L3-1 a forged-`kid` miss pulses the shared in-flight guard so a periodic cycle and its staleness check can be skipped, which
  the docs say needs a window-claiming caller; L3-2 a `@BearerAuth` scope outside the grammar throws from the scope-violation path and nothing
  validates it at startup; L3-3 a failed hand-off still burns the window; L3-4 the memory bound holds in bytes and not in per-chunk objects, now
  also for the client back channel; L3-5 the challenge builder accumulates before it refuses) plus one point not shown by reading (a malformed
  `Content-Length`). Layer 2 carries stale hub docs (the commons README range, the ADR index at 0029), a health-check test that cannot detect lost issuer
  sanitisation, an incomplete itemisation of default changes, and the `initialLoadRetry` plus refresh-0 asymmetry. The leaf's L1 "premise refuted"
  finding was dropped on re-read (the spec sentence was ambiguous, not wrong). Both folded defects (PLAN-05's `numericDate`, PLAN-06's hand-off leak)
  are fixed and re-read. Operator decisions at outline, read from ADR-0031: OD-1 scheme case follows the RFC, OD-2 whitespace is trimmed, OD-3 a scope
  that cannot round-trip is refused, OD-4 the sanitiser escapes the backslash, OD-5 the shared body handler was done. Inbox drain: 11 candidate-lessons
  dispositioned: 2 promoted as lessons 2026-11-16-06-001 and -002, 8 recurrences or folds into existing lessons, 1 single-occurrence lead recorded in a
  Watch. The layer-3 and layer-2 residuals were reported to the operator, who chose to **stage a new PLAN-09** for them. At the same
  `analyze and emit` the operator answered the overlap prompt for PLAN-03 and PLAN-08 (five shared files: `TokenLifecycleManager.java`,
  `TokenSheriffClientProducer.java`, `doc/client/requirements.adoc`, `doc/client/specification/test-strategy.adoc`, `doc/LogMessages.adoc`):
  **emit PLAN-08 only**; PLAN-03 waits for PLAN-08 to land.
- 2026-11-17 — PLAN-08 shipped (PR #912, merge dcbdcfb6; `landings/PLAN-08.md`). Three-layer analysis: no blocker or major at any layer. Layer 3 carries
  two minor points (a caller with an interrupt flag already set gets a failing provider's refusal rethrown unrecorded; `CachingDiscoveryResolver:407-411`) and
  two not shown by reading (the build step matches by required-type name only, so ArC's removal of an unused injection point is untested; the duration parse
  message names ISO-8601 only and Quarkus's converter was never run); layer 2 carries six residual text errors (the Javadoc Logging paragraph on interrupt and on a
  recovering refresh, the single-flight paragraph's missing third case, mixed `-001`/`-4` identifier forms, "most failed attempts", the 12.2 rationale omitting the
  unchecked stored ID token, the ADR-0026 summary); layer 1 one (the duplicate-WARN cause is the unconditional `state.set`, not a lost CAS). The lane's `weakened_test`
  claim was verified (49 to 60 assertion call sites, 9 to 2 gate-pattern lines; the override was a false positive). The five operator decisions at outline were read off
  the merged code: fail-fast injection at runtime init, interrupt aborts with no state, refuse (not saturate) at 365 days, no quiet resolve path, `sub` kept and
  documented. Inbox drain: 11 candidate-lessons dispositioned: 2 promoted as lessons 2026-11-17-06-001 (from -001, with -002 to -005 aggregated into it) and
  2026-11-17-06-002 (from -006, with -008 aggregated), two recurrences of existing lessons (-007, -009), two `script_failure` clusters classified from the archived work
  log and absorbed into the Watch (-010, -011). The residuals were reported to the operator, who chose **one follow-up plan** for them and "other left-overs and open
  issues": staged as **PLAN-10** (the discovery cache's residuals, PLAN-01's and PLAN-02's minor residuals, PLAN-04's two nitpicks, a disposition for every unsettled
  review-bot body finding). The overlap prompt for PLAN-03 against PLAN-09 (seven shared files: `LogMessages.adoc`, `doc/commons/requirements.adoc`,
  `IssuerConfigResolver`, `openid-discovery.adoc`, `BoundedBodyHandler`, `HttpJwksLoaderConfig`, `BoundedBodyHandlerTest`) was answered **emit PLAN-03 only**; PLAN-09 waits for
  PLAN-03 to land and PLAN-10 sequences after PLAN-03.
- 2026-11-12 — Not in this epic: anything API Sheriff can do with 0.9.9 as released (that is API
  Sheriff's PLAN-33); API Sheriff's own internal duplicates; `TokenLifecycleManager` as a replacement
  for the gateway's refresh coordinator (the manager is keyed on a server-side store and has no
  stateless cookie mode, ADR-0046 on the API Sheriff side). If the operator wants a typed-outcome
  single-flight primitive here later, it is a new plan.

## Open Defects

- Candidate defect from the audit, **not verified**: `RefreshFlow` (client) validates
  only the access token of a refresh response (`RefreshFlow` ~:196), so a consumer that does not
  use `TokenLifecycleManager` gets no ID-token or `sub` check. **Settled by PLAN-04 (#907)**: `RefreshResultVerifier.verify` is now
  public and `TokenLifecycleManager` calls it, so the claim was true and the seam exists. What stayed open was the unbound-session case
  (the ID token's `sub` against nothing when no subject is bound and the access token has no `sub`), staged as PLAN-08 deliverable 5.
  **Settled by PLAN-08 (#912)** as keep-and-document (`token-handling.adoc`, `threat-model.adoc:363-366`, re-read); the stated reason is partly overstated (the
  stored ID token of an unbound session is replaced unchecked, `StoredToken.java:174-182`), now in PLAN-10 deliverable 1.
- 2026-11-12 — PLAN-01 landing `client-native-image-registration-008.md` is **incomplete**
  (`inbox landing-check`: `complete: false`). Missing keys: `unresolved_review_threads`,
  `review_body_findings_total`, `review_body_findings_handled` (all `unknown`; no finalize step records
  them). The provider re-read (`review-gate --pr-number 898`) shows 0 open threads and 2 coderabbit
  inline body findings (`inline-1`, `inline-2`) with no recorded disposition, while the lane narrative
  says every inline finding was answered. Sonar `sonar_open_new_issues=0` could not be re-verified
  (`undecidable`, `head_revision_unavailable`). Operator to settle the two coderabbit findings on the
  PR. See `landings/PLAN-01.md` § Routing and Merge Behavior.
- 2026-11-12 — PLAN-01 minor residuals, nothing staged (see `landings/PLAN-01.md`): (1) the delivery
  rationale at `doc/client/requirements.adoc:591` says API Sheriff's shape uses the engine without
  the client Quarkus extension — **wrong**, its pom lists `token-sheriff-client-quarkus`; (2) "SecureRandom
  holders" wording at `requirements.adoc:595` and `test-strategy.adoc:503` where the guard covers every
  static `Random`; (3) `NativeImageClassScanner:65,83` `@throws` omits `UncheckedIOException`;
  (4) two untested 400 branches in `ClientEngineNativeEndpoint:184,210`; (5) `ClientNativeImageSpecIT:23`
  imports Hamcrest against the spec constraint (module precedent). Becomes a plan only on the operator's word
  (PLAN-07 has shipped, so it can no longer take them; PLAN-03 is at six deliverables). **Staged as PLAN-10 deliverable 3 on the operator's word
  (2026-11-17)**; its line numbers have moved and were not re-read.

- 2026-11-13 — PLAN-05 layer-3 defect, reported to the operator, **nothing staged** (see `landings/PLAN-05.md` § Layer 3):
  `LogoutTokenValidator.numericDate:311-312` runs `BigDecimal.setScale(0, FLOOR)` on an attacker-sized exponent
  (`iat`/`exp` = `1e99999999`, ten characters) inside `synchronized (decisionLock)` (`:170-186`), so one signed token
  stalls every other logout validation on that validator; the lane's proposed fix covers only the positive exponent
  (a negative one takes the same path). Reachable only after the signature verified, so the signer must be the trusted
  issuer. Same area, minor: `freshnessWindow` accepts an overflowing `Duration` and `validate` then throws inside the
  lock (`:138-140,247`); a backward wall-clock step can re-admit a purged `jti` and no document says so (`:171`);
  `SignatureVerifiedToken.toString` prints `typHeader` unsanitised (`:77`); the out-of-range `numericDate` branch and the
  `fromIssuerStatus` catch are untested; three test classes exceed 500 lines. **Operator's word (2026-11-13): fold into
  PLAN-07** — now its deliverable 6 (numericDate bound for both exponent signs, `freshnessWindow` limit, the backward-clock
  statement, the `toString` exclusion, a test class for the out-of-range branch), together with the lane's other advisories
  (the untested `fromIssuerStatus` catch, the three test classes over 500 lines, the ADR-0029 revision-history note). The
  PLAN-01 minor residuals above are NOT in it and are still the operator's open call. **Settled by PLAN-07 (#908)**: the bound is
  in place for both exponent signs (`LogoutTokenValidator:371-388`, re-read), the freshness window is capped and saturating, and
  the clock-step advice was reworded after review. Not shown by reading: whether the "promptly" tests fail on the old code.
- 2026-11-13 — PLAN-05 landing review numbers contradicted by the provider: landing says `review_body_findings_total=1`,
  `review-gate --pr-number 899` returns 10 (nine CodeRabbit `inline-1..9`, `additional-1`), `handled` 0 with
  `merge_allowed: false, reason: body_findings_undisposed`; `unresolved_review_threads` agrees (0). `handled` is not
  re-verified. Sonar `sonar_open_new_issues=0` is `undecidable` (`head_revision_unavailable`), recorded as unknown. The
  lane's narrative shows at least six of the findings answered by fix tasks. Operator to settle the ten on PR #899.
- 2026-11-13 — PLAN-02 landing `client-authorization-request-seam-007.md` is **incomplete** (`inbox landing-check`:
  `complete: false`; the three review keys are `unknown`, the manifest has no `review-gate` step). The provider re-read
  (`review-gate --pr-number 901`) shows 0 open threads and 3 coderabbit body findings (`nitpick-1`, `inline-1`, `inline-2`) with
  no recorded disposition, `merge_allowed: false`. No claim to contradict. Sonar `0` is `undecidable`
  (`head_revision_unavailable`), recorded as unknown. Operator to settle the three on PR #901.
- 2026-11-13 — PLAN-02 minor residuals, nothing staged (see `landings/PLAN-02.md`, all in `token-sheriff-client` and
  `doc/client`): (1) `ClientConfiguration.validateScopes` was tightened for existing callers (RFC 6749 §3.3 set; NUL, DEL,
  quote, backslash, U+00A0 and non-ASCII now refused at `build`) and made public with no `@since` (`:576`); the refresh overload
  `RefreshFlow:236` has none either; (2) `requirements.adoc` `Security exclusions` (`:561-563`) still bans "tokens / codes in URL
  query" while `CLIENT-27` ships an opt-in `query`, and `CLIENT-14` was reworded to drop "authorization codes" from its MUST NOT;
  the `CLIENT-27` traceability row is out of order (`:680`); (3) 3-arg and 4-arg `AuthorizationRequestBuilder.build` are
  independent paths, so a subclass override of one is bypassed by the other's callers, incl. `authorizePushed`
  (`:100-130`); (4) a missing PAR endpoint is the generic `ClientProtocolException` (`AuthorizationCodeFlow:240`), the
  endpoint lookup at `:247` is dead and the redirect-URI guard exists three times; (5) history-framed wording
  (`AuthorizationRequestBuilder:191`, `retrieval-flow.adoc:85`, five test sites), raw U+00A0 in two literals
  (`ClientConfigurationTest:666`, `AuthorizationRequestOptionsTest:102`), `ClientConfigurationTest` at 904 lines, refusal
  tests that assert only the type and no test of the `LogSanitizer` output. Becomes a plan, or folds into PLAN-03
  (which shares `ClientConfiguration`, `requirements.adoc` and `test-strategy.adoc`), only on the operator's word.
  **Staged as PLAN-10 deliverable 4 on the operator's word (2026-11-17)**; its line numbers have moved and were not re-read.
- 2026-11-15 — PLAN-06 landing `jwks-loader-gateway-needs-014.md` is **incomplete** (`inbox landing-check`: `complete: false`;
  `unresolved_review_threads` and `sonar_open_new_issues` are `unknown`) and its review numbers are contradicted by the provider:
  the landing says `review_body_findings_total=1`, `handled=1` (from the plan findings store); `review-gate --pr-number 906`
  returns 9 (`nitpick-1`, `inline-1..8`), `handled` 0, `merge_allowed: false, reason: body_findings_undisposed`;
  `unresolved_review_threads` agrees (0). `handled` is not re-verified; the lane's messages name seven bot findings answered by fix
  tasks. Sonar is `undecidable` (`head_revision_unavailable`), recorded as unknown. Operator to settle the nine on PR #906.
- 2026-11-15 — PLAN-06 layer-3 defect, reported to the operator, **folded into PLAN-07 deliverable 7 on the operator's word**
  (see `landings/PLAN-06.md` § Layer 3): `HttpJwksLoader.scheduleUnknownKidRefresh:865-876` sets `fetchInFlight`, then catches only
  `RejectedExecutionException` around `execute`; a caller-supplied executor that throws another `RuntimeException` or discards the
  task leaves the guard set, so `performBackgroundRefresh` skips every cycle uncounted (`:1461`) — no refresh and no `maxStaleness`
  degradation, and the thrown case also reaches the request thread. The default executor is not affected; not run here. Folded with
  it: the window burned before the guard is tried (`:861`), a new-code catch-all (`:948`), four spec test cases never written, two
  test classes over 500 lines (571, 1080), the `10` s timeout rows and the stale rotation-delay wording in the docs, a Quarkus
  failure message that names the builder method, and a `WARN` for a benign discard. **Settled by PLAN-07 (#908)**: the hand-off now
  releases the guard on any `RuntimeException` (`HttpJwksLoader:884-889`, re-read) and the test classes are split. Left over, see
  the PLAN-07 residuals entry below: the window is still burned by a failed hand-off, the guard is still taken before the window,
  and the `initialLoadRetry` plus refresh-0 message still names the builder.

- 2026-11-15 — PLAN-04 landing `client-refresh-verification-discovery-cache-010.md` is **incomplete** (`inbox landing-check`:
  `complete: false`; the three review keys and `sonar_open_new_issues` are `unknown`, and the lane says the lifecycle publishes no
  `review-gate` facts). No claim to contradict. The provider re-read: both CodeRabbit threads on #907 are resolved (one fixed in
  `36b61921`, one declined with a reason); the review bodies carry two nitpicks, answered in a comment as known test-hardening gaps and
  not fixed (`DiscoverySpecIT:102-103`, `CachingDiscoveryResolverAdversarialTest:157-158`); `review-gate --pr-number 907` is
  `undecidable_read` because of the unreadable "Files not reviewed ... : 8" section. Sonar is `undecidable`
  (`head_revision_unavailable`), recorded as unknown; the lane's own `new_code_issue_count=0` did not read hotspots. Operator to settle
  the two nitpicks on PR #907 (or accept them as recorded).
- 2026-11-15 — PLAN-04 layer-3 and fidelity defects, reported to the operator, **staged as PLAN-08 on the operator's word** (see
  `landings/PLAN-04.md` § Layer 1 and 3 and `plans/PLAN-08-discovery-cache-and-verifier-hardening.md`): invalid Quarkus discovery-cache
  values do not fail at startup (`ClientConfigMapper.validateDiscoveryCache:178-197`, `TokenSheriffClientProducer:193`); no test for the
  changed-endpoint criterion; an unexpected delegate exception never reaches the cache status (`CachingDiscoveryResolver:241`); a caller's
  interrupt becomes a cache-wide failure episode (`DiscoveryResolver:190-193`); the four durations are unbounded; the delegate still logs
  INFO per refresh while the Javadoc, `LogMessages.adoc` and `retrieval-flow.adoc` imply it does not; the grace-period default, the
  disabled-cache injection and the `TokenSheriffClient-4` identifier statements disagree with the code; ADR-0026 does not cover the
  client module. Nothing here has run; the leaf read code only. **Settled by PLAN-08 (#912)** except the residuals in the PLAN-08 entry below
  (`landings/PLAN-08.md`): the startup check, the delegate-exception record, the interrupt handling, the 365-day bounds, the test gaps and the
  injection statement are fixed and re-read; the logging, defaults, identifier and ADR-0026 statements were rewritten with six text errors left (PLAN-10 deliverable 1).
  PLAN-04's two nitpicks (`DiscoverySpecIT:102-103`, `CachingDiscoveryResolverAdversarialTest:157-158`) are PLAN-10 deliverable 2.
- 2026-11-15 — PLAN-04's `e2e-tests` CI job failed twice on `accessibility-wcag-compliance.spec.js:60` (a Playwright screenshot
  protocol error on the Token Debugger page; heads `fd77ed55` and `36b61921`) and passed on rerun both times. The lane says no branch
  code is implicated (the plan does not touch the Token Debugger UI); the orchestrator did not reproduce it. The lane's cause (a
  screenshot taken before the page settles) is a hypothesis. Lesson `2026-11-15-18-003` filed. **Nothing staged**: a plan to fix the
  spec's wait, or a known-flake registration, is the operator's call.
- 2026-11-16 — PLAN-07 landing `plan-07-public-helper-hygiene-012.md` is **incomplete** (`inbox landing-check`: `complete: false`;
  `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and `sonar_open_new_issues` are `unknown`; no
  finalize step records them). No claim to contradict. The provider re-read: `review-gate --pr-number 908` returns 0 open threads,
  7 coderabbit body findings (`inline-1..7`), `handled` 0, `merge_allowed: false, reason: body_findings_undisposed`; all seven threads read
  `resolved` and each carries a reply (3 fixed, 3 declined with a reason, 1 declined after a reverted fix). `handled` is not re-verified.
  CodeRabbit did not mark two of them addressed and names a header-length round-trip test and the one-byte-chunk case as the open follow-ups. Sonar is
  `undecidable` (`head_revision_unavailable`), recorded as unknown. Operator to settle the seven on PR #908 or accept them as recorded.
- 2026-11-16 — PLAN-07 layer-3 and layer-2 residuals, reported to the operator, **staged as PLAN-09** (see `landings/PLAN-07.md` § Layer 2 and 3;
  all by reading, nothing run): (1) `HttpJwksLoader.scheduleUnknownKidRefresh:862-875` takes the in-flight guard before the window check, so every
  forged-`kid` miss pulses the guard and `performBackgroundRefresh:1476-1479` can skip a cycle and its `maxStaleness` check; ADR-0030 and
  `openid-discovery.adoc` say the guard is taken "when a window is claimed" (the order itself was the operator's D7 choice, declined on the thread);
  (2) the same method advances `nextAllowedNanos` (`:873-875`) before `execute`, and a failed hand-off releases only the guard, so the next fetch waits up to
  the window; (3) `BearerTokenResponseFactory.createConstraintViolationResponse:158-163` lets `BearerChallenge`'s `IllegalArgumentException` escape for an
  annotation scope outside the grammar or over 64 scopes, `BearerTokenInterceptor:164-167` does not catch it and nothing validates `@BearerAuth` scope names
  at startup; (4) `BoundedBodyHandler:259` keeps one array per delivered buffer and the client back channel lost its `ByteArrayOutputStream`
  (`COMMONS-17` states the byte bound only; the bot's proposal broke that bound and was reverted); (5) `BearerChallenge.Builder` accumulates scopes before
  `build()` refuses (`:160-198`); (6) not shown: a non-numeric `Content-Length` at `BoundedBodyHandler:154`. Layer 2 and docs: `doc/commons/README.adoc:53` says
  `COMMONS-1`–`COMMONS-15`; `doc/README.adoc` lists ADRs only to 0029; ADR-0031's "Changed default behaviour" omits the 100-year freshness window, the DEBUG
  downgrade of the unchanged discard, the health-check log change and the chunk list; `JwksEndpointHealthCheckIssuerStatusFailureTest` would stay green if the
  issuer went through raw; the `initialLoadRetry` plus refresh-0 failure names the builder (`IssuerConfigResolver:600-603`); nested public types in `BoundedBodyHandler`
  carry no `@since`. Open follow-ups the lane or the bot named: a header-length round-trip test (`BearerChallenge:74` against the client parser), the narrower RFC 6750
  `error_description` grammar (operator decision OD-3 kept the RFC 9110 quoted string), and a one-byte-chunk test. **Staged as PLAN-09 on the operator's word (2026-11-16)**
  (`plans/PLAN-09-public-helper-residuals.md`, five deliverables, WS-02): all of the above except the `error_description` grammar, which stays the
  OD-3 contract unless the operator decides otherwise.
- 2026-11-17 — PLAN-08 landing `discovery-cache-verifier-hardening-012.md` is **incomplete** (`inbox landing-check`: `complete: false`;
  `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` are `unknown`; no finalize step records them). No claim to
  contradict. The provider re-read: `review-gate --pr-number 912` returns 0 open threads, 5 coderabbit body findings (`inline-1..5`), `handled` 0,
  `merge_allowed: false, reason: body_findings_undisposed`; the five threads carry the lane's replies (four fixed with regression tests, one answered "not changed"
  on the `token-handling.adoc` ordering sentence). `handled` is not re-verified. `ci pr comments` reports `unresolved: 7` of 18 beside the gate's 0 unresolved threads of 5;
  the difference was not reconciled per item. Sonar is `undecidable` (`head_revision_unavailable`), recorded as unknown; the lane's `0` is the roundtrip's own count.
  Settling the five findings (with those of the other shipped PRs) is **PLAN-10 deliverable 5**.
- 2026-11-17 — PLAN-08 layer-3, layer-2 and layer-1 residuals, reported to the operator, **staged as PLAN-10 deliverables 1 and 2 on the operator's word** (see
  `landings/PLAN-08.md` § Layer 1 to 3; the code points re-read at `dcbdcfb6`, the leaf's test-side points not): a caller with an interrupt flag already set gets a
  failing provider's refusal rethrown unrecorded (`CachingDiscoveryResolver:407-411`, accepted by the stated design, no backoff from that thread);
  `TokenSheriffClientProcessor:148-149` matches an injection point by required-type name only, so ArC's removal of an unused point and an `Instance<>` optional lookup are untested
  (not shown by reading); `ClientConfigMapper:283-290` names ISO-8601 only and was never run under Quarkus's converter (not shown by reading); six text errors
  (Javadoc `:95-102` and `retrieval-flow.adoc:323` on interrupt and on a recovering refresh; `retrieval-flow.adoc:268-269` omits the failed-in-backoff case; `LogMessages.adoc:209-212`
  mixes `-001` and `-4`; "most failed attempts" (the leaf counted 5 of 13 sites, not re-read); `threat-model.adoc:363-366` omits that an unbound session's stored ID token is replaced
  unchecked; ADR-0026's `summary` and `tags` still describe a validation-only ADR); the duplicate-WARN cause is wrong in the Javadoc and `retrieval-flow.adoc:331-333`; three unit-test helpers
  catch `RuntimeException` under `cui-rewrite:disable` against AGENTS.md (`...FailureHandlingTest:321`, `...HookFailureTest:320`, `...RefreshRegressionTest:287`); the harness is copied into three classes
  and `invalidSettings()` duplicates `invalidDurations()` (the leaf's reading); no test drives `resolve()` with 365-day values. Nothing here has run.
- 2026-11-17 — Lessons named in this ledger are not in the lessons store: `2026-11-16-06-001` (promoted at the PLAN-07 drain; its file does not exist and `manage-lessons get` returns `not_found`,
  `list --status all` shows 19 lessons and no tombstone) and `2026-11-02-14-001` (the lesson the Watch below asks the operator to review; absent from the listing). Lessons `2026-11-11-12-002`, `2026-11-11-12-003`,
  `2026-11-13-06-005`, `2026-11-13-06-008` and `2026-11-15-18-001`, `-002`, `-004`, `-005` and `-006` are not listed either (`2026-11-15-18-003` is). The cause is not known (an explicit removal, a plan's lesson carry-back or a cleanup run); not recreated here. The operator
  decides whether the missing lessons are re-promoted from the ledger text; every Watch below that names a missing lesson loses its owner until then.

## Watches

- Source claims in the specs are audit leads read from code only; nothing was run. Each spec labels
  them OBSERVED or HYPOTHESIS with a verify-at-outline clause. — trigger: every plan's outline.
- API Sheriff's refresh persist is presence-only while 0.9.9 compare-and-sets on the base refresh
  token (reseed fix); a stale grant overwriting a re-login mid-refresh is hypothesised, not
  verified, and is API Sheriff's to settle (its PLAN-33 D5). PLAN-04 shipped without a word about it (its landing and the diff
  carry no compare-and-set seam), so it stays API Sheriff's. — trigger: API Sheriff PLAN-34, in case a TokenSheriff-side
  compare-and-set seam is the right answer.
- PLAN-01 added `doc/developer/test-impl-mapping.adoc` rows that PLAN-04 and PLAN-07 also declare — the landing
  under-declared its surface. PLAN-05, PLAN-02 and PLAN-06 each met it on rebase and shipped with the other plans' rows present
  (PLAN-06's landing does not say whether a conflict occurred). PLAN-04's merge diff does not touch the file, so the watch is
  **narrowed to PLAN-07**, which declares it. `doc/LogMessages.adoc` was edited by PLAN-04 beside PLAN-06 and both rows are present on
  main (INFO `4`, WARN `115` and `116` here; INFO `10` and WARN `173` there); neither landing says whether a conflict occurred.
  **Retired at the PLAN-07 landing**: that merge edits `test-impl-mapping.adoc` (+27/-15) and `LogMessages.adoc` (the `173` row), PLAN-04's rows are
  still on main, and neither the landing nor the diff shows a conflict (the landing does not say; not checked beyond the diff).
- PLAN-05's capture carried five `script_failure` clusters it did not classify (`manage-execution-manifest`,
  `manage-findings`, `manage-solution-outline`, `manage-status`, and `verdict_currency`, the last folded into lesson
  2026-11-13-06-008); the argv and error are in that plan's work log, not here. Recorded instead of filed as lessons because
  nothing says caller or script. PLAN-06's five clusters carried their argv and cause and were all classified (three argparse
  rejections and a `read --field` one are recurrences of lesson 2026-11-11-12-003, a ledger gap became lesson 2026-11-15-11-004, a
  `no_sanction` commit is a recurrence of 2026-11-12-18-003); the same notations (`manage-findings`, `manage-solution-outline`,
  `manage-status`) are probably PLAN-05's, so this Watch is **narrowed**, not retired. PLAN-04's drain carried four more, all with
  their argv and cause: an invented `--number` flag on `manage-solution-outline get-deliverable` (a recurrence of 2026-11-11-12-003),
  an `architecture search` rejection whose logged detail held only the argv (lesson 2026-11-15-18-004), a `no_sanction` commit (the
  third recorded recurrence of 2026-11-12-18-003) and a platform-runtime dispatch seam answering `prompt_not_found` with no marker
  logged (lesson 2026-11-15-18-005). The `no_sanction` refusal is now seen in three plans (PLAN-01, which produced the lesson, then PLAN-06 and
  PLAN-04), so the lesson's text may not be reaching the lane that commits; that is a lesson-handling question, not another
  recurrence line. PLAN-07's drain carried five more, all with their argv or cause: an invented `--number` flag on `manage-solution-outline get-deliverable`
  and an invented `query` verb on `manage-findings qgate` (recurrences of 2026-11-11-12-003; the `--number` rejection is the same one PLAN-04
  recorded), `--plan-id` placed before the `search` verb of `manage-architecture` (the router-position half of that lesson; PLAN-04's `architecture search`
  rejection is lesson 2026-11-15-18-004), a `no_sanction` commit (the **fourth** plan: PLAN-01, PLAN-06, PLAN-04, PLAN-07, so the lesson-handling
  question above stands), and one `phase_handshake` `drift` result at the 3-outline to 4-plan transition: the Q-Gate counts `qgate_open_count` and
  `pending_findings_blocking_count` were captured as 2 and observed as 0 after q-gate-validation resolved them (the safe direction). A search of the work logs of
  the six other archived plans found no other `phase_handshake` drift, so this is a single occurrence and a lead, not a lesson; the lane's two causes (a capture
  taken before q-gate-validation and never refreshed, or any change read as drift) are hypotheses. **PLAN-08's drain carried two more, read from its archived work log
  (`work.log:593`, `:609`) and both classified:** a `manage-status` exit 1 that is a guarded refusal working as designed (the pre-merge check "1 of 6 worktree-branch
  commit(s) since diverging from main carry no kind=change ledger row", the same class as lesson 2026-11-15-11-004), and an invented `--deliverable all` on `manage-tasks list`
  (an argparse rejection, `--deliverable` takes an int; a recurrence of 2026-11-11-12-003). PLAN-08's inbox carried no `no_sanction` cluster, so that count stays four plans (PLAN-01, PLAN-06,
  PLAN-04, PLAN-07); whether the `phase_handshake` drift recurred in PLAN-08 was not checked (its archived log was searched for `script_failure` only). — trigger: the next `analyze` of a landing, and any second `phase_handshake` drift.
- Four of PLAN-06's thirteen candidate lessons were the same defect class as lesson 2026-11-02-14-001 (a document states a rule more
  broadly than the code gives it, or describes a throttle as a deadline), one with an incomplete sweep. That lesson has now recurred
  in the PLAN-05 and the PLAN-06 drains; if PLAN-04 or PLAN-07 add another, the lesson's rule is not working as written and
  needs a review rather than another recurrence. PLAN-04's inbox carried none, but its analysis found the class four more times in
  the shipped text (`landings/PLAN-04.md` L1-6: a failure episode "logged once" while the delegate logs per attempt, a grace default
  "of zero" that is Quarkus-only, a minimum refresh interval that holds nothing back at its default, an injection that "fails at
  injection"), found by the layer-3 read and not by the lane or its review. The condition is met by that count; the review of the
  lesson is the operator's call. **PLAN-07 adds a sixth plan to it**: candidate message `-002` carried two more (ADR-0029, the security reference and the
  `LogoutTokenValidator` Javadoc said a backward clock step smaller than the freshness window is safe, while the replay guard purges at `iat + window`;
  `openid-discovery.adoc` promised a two-interval pickup bound the loader does not enforce), both found by CodeRabbit and not blocked by the lane's two
  reviews, and the analysis found it again in shipped text (`landings/PLAN-07.md` L3-1: the docs say the guard is taken "when a window is claimed", the code takes it for
  every miss; L2-3: the defaults list is incomplete). The rule is not working as written; a review of 2026-11-02-14-001 is the operator's call and is now
  overdue by this Watch's own test. **PLAN-08 adds a seventh plan**: its inbox carried none, but the analysis found the class again in text the lane had just rewritten to
  fix it (`landings/PLAN-08.md` L2-1 to L2-5: the Javadoc Logging paragraph says an interrupt leaves a `DEBUG` line and a recovering refresh logs `DEBUG`, the single-flight paragraph
  omits one outcome, "most failed attempts" overstates by site count, the 12.2 rationale omits the unchecked stored ID token), and the lane's own advisories named two of them as left unfixed.
  Note: lesson 2026-11-02-14-001 is not in the lessons store (see the Open Defect of 2026-11-17), so the review has nothing to review until it is restored or re-promoted.
  — trigger: any decision on reviewing 2026-11-02-14-001.
- The mirrored-list defect class (lesson 2026-11-15-11-001) recurred in PLAN-04: a hand-kept key list in the Quarkus orphan test
  mirrored `ClientRuntimeConfig` and was caught only by CodeRabbit (fixed in `36b61921`); the production side still keeps
  `ClientConfigMapper.DISCOVERY_CACHE_SETTINGS` as a second list, guarded by that test. PLAN-04 and PLAN-06 ran in parallel, so the
  lesson did not exist when PLAN-04's lane ran. PLAN-07 ran after it existed: its inbox and the leaf's findings carry no mirrored-list instance
  (the orchestrator did not search the diff for one). **PLAN-08 recurred it twice, both caught by CodeRabbit and fixed** (inbox `-007`, `-009`): the Quarkus mapper kept
  `DISCOVERY_CACHE_SETTINGS` beside a parameter-name map mirroring `checkSettings` (now one exhaustive `propertyOf(Setting)` switch, `ClientConfigMapper:126-133`, re-read),
  and the guard test for the orphan check built its expected set from the same constants it looped over, so it could not catch an omission (now derived by reflection over
  `ClientRuntimeConfig`, the leaf's reading). The second is a refinement the lesson's text does not carry: a completeness test needs an oracle independent of the registry it checks.
  — trigger: the PLAN-03 landing, which is the next to run with the lesson; if the lesson is restored or re-promoted, add the independent-oracle sentence.
- The Standing Constraint "never gate on `test-compile` or `test`" in every spec rests on a premise main no longer holds:
  PLAN-05's `AGENTS.md` rewrite says the fixtures resolve in the reactor through `<type>test-jar</type>`. The constraint stays
  safe; whether `test-compile` now builds is not shown by reading, and the reactor and published classpaths can diverge.
  PLAN-07's PR body claims whole-tree `test-compile` and `test` (5139) green (the lane's statement, not re-run). — trigger: the next plan's
  pre-push gate, and the PLAN-03 outline (each re-reads `AGENTS.md` at HEAD). PLAN-02's
  lane hit the same defect from the other side: the reactor-root `module-tests` / `test-compile` canonical needed a patch in the
  operator's plugin cache (cached 2114 and 2115 both carry it), which is lost on the next plugin update; a recurrence of lesson
  2026-11-13-06-005, so the fix has to land in the marketplace source.
- PLAN-01 native claims rest on the lane: the native IT 3/3, the removal controls and the constructor-only
  reflection sufficiency were not shown by reading. — trigger: the first native build of a consumer on 0.9.10
  (API Sheriff PLAN-34).
- A TokenSheriff change in 0.9.10 can change API Sheriff behaviour on bump: the 0.9.9 notes list
  JWKS staleness bound and re-discovery (#844), `HttpJwksLoader` status truth (#830), issuer policy
  on cache hits (#833) and DPoP/step-up changes. — trigger: each landing here; the landing report
  names what API Sheriff must re-measure. PLAN-05 adds: `MapRepresentation` record shape and member order, the new
  `SIGNATURE_ONLY_VERIFIED` event type, the opt-in `report-external-validator` / `disclose-issuer` keys (defaults
  unchanged), and the `logout` package contract (`exp == now` refused, fractional `NumericDate` kept, `"nonce":null` refused).
  PLAN-02 adds: `ClientConfiguration.validateScopes` is stricter (a configured scope with a non-RFC character now fails build);
  an empty per-call scope override is refused, not "send no `scope`"; a missing PAR endpoint is a generic
  `ClientProtocolException`, with no dedicated reason; `QueryResponseModeAuthorizationRequestBuilder` (a subclass overriding the 3-arg
  `build`) is bypassed by the options and pushed paths; the default `response_mode` is unchanged and `CLIENT-14` now permits an
  opt-in `query`. PLAN-06 adds: both new `HttpJwksLoaderConfig` opt-ins are off by default and the default behaviour is unchanged by
  the zero-request tests; what changes is the in-flight guard now taken unconditionally in the periodic cycle, the new `INFO` id 10
  and `WARN` id 173 records, the `5..3600 s` bounds, `JwksLoader`'s Javadoc, and the documented `kid`-spray semantics (a spray in
  every window defers on-demand pickup entirely; the periodic refresh is the only guaranteed path); if API Sheriff supplies its own
  executor, the hand-off guard leak (PLAN-07 deliverable 7) applies to it. PLAN-04 adds: `RefreshResultVerifier.verify` is a static
  public method in `flow`, and its bound-subject step compares the refreshed access token's `sub` (API Sheriff passes its own bound
  subject; `TokenLifecycleManager` passes `null` and leaves that check to `StoredToken.refreshed`); a refusal is a
  `RedeemedValidationRefusalException` or the new `RedeemedIdentityRefusalException` (an `IllegalStateException`, with a transient
  redemption that degrades to `rotationUnknown()` after deserialization); an unbound session with an access token without a `sub`
  adopts the ID token unchecked against any principal; `TokenLifecycleManager` now quarantines only on those two carriers; the new
  `CachingDiscoveryResolver` is opt-in, requires a TTL, hands out a copy per call, fails closed after the grace period, blocks joiners
  on a slow refresh, logs the delegate's `DISCOVERY_RESOLVED` at INFO once per TTL and a failing provider once per attempt, and is
  not validated for value at Quarkus startup (PLAN-08 deliverable 1); the new `sheriff.client.discovery-cache.*` keys fail an orphan.
  PLAN-08 adds (`landings/PLAN-08.md`): `CachingDiscoveryResolver` now validates `ttl`, `grace-period`, `min-refresh-interval` and `jitter` at Quarkus startup for an enabled cache (an invalid or
  unparseable value fails naming the property; a disabled cache with the key still fails as an orphan) and refuses a value over 365 days at construction; an unexpected runtime exception from the delegate is
  recorded as a failed refresh (status cause class only; the stale policy and backoff apply) instead of escaping with no state; a caller whose interrupt flag is set while the delegate fails aborts the refresh with no
  state and keeps the flag, and joiners re-evaluate; a superseded claim is aborted so no joiner is handed an expired entry; injecting `CachingDiscoveryResolver` with the cache disabled now fails at runtime init (a
  new build step and `TokenSheriffClientRecorder`), where it failed at first use; the delegate's `INFO` line stays one per TTL and one per failed attempt, now stated in the docs; the unbound-`sub` decision is
  keep-and-document, and `TokenLifecycleManager` still passes `null` to `verify`; `AGENTS.md` lists one new approved broad-catch site.
  PLAN-07 adds: `Bearer` scheme matching is case-insensitive and the `Authorization` value is trimmed (OD-1, OD-2); a token with an internal space or a
  control character is answered 400 `INVALID_REQUEST` and the cookie is not consulted; a quoted all-blank cookie value is now no token; a required scope
  outside the RFC 6749 §3.3 grammar (or more than 64) throws `IllegalArgumentException` from the challenge build (OD-3) and, on the scope-violation path of
  an annotated method, nothing catches it (`landings/PLAN-07.md` L3-2); scopes in the challenge are sorted; a backslash in a role or group of the
  privileges header is escaped; `ErrorTextSanitizer.escapeNonPrintableAscii` escapes the backslash, which API Sheriff's `renderForMessage` does not (OD-4),
  and its hex case matches; `LogoutTokenValidator.MAX_FRESHNESS_WINDOW` is 100 years and a `numericDate` beyond 17 integer digits is refused as the owning
  check; an unchanged JWKS discard logs at DEBUG; the client back channel gets the `Content-Length` pre-check, the validation module's failure text and a per-chunk
  list instead of a `ByteArrayOutputStream`; `BoundedBodyHandlers` and `BoundedContentBodyHandler` are gone. API Sheriff's own deletions are its plan.
- PLAN-07's drain recurred four finalize-tooling lessons with new detail, none worth another lesson: `weakened_test` re-raised six accepted test-class-split flags
  at every later head (a split with no rename reads as a weakened test; lesson 2026-11-11-12-002 has the cause for raw U+2028 and the carry-forward ask, and the
  split case is a second trigger for the same carry-forward); a `-Ppre-commit` verify rewrote imports in about 60 client test files the plan did not touch, and reverting
  that churn made the freshness stamp stale (lesson 2026-10-31-12-005); each fix commit after the PR opened re-staled both review steps and the pre-push gate, and a step
  whose last record is `loop_back` replays as a full round (lessons 2026-11-13-06-008 and 2026-11-15-18-002). All four are the lane's account of its own logs, not recounted here.
  — trigger: PLAN-03's landing; if the split-flag or the stale-stamp case repeats there, add the second trigger to the owning lesson.
- PLAN-08's `weakened_test` rows had a different cause from the split flags above and were promoted as lesson 2026-11-17-06-001: the check's pattern `\.assert[A-Z]\w*` (`_review_support_integrity.py:66`,
  plugin 0.1.2114) counts `import static ...Assertions.assertAll` lines, so an OpenRewrite wildcard-import rewrite read as a lost assertion; five accepted rows in one plan (two reviews times
  two heads, then a fifth), re-counted here as 49 to 60 call sites and 9 to 2 gate-pattern lines. It recurs in any plan that touches test imports until the counter changes; PLAN-03 and PLAN-10 edit client tests.
  — trigger: PLAN-03's and PLAN-10's landings; if the row recurs there, the checker fix is the operator's to route to the marketplace source (as for lesson 2026-11-13-06-005).
