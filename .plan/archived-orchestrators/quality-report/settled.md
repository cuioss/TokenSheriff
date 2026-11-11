# Settled narrative

Entries relocated verbatim out of `epic.md` by the `cleanup` verb (2026-11-06, operator-confirmed). Each subject is closed; each is reachable from a pointer left at its origin.

## Executor drift (resolved)

- Executor drift: RESOLVED as of PLAN-21 (2026-11-02): `executor_version` 0.1.2046 equals `plugin_version`
  0.1.2046 after the operator's marshall-steward run (#848). It affected PLAN-06, 07, 12 and 20. Plans started
  while the regeneration was in flight may still show mixed versions; PLAN-08 is one of them.

## Lesson store 2026-11-02

- Lesson store, 2026-11-02: the local store holds 7 lessons. `2026-10-31-12-003`, `-006`, `-007`, `-009` and the
  six `2026-10-31-13-00x` theme lessons recorded as filed on 2026-10-31 are no longer there, and I do not know
  why (no trace in this ledger's log beyond their filing). Their drafted bodies are still in the session
  scratchpad. DROPPED 2026-11-05 on the operator's say-so ("ignore", on the premise that plan-marshall fetched them;
  the premise is unconfirmed here). Not re-filed.

## Sonar read 2026-10-30 (PLAN-01 to PLAN-05 PRs)

- Sonar, verified 2026-10-30 by a direct unresolved-issue read per PR (`sonar read_open_issues` returned
  `undecidable` / `head_revision_unavailable` for all four merged PRs; the quality gate read OK on each):
  #832 has 1 open issue, INFO `java:S1135` (the TODO marker in `TokenLifecycleManager.java`, the same one
  recorded as a PLAN-02 residual), neither fixed nor dismissed; #833, #834 and #835 have 0 open. The PLAN-02
  spec requires 0 open or every issue dismissed, so that one INFO issue is an unmet criterion, minor in
  weight. The read is a PR-scoped snapshot of the last analysis, not a re-run.

## Sonar read 2026-10-30 (PLAN-06 and PLAN-07)

- Sonar, PLAN-06 and PLAN-07, verified 2026-10-30 the same way: quality gate OK on #840 and #844, direct
  `resolved=false` query returns 0 open on both (PLAN-07's landing also reports `new_code_issue_count=0`
  confirmed; PLAN-06's carries no Sonar keys). PR-scoped snapshots, not re-runs.

## Sonar read 2026-11-01 (PLAN-12 and PLAN-20)

- Sonar, 2026-11-01: gate OK and 0 open on #846 and #847; the project-wide `java:S1135` query returns 0, so
  the one open INFO on #832 recorded above is resolved (by PLAN-20). PR-scoped snapshots, not re-runs.

## Sonar read 2026-11-02 (PLAN-21, 44 open on main)

- Sonar, 2026-11-02: gate OK and 0 open on #849. The project-wide query on the default branch returns 44 open
  issues (23 MAJOR, 13 MINOR, 5 CRITICAL, 3 INFO; 41 code smells, 3 bugs; rules `java:S8714` x11, `S5778` x7,
  `S4449` x4, `S8688`, `S8786`, `S3776`, `S2589` x3 each, others), the newest created 2026-10-10, so all predate
  this epic's plans. None belongs to a plan's own PR, but the standing "fix every Sonar finding immediately"
  policy covers them. Owned since 2026-11-04 by PLAN-23 (staged). Done when a `branch=main` query returns 0 open
  or only issues with a recorded dismissal; the orchestrator re-reads it when PLAN-23 lands.
  UPDATE 2026-11-05: PLAN-23 landed as #855. Read through the Sonar REST API at the merge analysis (`0c56583e`,
  2026-11-05T07:51:36Z): `main` has 1 open issue, `java:S8786` at `JwtTokenTamperingUtil.java:450` (key
  `AZ7xhrgAXGjoVdkwztNZ`; the possessive-quantifier rewrite did not clear it). Owned by PLAN-15 (folded into
  deliverable 1). Gate `OK`. Retire this item when a `branch=main` read returns 0 or a recorded dismissal.

## Incomplete landings with an unstamped effort (PLAN-01 to PLAN-19)

- 2026-10-29 — Incomplete landing `plan-01-jwks-loader-lifecycle-009.md` (PLAN-01): `landing-check`
  reports `complete: false`, missing key `effort` (written as `unknown` by the producer). Drained and
  reconciled as far as it goes.
- 2026-10-30 — Incomplete landing `plan-05-jwe-ecdh-es-001.md` (PLAN-05): missing key `effort` (never
  stamped, written as `unknown`). Drained and reconciled as far as it goes. Fourth producer with an
  unstamped `effort` (PLAN-01, 02, 04, 05); only PLAN-03 carried it.
- 2026-10-29 — Incomplete landing `plan-04-quarkus-startup-health-009.md` (PLAN-04): missing key `effort`
  (never stamped, written as `unknown`). Drained and reconciled as far as it goes. Third producer in a
  row with an unstamped `effort`; PLAN-03 was the exception.
- 2026-10-29 — Incomplete landing `implement-plan-02-refresh-family-rotation-006.md` (PLAN-02): missing
  key `effort`; the plan's `refresh-runtime-stamp` could not refresh (`model_unknown`). The message also
  ends before `emit-landing` and `archive-plan` recorded an outcome.
- 2026-11-01 — Incomplete landing `plan-12-benchmark-build-006.md` (PLAN-12): missing key `effort` (never
  stamped, written as `unknown`). Drained and reconciled as far as it goes. Fifth of the nine landings so far
  without it (PLAN-01, 02, 04, 05, 12); PLAN-03, 06, 07 and 20 carried it.
- 2026-11-04 — Incomplete landing `plan-09-dpop-parser-hardening-007.md` (PLAN-09): `landing-check` reports
  `complete: false`, missing key `effort` (written as `unknown`; the landing says the stamp was absent). Drained and
  reconciled as far as it goes. Sixth known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09). The
  PLAN-08 and PLAN-21 landings were not re-checked for it.
- 2026-11-05 — Incomplete landing `plan-10-dpop-binding-stepup-010.md` (PLAN-10): `landing-check` reports
  `complete: false`, missing key `effort` (the runtime stamp could not be read; written as `unknown`). Drained and
  reconciled as far as it goes. Seventh known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09, 10).
- 2026-11-05 — Incomplete landing `plan-14-client-adversarial-tests-005.md` (PLAN-14): `landing-check` reports
  `complete: false`, missing key `effort` (never stamped, written as `unknown`). Drained and reconciled as far as it
  goes. Eighth known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09, 10, 14). PLAN-13 carried it.
- 2026-11-06 — Incomplete landing `plan-22-residual-docs-tests-adrs-001.md` (PLAN-22): `landing-check` reports
  `complete: false`, missing key `effort`; `refresh-runtime-stamp` at landing returned `refreshed: false, reason:
  model_unknown`. Drained and reconciled as far as it goes. Ninth known producer with an unstamped `effort` (PLAN-01,
  02, 04, 05, 12, 09, 10, 14, 22). The landing's functional-review and code-review `operator_override` acceptances name
  no finding and the plan workspace is gone, so what was accepted cannot be read back.
- 2026-11-06 — Incomplete landing `plan-16-keycloak-it-assertions-005.md` (PLAN-16): `landing-check` reports
  `complete: false`, missing key `effort` (`refresh-runtime-stamp`: `model_unknown`). Drained and reconciled as far as it
  goes. Tenth known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09, 10, 14, 22, 16).
- 2026-11-06 — Incomplete landing `plan-15-validation-test-gaps-009.md` (PLAN-15): `landing-check` reports `complete: false`, missing
  key `effort`. Drained and reconciled as far as it goes. Eleventh known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09,
  10, 14, 22, 16, 15). PLAN-13 carried it.
- 2026-11-06 — Incomplete landing `plan-18-doc-accuracy-008.md` (PLAN-18): `landing-check` reports `complete: false`, missing key
  `effort` (written as `unknown`; the landing says the stamp was never written at init). Drained and reconciled as far as it goes.
  Twelfth known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09, 10, 14, 22, 16, 15, 18). PLAN-24 carried it. The
  landing also omits the `emit-landing` and `archive-plan` steps (not yet recorded when it was assembled).
- 2026-11-06 — Incomplete landing `plan-17-javadoc-examples-007.md` (PLAN-17): `landing-check` reports `complete: false`, missing key
  `effort` (written as `unknown`; the plan carries no effort stamp). Drained and reconciled as far as it goes. Thirteenth known producer
  with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09, 10, 14, 22, 16, 15, 18, 17). The landing lists `emit-landing` as
  `in_progress` and `archive-plan` as `pending` (written before either recorded), and its token total includes a known double-count of
  about 186k execute-phase tokens.
- 2026-11-07 — Incomplete landing `plan-19-doc-structure-hygiene-012.md` (PLAN-19): `landing-check` reports `complete: false`, missing key
  `effort` (written as `unknown`). Drained and reconciled as far as it goes. Fourteenth known producer with an unstamped `effort` (PLAN-01,
  02, 04, 05, 12, 09, 10, 14, 22, 16, 15, 18, 17, 19). The landing lists `emit-landing` and `archive-plan` as `pending`.

## Per-plan landing residuals (PLAN-01 to PLAN-23, dispositioned 2026-11-07)

- PLAN-13 and PLAN-14 residuals (2026-11-05; verified in the merged source or the PR unless marked): (1) PLAN-14
  merged under a `barrier-ask-override` (CodeRabbit rate-limited, head `15eb888a`); PLAN-13 recreated its PR (#859 to
  #861) to get a review after a quota refusal and had a merge-queue ejection on a hung `e2e-tests` job (landing
  message). (2) PLAN-14's discovery change now refuses a response without an `application/json` `Content-Type` and a
  non-https endpoint in an https document: a provider that served discovery differently would now fail with
  `DISCOVERY_FAILED`; not tested against a real provider here. (3) PLAN-13: `maxHttpResponseSize` is undocumented in
  two architecture docs, the `ParserConfig` Javadoc on `maxPayloadSize` is unclear, an oversized-content test also
  passes on a missing `jwks_uri`, two fixed reason strings omit a cause (landing message; docs and strings not re-read);
  all FOLDED into PLAN-25 deliverable 7, with the log-record sanitisation sweep as deliverable 6. (4) PLAN-14: a blank
  optional endpoint is refused, a quoted numeric `expires_in` is accepted (pinned as observed), no ADR for the
  non-https refusal, and only discovery checks the response `Content-Type`: FOLDED into PLAN-25 deliverable 7. (5)
  Post-merge JMH benchmark runs were still pending when each plan's wait ended; not re-read. (6) The metrics total of
  PLAN-13 is a mixed population and `model` is self-reported (landing message). (7) PLAN-13's functional review closed
  with one deliverable "met with residuals" and nine advisory code-review notes (landing message; only the ones it
  names are known).
- PLAN-23 residuals (2026-11-05; verified in the merged source, the PR or Sonar unless marked): (1) one Sonar issue
  remains on `main` (`S8786`, see the Sonar item above); folded into PLAN-15. (2) `StepUpHandler` gained a new
  fail-closed guard (a null or blank configured redirect URI throws `IllegalArgumentException`); it narrows
  behaviour, and PLAN-22 edits the same file next. (3) Merged under a `barrier-ask-override` (`review-barrier-gap`,
  head `8986a132`; CodeRabbit quota); both its earlier comments were fixed (landing message). (4) Unfixed advisories
  (landing message): the `TokenBuilder` counter-field comment, its constructor Javadoc and the message of commit
  `627f2b5b` still describe only `MISSING_CLAIM` counting although `mapClaim` now also counts a mapper-thrown
  exception under its own event type (the Javadoc half is a one-line fix in `TokenBuilder.java`, which no staged spec
  declares; candidate: PLAN-22 deliverable 1 wording); `UncheckedThrowBoundaryTest` EXEMPT comments name
  `extractClaims` where the sanctioned broad catch is `mapClaim`. FOLDED into PLAN-22 deliverable 1 on 2026-11-05
  (both read at `TokenBuilder.java` lines 60 and 67-68 and `UncheckedThrowBoundaryTest` lines 199-204; expected surface
  +2 entries). (5) `NativeTokenValidatorProducerIT` was not run
  locally and whether the native IT ran in CI is unconfirmed (its CI `integration-tests / test` job is green). (6)
  The module-tests arm of the pre-push gate degraded again (seventh plan); the whole-tree `verify` ran 3680 tests per
  the landing (not re-run). (7) DONE 2026-11-05 on the operator's instruction (run on `main`, module `token-sheriff-validation`, which exists; the
  hint text was unchanged; it left `.plan/project-architecture/_project.json` and `.../token-sheriff-validation/enriched.json`
  modified and uncommitted in the main checkout): the preference emitter owed one `architecture enrich` call on `main`,
  target module `token-sheriff-validation`, verb `best-practice`, hint (verbatim): "Prefer to suppress Sonar java:S1612
  (replace lambda with method reference) in token-sheriff-validation tests when the suggested method reference is
  ambiguous between assertDoesNotThrow(Executable) and assertDoesNotThrow(ThrowingSupplier) because the target method
  is overloaded (for example CountDownLatch.await): the suggestion does not compile, so the lambda stays and the rule
  is suppressed with a stated reason rather than the code being rewritten." The module attribution was a Sonar project
  key, so confirm the target at pickup; not run by the orchestrator (it writes architecture data outside the epic
  tree).
- PLAN-10 residuals (2026-11-05; verified in the merged source or the PR unless marked): (1) the three client-side
  DSL-JSON sites that catch only `IOException` — `DiscoveryResolver` (line 191), `ParClient` (line 147),
  `UserInfoClient` (line 261) — are NOT covered by the PLAN-15 fold, which is validation-only; whether each leaks the
  unchecked exception on a truncated escape is untested. CARRIED by PLAN-25 deliverable 2 (operator, 2026-11-05;
  not PLAN-14). (2) PLAN-23 (running) declares `RefreshFailureClassificationTest.java`, which PLAN-10 changed (+57) without
  declaring it: an overlap the gate did not predict; PLAN-23 rebases over it. PLAN-10's spec is a record and is not
  edited. (3) The PR merged past an unreviewed head under a `barrier-ask-override` (`review-barrier-gap`): CodeRabbit
  was quota-limited on `310669e`, which holds only its own two suggested fixes (landing message; not independently
  re-read); gate commit `25e892e` (a comment-only suppression marker) post-dated the reviews. (4) One ADR candidate
  was proposed and never created (landing message; PLAN-22's ADR item is the natural home, not acted on). (5) Pre-push
  gate and token figures: `total_tokens` is a mixed population and a phase figure is over-counted by about 125
  thousand (landing message). (6) A PLAN-10 documentation edit (`client-auth-code-pkce.svg`) was read only as a
  diff stat, not rendered.
- PLAN-11 residuals (read in the merged source 2026-11-04 unless marked): (1) `client_id` is still mapped by
  `IdentityMapper`, which stringifies any non-string value, and `AuthorizedPartyValidator` (line 100) falls back to it
  when `azp` is absent and the RFC 9068 fallback is on, so a numeric or list-typed `client_id` on an `azp`-less token
  can still satisfy the configured-id comparison PLAN-11 closed for `azp`. Operator decision at PLAN-11. STAGED as
  PLAN-24 on 2026-11-04 (strict mapping, an `iss`/`sub`/`jti` sweep, tightened `azp` assertions); retire this item
  when PLAN-24 ships. (2)
  `ClaimValue.isNotPresentForClaimValueType()` now has no production caller, only `ClaimValueTest`: dead code, a
  cleanup candidate (CARRIED by PLAN-25 deliverable 3). (3) The three code-review advisories (landing message, not independently verified): the
  non-string `azp` tests assert only the exception supertype; a `StrictStringMapper` rejection is not counted or
  logged beyond the `MISSING_CLAIM` exception it throws (RESOLVED 2026-11-05: PLAN-23 #855 made `TokenBuilder.mapClaim`
  count a mapper-thrown `TokenValidationException` under its own event type; read in the merged source; it is still
  not logged); `client_id`
  as in (1); items (1) and the first advisory are carried by PLAN-24. (4) `.plan/marshal.json` line 133 sets
  `final_merge_without_asking: true`, which conflicted with the `CLAUDE.md` rule against auto-merge. The operator
  decided on 2026-11-04 to change `CLAUDE.md` to allow the merge. RESOLVED: PR #857 merged as `e8d28f8a` on
  2026-11-04, through the merge queue, with `license/cla` still pending (the operator said that check is optional
  and told me to merge; the CLA bot read the `Co-Authored-By` trailer as an unsigned contributor, unverified).
  Sourcery's "needs a human reviewer" remark was answered on the PR as an accepted trade-off. Plans launched from
  now on read the new rule; the item can be deleted at the next compaction. (5) The native-image metadata for `StrictStringMapper` (`reflect-config.json`) was not exercised in a
  native build here. (6) Pre-push `test-compile` and `module-tests` arms degraded again (sixth plan); 120 auto-filed
  `build-error` findings blocked the first merge dispatch until rejected. Whole-tree `verify -Ppre-commit` (3621
  tests per the landing) not re-run.
- PLAN-09 residuals and review (landing message + PR #851 body; the items marked "read" were corroborated
  2026-11-04): merged after a hand-accepted scope-creep warning (six files outside the declared scope, threshold
  five). Surface delta `expansion_detected` (11 added, 1 missing). The one unfixed advisory, as the landing words it
  (the non-EC-key and null-domain-parameters guards in `SignatureVerificationUtil.checkEcdsaComponentRange` are
  shadowed by `initVerify`), is REFUTED in part: read in the merged source, the check runs BEFORE `initVerify`, and
  `SignatureVerificationUtilTest` has `shouldRejectNonEcKey` and `shouldRejectNullSignature`. Still open and
  unverified: no test names the `params == null` branch (a defensive guard, hard to reach), and the claim that
  `PsychicSignatureAttackTest` cannot fail without the explicit range check (the JDK rejecting all-zero signatures
  itself) was not tested (both CARRIED by PLAN-25 deliverable 4). Pre-push `module-tests` and a valid whole-tree `test-compile` arms degraded (same cause
  as PLAN-21 and PLAN-08; covered by the whole-tree verify, 3592 tests per the landing and 3568 per the PR body, not
  re-run). The post-merge `Run JMH Benchmarks` workflow had not finished when the run's wait hit its deadline; not
  re-read. Two metrics rows carry placeholder figures later corrected (landing message). Sonar read directly: gate
  OK and 0 open on #851 (a PR-scoped snapshot, not a re-run).
- PLAN-06 residuals and review (landing message + PR #840 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `aa72e8cb`) — CodeRabbit had not reviewed the last,
  test-only commit; `adr-propose` skipped, so single-use `FlowContext` (a rejected first `handle()` spends
  the context) and the shared `ErrorTextSanitizer` in the validation module have no ADR; the landing
  carries no code-review counts; `AuthorizationRequestBuilder.build` still appends its query after a
  fragment (PLAN-10); one merge-queue ejection on the unrelated benchmark flake.
- PLAN-07 residuals and review (landing message + PR #844 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `75dc96e0`, final commit test-and-doc-only);
  duplicate WARN identifier 168 left for PLAN-13 (V-14); no Quarkus config key for `maxStaleness` (CARRIED by PLAN-25 deliverable 5b); staleness
  is measured on the wall clock; unplanned cache-hit health check in `AccessTokenValidationPipeline` (a
  behaviour change beyond the spec); the Performance Benchmark job had not finished when the post-merge
  wait timed out.
- PLAN-12 residuals and review (landing message + PR #846 body, not independently verified): merged under the
  operator's "Accept the coverage gap" ruling at head `145f838c` (CodeRabbit quota-limited for the last two fix
  commits); three advisory code-review notes, one named: the token pool's rotation counter can wrap around
  (now in PLAN-22); the pre-push gate was satisfied from the whole-tree verify (3443 tests) instead of the
  separate arms; the first TASK-5 commit lacks the Claude trailer; a roughly 21-hour pause from a weekly usage
  limit sits in the wall-clock total.
- PLAN-20 residuals (landing message + PR #847 body, not independently verified): two `AGENTS.md` advisories
  (now in PLAN-22); whether an existing test pins a throwing `TokenStore.retrieve` was not shown at file level;
  CodeRabbit refused twice and `review_rate_window_await` was switched on; the functional review skipped the
  delta round for the second head.
- PLAN-21 residuals (landing message + PR #849 body, not independently verified): the pre-push gate's
  `test-compile` and `module-tests` arms were UN-GATED (the generators test-jar cannot resolve in a reactor
  `test-compile`; whole-tree `verify -Ppre-commit` 3476 tests green at the merged head; same degradation as
  PLAN-05); `applyRefresh` is a breaking public signature change (release notes); no ADR for the compare-and-set
  decision (operator declined in the run); the post-merge `Run JMH Benchmarks` workflow had not finished; the
  spec's PLAN-20 ordering was not checked by the run but holds on `main`. Advisories routed: see Decisions
  2026-11-02. `doc/developer/test-impl-mapping.adoc` does not exist, and the plan-time `module_mapping_validator`
  flag on cross-module test routing was accepted; whether the project wants that mapping doc is an open
  operator-level question, not blocking (CARRIED by PLAN-25 deliverable 5a).
- PLAN-08 residuals (landing message + PR #850 body, not independently verified): OPEN, security-relevant: Q-1
  (class-level `@BearerAuth`) was verified in a real native container only with the two fixtures that carry
  `@RegisterForReflection`; CodeRabbit asked for a fixture without it, plus two notes marked unverified (native
  reflection metadata for the class-level lookup; a fail-open when ArC reports an unannotated generated
  override). Whether the unregistered case can fail open is untested and needs a native build (CARRIED by PLAN-25 deliverable 1). Also: env-only
  multi-key JWE configuration is skipped without a startup signal (documented and pinned by a test); the
  whole-tree `test-compile` and `module-tests` are red on unmodified `main` (test-jar not visible before
  packaging; `verify:quality-gate` accepted in place, same cause as PLAN-21); a post-merge `Run JMH Benchmarks`
  run had not finished.
- PLAN-02 residuals (landing message + PR #832 body, not independently verified): public `applyRefresh`
  overloads take no base-token argument, so a late external result can replace a newer re-login grant
  (needs a public signature change; pre-existing); stale comment on
  `RefreshAdversarialTest.shouldFailClosedWhenRevocationThrowsUnchecked`; an OpenRewrite `TODO` marker
  in `holdsNewerGrant`; ADR-0005 and ADR-0010 still call the post-redemption clear unconditional.
- PLAN-03 residuals (landing message, not independently verified): swapped `@DisplayName` V-9/V-2
  labels on two new `AccessTokenValidationPipelineTest` tests; `AccessTokenCache.put()` does not require
  `iat` when a max age is set (unreachable from the pipeline, but a direct public-API `put` could make
  eviction throw `MISSING_CLAIM`); no test combines non-zero clock skew with a max age; two draft ADRs
  (per-entry issuer policy at store time; rejection inside the cache before DPoP) were not created.
- PLAN-03 review/Sonar: merged under a recorded `barrier-ask-override` (`review-barrier-gap`, head
  `467a857b`) because CodeRabbit's one-review-per-hour quota left the fix-up head unreviewed by the
  required bot. Sonar: 0 open issues on #833 per the 2026-10-30 direct read; the landing's `java:S5778` fix and
  `java:S2093` code suppression both cleared. The review quota will recur
  on every plan — a process cost to weigh when pairing plans.
- PLAN-05 residuals (landing message + PR #835 body, not independently verified): `token-decryption.adoc`
  line 200 over-generalises the `kty` rejection; the CWE-117 sanitiser is duplicated in `JweDecryptor` and
  `JwkKey`; a top-level JSON `null` header is rejected but neither logged nor counted (pre-existing; CARRIED by PLAN-25 deliverable 4c);
  test gaps (null/blank `epk` coordinate, `apu`/`apv` decryption, inner-JWS payload decode failure,
  unsupported-compression row); two test lines hold raw U+2028/U+2029; eight advisory code-review notes;
  pre-push test-compile and module-tests arms DEGRADED, coverage adequacy not audited; Sonar: 0 open on
  #835 per the 2026-10-30 direct read. Four plan-marshall-side lessons could not be filed because this repo's lesson store
  rejects plan-marshall components — they exist only in the landing record.
- PLAN-04 residuals (landing message + PR #834 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `0053b11c`) with functional and code review closed by
  operator override at that head; accepted functional-review coverage gap D5.C3; four advisory notes
  (HealthCheck catch-suppression placement, README wording for apps with their own `TokenValidator`,
  `CLIENT-21` vs `CLIENT-20` Javadoc, initializer test does not pin the proxy dereference); Sonar:
  0 open on #834 per the 2026-10-30 direct read; the "mappers inactive at runtime" hypothesis was refuted as a cause but
  never observed failing without the registration.
- PLAN-01 residuals (landing message, not independently verified): three test classes over the
  400-line budget; a retained suppressed catch-all in `TokenValidatorProducer.destroy()`; an
  `initialize()` unexpected-exception path that leaves `LOADING`; a shared `HttpJwksLoaderConfig`
  owned-executor caveat for V-5. ADRs 0016–0018 carry provisional numbers.
- PLAN-22 residuals (2026-11-06; verified on `38aefc0a` unless marked): (1) the 400-line test budget: PLAN-22 split only
  `NonValidatingJwtParserJweTest` (1018 to 369, plus two classes of 245 and 256 lines); `JweDecryptorTest` (1020) and
  `AccessTokenCacheTest` (1000) are still over and grew, with no "why not" recorded; the 400 figure is not in
  `AGENTS.md` or any versioned doc (grep), and 52 test classes exceed 400 lines, 17 exceed 600 and three exceed 1000 (`DpopProofValidatorTest` 1375; count corrected 2026-11-06 by the cleanup re-grounding, this entry first said about a dozen).
  The PLAN-01 "three test classes over the budget" residual above is the same item. Not folded into a plan: an
  operator ruling on whether the budget is real comes first; PLAN-25's emit-time re-read looks at it again. (2) The
  `TokenLifecycleManager` quarantine widening landed under a docs plan (see `landings/PLAN-22.md`): fine, tested, but a
  client threat-model review should list it. (3) The two accepted functional-review and code-review findings are
  unnamed and unreadable. (4) CLOSED by PLAN-22: the `DpopConfig` 300 versus 360 Javadoc, the raw U+2028/U+2029 test
  lines, the PLAN-08 empty-candidate guard test, the `TokenValidator.close` first-failure drop, the CWE-117 duplication,
  the seven missing ADRs; DROPPED as already correct: the rotation-counter wrap and the iat-less cache Javadoc. ADRs
  0016 to 0018: no "provisional" marker anywhere under `doc/adr/`.
- PLAN-16 residuals (2026-11-06; verified on `306e6053` unless marked): (1) the parked ADR draft, FOLDED into PLAN-25
  deliverable 7(h) (draft preserved under `drafts/`; the plan's number 0020 is taken, allocate at finalize). (2) No file
  under `doc/client` mentions `invalid_client` or `unauthorized_client`, so the new public exception is undocumented:
  in the same PLAN-25 item. (3) `RefreshFailureClassificationTest` +18 pins that the new type stays pre-redemption (not
  read line by line). (4) The code review's one advisory finding is unnamed in the message. (5) The pre-push gate reported
  module tests DEGRADED and some builds `required_coverage_unknown`; the PR gate (24 checks, Sonar OK, coverage 100.0)
  is the stronger evidence. (6) Specs naming an external wire value now label it HYPOTHESIS (my own rule, see Decisions).
- PLAN-15 residuals (2026-11-06; verified on `262369d6` unless marked): (1) TV-4 stays open by operator decision, see Decisions.
  (2) No class-level guard exists for the DSL-JSON pattern: FOLDED into PLAN-25 deliverable 2. (3) The `JWKSKeyLoader` and `KeyProcessor`
  log sites remain unsanitised: in PLAN-25 deliverable 6. (4) The JWE-header catch in `NonValidatingJwtParser` has no dedicated test (the
  landing's own note): PLAN-25 deliverable 4(d). (5) The pre-push gate degraded its test-compile and module-tests arms because client
  tests need the validation test-jar; the owed hint is recorded through its own PR. (6) Both review closes were operator overrides at a
  head, for criteria observable only after the push; both are met after the push. (7) The CodeRabbit quota waits and a repeated build-queue
  `max_slots` configuration warning were not reported as lessons; the first is the known shared-quota Watch above.
