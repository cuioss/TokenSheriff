# Settled narrative — api-sheriff-seams

> Live-epic sibling of `epic.md`. Each section below is the verbatim body of an `epic.md` section whose subject is closed, relocated by the `cleanup` verb on 2026-11-18 (after PLAN-01 to PLAN-11 shipped). `epic.md` keeps a pointer naming the heading. Nothing here was edited; a retraction, a refutation and a "do not re-derive" note stay reachable.

## Open Defects settled through PLAN-10 (audit candidate and the PLAN-01 to PLAN-06 landing and residual entries)

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

## Open Defects settled through PLAN-10 (the PLAN-07 and PLAN-08 landing and residual entries)

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

## Watch retired at the PLAN-07 landing (PLAN-01's under-declared test-impl-mapping and LogMessages surface)

- PLAN-01 added `doc/developer/test-impl-mapping.adoc` rows that PLAN-04 and PLAN-07 also declare — the landing
  under-declared its surface. PLAN-05, PLAN-02 and PLAN-06 each met it on rebase and shipped with the other plans' rows present
  (PLAN-06's landing does not say whether a conflict occurred). PLAN-04's merge diff does not touch the file, so the watch is
  **narrowed to PLAN-07**, which declares it. `doc/LogMessages.adoc` was edited by PLAN-04 beside PLAN-06 and both rows are present on
  main (INFO `4`, WARN `115` and `116` here; INFO `10` and WARN `173` there); neither landing says whether a conflict occurred.
  **Retired at the PLAN-07 landing**: that merge edits `test-impl-mapping.adoc` (+27/-15) and `LogMessages.adoc` (the `173` row), PLAN-04's rows are
  still on main, and neither the landing nor the diff shows a conflict (the landing does not say; not checked beyond the diff).

## Open Defects settled through PLAN-11 (the PLAN-03 and PLAN-09 landing and residual entries and the PLAN-09 owed items)

- 2026-11-17 — PLAN-03 landing `plan-03-client-key-material-config-validators-006.md` is **incomplete** (`inbox landing-check`: `complete: false`; `unresolved_review_threads`, `review_body_findings_total`,
  `review_body_findings_handled` and `sonar_open_new_issues` are `unknown`; no finalize step records them). No claim to contradict. The lane's residue text gives what it observed (0 pending `pr-comment` findings at the merge
  gate, four stored CodeRabbit findings fixed and answered, a Sonar round trip with 0 new-code issues that does not count hotspots). The provider re-read: `review-gate --pr-number 915` returns 0 open threads, 5 coderabbit body findings
  (`nitpick-1`, `-2`, `inline-1..3`), `handled` 0, `merge_allowed: false, reason: body_findings_undisposed`; the three threads are resolved and carry the lane's replies. `handled` is not re-verified. `ci pr comments`
  reports `unresolved: 5` of 11 beside the gate's 0 unresolved threads; the difference was not reconciled per item. Sonar is `undecidable` (`head_revision_unavailable`, analysis revision `67672997`, the PR's last head),
  recorded as unknown. These five findings are **not** in PLAN-10 deliverable 5 (its list ends at #912); whether to add #915 is the operator's call.
- 2026-11-17 — PLAN-03 layer-1, layer-2 and layer-3 residuals, reported to the operator at once and **not staged** (a layer-3 defect is staged only on the operator's word; see `landings/PLAN-03.md`, the code points
  re-read at `dfab40cb`, the leaf's test-side points not): (1) the validator refuses more than the spec's bound (`EgressHostValidator:92`, `:101-123`, `:146-163`), and on the client path `ClientConfigMapper:114-116` passes
  elements on untrimmed, so `keycloak, dex` fails the mapping where ` dex` was stored and never matched; the PR narrative's "only a port-carrying element newly fails startup" is wrong in the same direction
  (`IssuerConfigResolver:555-563`); the Javadoc of both callers states the wider refusal (`ClientConfigMapper` says entries are not trimmed, `IssuerConfigResolver:543-548` says any other entry is refused), but the user-facing text
  (`openid-discovery.adoc`, the requirements) was not checked for it; (2) `[fe80::1%eth0]` is refused by the `%` rule and may have been a working entry
  (not shown by reading); (3) `ClientConfiguration` keeps the configured spelling while the other three builders store the canonical form (`ClientConfiguration:452-464`, `ClientConfigurationTest:908-914`);
  (4) the `CLIENT-30` row of `test-strategy.adoc:603-621` omits `EgressHostValidatorTest`, the three builder tests, `IssuerConfigResolverEgressHostTest`, `BackChannelEgressPolicyTest` and the `TokenSheriffClientProducerTest` case;
  (5) per-site refusal coverage at the three builders is null, blank, port and `[::1]:port` only (the leaf's reading); (6) `HttpJwksLoaderConfigTest` (537 lines), `TokenSheriffClientProducerTest` (523) and
  `ClientConfigurationTest` (968) exceed 500 lines with no reason in the PR body; (7) `PemKeyPairLoader:180-184` follows symlinks and can block on a special file swapped in after the check (info, operator-supplied path), and holds key
  text in unwipeable heap strings (`:198`, info); (8) `PemKeyPairLoaderAdversarialTest`'s off-curve case assumes the JDK's `KeyFactory` accepts the point (not shown by reading); (9) `DpopProofGenerator:188-191`
  does not check that its `KeyPair` halves match and `PrivateKeyJwtAuth:88-96` has no RSA floor, so the loader is the only check and only for loaded pairs (a statement for consumers, not a defect); (10) the `CLIENT-30` traceability row sits out of
  order (cosmetic). Nothing here has run. **Staged as PLAN-11 deliverable 4 on the operator's word (2026-11-17); landed in #921 (see "PLAN-11 landed" below for what closed and what remains)**, except the `ClientConfigurationTest` split, which is PLAN-10's deliverable 4 (5); the line numbers above were re-read at `dfab40cb` and are re-read again at PLAN-11's outline.
- 2026-11-17 — PLAN-09 landing `plan-09-public-helper-residuals-014.md` is **incomplete** (`inbox landing-check`: `complete: false`; `unresolved_review_threads`, `review_body_findings_total`,
  `review_body_findings_handled` and `sonar_open_new_issues` are `unknown`; no finalize step records them). No claim to contradict. The lane's residue text gives what it observed (Sonar 0 new-code issues confirmed at head `0baf5776`,
  issues only, hotspots not counted; eight CodeRabbit findings fixed in-run; every inline thread answered and resolved). The provider re-read: `review-gate --pr-number 916` returns 0 open threads, 8 coderabbit body findings
  (`nitpick-1`, `inline-1..7`), `handled` 0, `merge_allowed: false, reason: body_findings_undisposed`; `handled` is not re-verified. `ci pr comments` reports `unresolved: 9` of 23 beside the gate's 0 unresolved threads; the difference was not
  reconciled per item. Sonar is `undecidable` (`head_revision_unavailable`, analysis revision `0baf5776`, the lane's last head), recorded as unknown. These eight findings are **not** in PLAN-10 deliverable 5 (its list ends at #912, and #915's five are
  not in it either); PLAN-10 is running, so adding them means a new plan or the operator settling them on the PR. **Staged as PLAN-11 deliverable 5 (with the five of #915) on the operator's word (2026-11-17); landed in #921, whose dispositions for #915 and #916 were not shown by reading (see "PLAN-11 landed" below).**
- 2026-11-17 — PLAN-09 layer-2 and layer-3 residuals, reported to the operator at once and **not staged** (see `landings/PLAN-09.md`; the code points re-read at `67f8e480`, the leaf's test-side points not): **(7) the memory bound was weakened**:
  the spec's standing constraint keeps `COMMONS-17`'s ceiling plus one in-flight chunk, and the merge keeps one array that doubles (`BoundedBodyHandler:362-374`) and rewrites `COMMONS-17`, `transport.adoc` and ADR-0031 to "below twice the ceiling plus the
  chunk in flight" (`doc/commons/requirements.adoc:152-165`); the review thread carries an "Operator decision" for it (the lane's record), and the spec's own clauses conflict (D3's "state the factor" option against the constraint); in effect the worst-case streaming
  peak doubles on a read of a remote response. Five spec items **not delivered and not in the lane's report**: (1) ADR-0031's "Changed default behaviour" still omits the 100-year freshness window, the WARN to DEBUG change, the health-check log change and the
  chunk list (`ADR-0031:155-187`); (2) `MAX_CEILING` has no `@since` (`BoundedBodyHandler:94-97`); (3) the builder Javadoc of `unknownKidRefreshWindow` does not state the burned window (`HttpJwksLoaderConfig:933-957`, file not in the merge);
  (4) no non-ASCII scope in the new tests; (5) no test delivers several buffers in one `onNext` (`BoundedBodyHandlerTestSupport:163-167`); (6) the retention bound (`BearerChallenge:165`) has no test that fails without it (by reading). Also: three near-identical
  refusal tests are not parameterized (`BearerScopeValidationBuildStepTest:89-121`); an `Error` from the executor's `execute` leaves the in-flight guard held (`HttpJwksLoader:903-906`, info); the lane's thirteen advisory findings (the `Q-201` truncation unpinned, Javadoc literals
  `64` and `8192`, no control-character scope test, the ADR-0030 and discovery-doc "first miss" wording, the ADR-0031 peak without the in-flight chunk, the programmatic-set omission, an unqualified Javadoc claim not re-read); not shown by reading: that the build step
  runs in Quarkus augmentation (the boot test was not run) and the JDK `Content-Length` observation (`requirements.adoc:163`). **Staged as PLAN-11 deliverables 1 to 3 on the operator's word (2026-11-17); landed in #921 (the doubling array kept and its bound recorded and pinned, the five wording items and the `Error` release delivered, the tests present; see "PLAN-11 landed" below)**; the memory bound (7) is deliverable 1 and an operator decision at its outline.
- 2026-11-17 — Owed by the PLAN-09 lane, **outside the epic tree, not done here**: (a) one `architecture enrich best-practice --module maven` call with the verbatim hint "judge a Maven build by its exit status and its failing-test list, not by the log parser's finding count, because
  frontend build output (npm warnings, Jest summary, timing lines, WSL path diagnostics) is read as build errors" (inbox `-013`; it would land as an uncommitted diff on main; the cause is the log parser, whose fix would make the hint unnecessary); (b) structured rows in
  `doc/developer/test-impl-mapping.adoc` for the client round trip (to `commons.http`) and the deployment-module tests (to `TokenSheriffProcessor`), or a phase-4 profile change for Javadoc-only targets, because the same `module_mapping_validator` rows were raised in four of five
  deliverables and each needed an operator disposition (inbox `-012`; the lane's account, the validator rows not re-read). Both are the operator's. **Staged as PLAN-11 deliverable 6 on the operator's word (2026-11-17); landed in #921 only in part (the mapping rows and the architecture hint; the flake was not run, see "PLAN-11 landed" below)**, with the Playwright flake `accessibility-wcag-compliance.spec.js:60`; the architecture target is settled at its outline because
  `.plan/project-architecture/` has no `maven` directory (a listing at `a428e615`), which the lane's `--module maven` call assumed. **Item (a) and the second item were taken up by PLAN-11 (#921)**: the architecture hint was written against `token-sheriff-parent` (the diff touches `.plan/project-architecture/_project.json` and `token-sheriff-parent/enriched.json`, names re-read, content not opened) and the mapping document gained the deployment-module rows; see "PLAN-11 landed".
