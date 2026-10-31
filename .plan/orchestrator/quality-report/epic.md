# Epic: Quality report remediation

slug: quality-report

> Hand-written narrative for one epic under `.plan/orchestrator/quality-report/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug quality-report`
> on the merged tree, and `git add` the result.

## Vision

Remediate the still-valid findings of the full-repository quality review (snapshot of revision
`d6b927c`, 2026-08-15, 82 findings across validation, client, Quarkus/benchmarking/build, tests,
AsciiDoc and Javadoc). The report was moved verbatim from `doc/quality-report/` into this epic's
`source-report/` directory, where it is the epic's frozen source material — it is no longer part
of the published documentation. Because the snapshot predates later work (#731, #795, #796, …),
every finding was re-grounded against HEAD before staging; only findings that still hold are
staged as plans. Done at the epic level means: every finding has a recorded disposition
(fixed by a plan of this epic, already fixed at HEAD, or refuted), and each fixing PR names the
finding IDs it closes.

## Queue annotations

Queue order interleaves workstreams so that, with `parallelization_scope = 2`, each adjacent pair is
surface-disjoint by construction. Recommended pairings (validation lane ∥ client/Quarkus/bench lane):
PLAN-01 ∥ PLAN-02 → PLAN-03 ∥ PLAN-04 → PLAN-05 ∥ PLAN-06 → PLAN-07 ∥ PLAN-08 → PLAN-09 ∥ PLAN-10 →
PLAN-11 ∥ PLAN-12 → PLAN-13 ∥ PLAN-14 → PLAN-15 ∥ PLAN-16 → PLAN-17 → PLAN-18 → PLAN-19.

- PLAN-01 — shipped as #830 (`1ca5ab35`), see `landings/PLAN-01.md`. Scope grew past its declared
  surface (28 merged paths vs 8 declared): shared discovery layer, Quarkus `TokenValidatorProducer`,
  retry-probe IT fixture, ADRs 0016–0018. Staged specs that declare a path it changed — PLAN-03/17
  (`TokenValidator.java`), PLAN-04 (`TokenValidatorProducer.java`), PLAN-07 (`HttpJwksLoader.java`),
  PLAN-13 (`HttpJwksLoader.java`, `HttpWellKnownResolver.java`, `WellKnownConfigurationConverter.java`),
  PLAN-19 (`doc/validation/architecture.adoc`, `requirements.adoc`) — must re-verify their claims
  against HEAD at outline; they were grounded at `aa77acd9`. PLAN-04 is the most exposed.
- PLAN-02 — shipped as #832 (`31fb2212`), see `landings/PLAN-02.md`. Its footprint changed
  `doc/LogMessages.adoc` (new `TokenSheriffClient-003`) and `token-handling.adoc`: PLAN-07, PLAN-13
  (LogMessages) and PLAN-19 (token-handling) must re-check against HEAD.
- PLAN-01 → PLAN-07 → PLAN-13 — strictly sequential: all edit `HttpJwksLoader.java`.
- PLAN-03 — shipped as #833 (`2ea3ef2f`), see `landings/PLAN-03.md`. 8 merged paths vs 6 declared;
  `AccessTokenCache` lost its global `clockSkewSeconds` constructor parameter and `put()` now takes the
  issuer `ValidationContext`. Of the staged specs only PLAN-17 declares a path it changed
  (`TokenValidator.java`); PLAN-09's hypothesis that the `exp` boundary leaves cache-hit expiry alone
  must re-verify against the per-entry skew.
- PLAN-04 — shipped as #834 (`ef720fd0`), see `landings/PLAN-04.md`. 18 merged paths vs 10 declared; adds
  `TokenValidatorStartupInitializer` and ADR 0019. `quarkus-config-doc.adoc` was NOT touched, so the
  PLAN-08 single-file overlap did not materialise. PLAN-08 (`JwtValidationEndpoint.java`) and PLAN-17
  (`TokenSheriffClientProcessor.java`, `JwtValidationEndpoint.java`, one more) declare a path it changed;
  PLAN-08 also re-grounds its startup/producer claims. PLAN-19's ADR index assumes ADR-0001…0015 and is
  stale against ADRs 0016–0019.
- PLAN-05 — shipped as #835 (`e010c5cf`), see `landings/PLAN-05.md`. Scope grew past its declared surface
  (17 merged paths vs 8 declared): CWE-117 sanitisation, single-reporter JWE logging, a changed plain
  JWS decode message, sanitised `JwkKey` decode (JWKS and DPoP paths), retired log record
  `TokenSheriff-156`. Staged specs that declare a path it changed — PLAN-09 (`NonValidatingJwtParser.java`,
  `NonValidatingJwtParserTest.java`), PLAN-17 (`NonValidatingJwtParser.java`), PLAN-07 and PLAN-13
  (`doc/LogMessages.adoc`, `JWTValidationLogMessages.java`) — re-verify at outline. PLAN-05 → PLAN-09
  sequencing is satisfied.
- PLAN-06 — shipped as #840 (`a56b1b59`), see `landings/PLAN-06.md`. 20 merged paths vs 13 declared: new
  `EndpointSchemePolicy`, `EndpointQuery` and a shared `ErrorTextSanitizer` (in the validation module, used by
  the client `LogSanitizer` and `InboundErrorNormalizer`). `FlowContext` is now single use. Staged specs that
  declare a path it changed — PLAN-10 (`AuthorizationRequestBuilder.java`, `AuthorizationCodeFlowTest.java`),
  PLAN-14 (`EndSessionFlowTest.java`) — re-verify at outline; PLAN-10 re-grounds its fragment claim against
  the new scheme-policy split. `adr-propose` was skipped by the plan.
- PLAN-07 — shipped as #844 (`50c295e6`), see `landings/PLAN-07.md`. 16 merged paths vs 7 declared. Opt-in
  max-staleness bound (off by default, new `TokenSheriff-170`), re-discovery after three failed cycles,
  timeouts forwarded to the well-known transport; ADR 0017 amended. Unplanned: the cache-hit path in
  `AccessTokenValidationPipeline` now checks the issuer's current health. PLAN-13 declares three changed
  paths (`HttpJwksLoader.java`, `doc/LogMessages.adoc`, `JWTValidationLogMessages.java`) and re-grounds its
  log-record claims against `TokenSheriff-170`; the planned PLAN-07 → PLAN-13 sequencing is satisfied.
- PLAN-20 — staged 2026-10-31 (operator choice A): resolve the OpenRewrite marker PLAN-02 committed in
  `TokenLifecycleManager.holdsNewerGrant` (still on main; the one open Sonar INFO on #832). One method.
  Only PLAN-19 shares a possible path (`AGENTS.md`, and only if the suppression arm is taken); PLAN-19 runs
  last, so PLAN-20 can pair with any plan that does not touch `TokenLifecycleManager.java`.
- PLAN-12 — operator choice A (2026-10-31): the flaky `LibraryMetricsExporterTest` timing bound was added as a
  fifth deliverable, and PLAN-12 is to be emitted NEXT, ahead of PLAN-08, so the flake stops ejecting merges.
  Its surface is `benchmarking/` plus `bom/pom.xml` only, so it is disjoint from PLAN-20 and PLAN-08.
- PLAN-20 → PLAN-21 → PLAN-22 — staged 2026-10-31 (operator choice C on the residual advisories). PLAN-21
  (WS-01, five behaviour residuals) shares `TokenLifecycleManager.java` with PLAN-20 and `AccessTokenCacheTest.java`
  with PLAN-22, and overlaps staged PLAN-13 (`HttpJwksLoader.java`, log-record files), PLAN-09 and PLAN-17
  (`NonValidatingJwtParser.java`). PLAN-22 (WS-05, docs, tests, ADRs and the sanitiser duplication) overlaps
  staged PLAN-17 (`TokenSheriffClientProcessor.java`); run it before PLAN-19. Both are claim-light: every
  residual is a landing-message claim marked verify-at-outline, and a residual that no longer exists is dropped.
- PLAN-14 — runs after PLAN-06 and PLAN-10 (tests the behaviour they change).
- PLAN-15 — after PLAN-09 (exp boundary) and PLAN-13 (dup-kid id renumber).
- PLAN-16 — Keycloak IT cost; after PLAN-06 (C-6 callback ordering). Pair with a cheap plan.
- PLAN-17 — Javadoc-only but touches many code-plan files; run after all code plans in WS-01/WS-03.
- PLAN-18 → PLAN-19 — multi-file doc overlap (D-11 xref sweep); strictly sequential.

## Decisions

- 2026-10-28 — `doc/quality-report/` moved (git mv, history kept) into `source-report/` of this epic
  per operator instruction; no inbound links existed in the repo. The report is the epic's frozen
  source; it is no longer published documentation.
- 2026-10-28 — Every finding re-grounded against HEAD `aa77acd9` before staging (four read-only
  verification passes + orchestrator spot checks of 14 claims, all corroborated). Per-finding
  verdicts live in `findings-disposition.md`; only VALID/PARTIAL findings are staged.
- 2026-10-28 — Closed without a plan: V-6 (#818/#817), TV-1, TV-3, TV-5 (#795), TC-2 (replaced by
  `RefreshProductionPathSpecIT`). TV-8 recorded NO-ACTION (fixture self-tests; soft assertions fine).
- 2026-10-28 — Doc findings that move with a code fix are owned by the code plan (D-1 → PLAN-04,
  D-3/D-4 → PLAN-13, D-8/J-8 → PLAN-08) so code and docs never diverge across PRs.
- 2026-10-28 — Q-4 escalated in priority: #795 registered the liveness check in every build, so the
  adversarial report's mitigation no longer holds. PLAN-04 is placed early.
- 2026-10-29 — PLAN-01 (#830) and PLAN-02 (#832) reconciled as shipped from their inbox landings,
  corroborated against PR state and the merge commits. Findings closed per the PRs: V-3, V-4, V-5, V-8,
  V-16 (PLAN-01); C-1, C-2, C-8b, TC-6 (PLAN-02). `findings-disposition.md` marks all nine FIXED,
  noting the code itself was not re-read.
- 2026-10-29 — PLAN-03 (#833) reconciled as shipped from its inbox landing, corroborated against PR
  state and the merge commit. Findings closed per the PR: V-2, V-9. `findings-disposition.md` marks both
  FIXED, noting the code itself was not re-read.
- 2026-10-29 — PLAN-04 (#834) reconciled as shipped from its inbox landing, corroborated against PR
  state and the merge commit. Findings closed per the PR: Q-2 (remainder), Q-3, Q-4, Q-8, D-1
  (remainder). `findings-disposition.md` marks all five FIXED, noting the code itself was not re-read.
  Q-4 resolved as catch-and-report (liveness stays UP), Q-8 as document-only; Q-3 is gated on at least
  one enabled issuer (ADR 0019).
- 2026-10-30 — PLAN-05 (#835) reconciled as shipped from its inbox landing, corroborated against PR
  state and the merge commit. Findings closed per the PR: V-1, V-17g and gap VAL 8.
  `findings-disposition.md` marks all three FIXED, noting the code itself was not re-read. V-1 took the
  fix arm (typed `epk`), not the drop-ECDH-ES fallback.
- 2026-10-30 — PLAN-06 (#840) and PLAN-07 (#844) reconciled as shipped from their inbox landings,
  corroborated against PR state and the merge commits, with the quality gate and a direct unresolved-issue
  read (0 open on both). Findings closed per the PRs: C-4, C-5, C-6, C-7, C-8a, TC-5 (PLAN-06); V-7
  (remainder) and V-10 (PLAN-07). `findings-disposition.md` marks all eight FIXED, noting the code itself was
  not re-read. C-7 took the enforce arm; V-10a took the max-staleness arm as an opt-in knob. Both landings
  carry `effort`.
- 2026-10-28 — Open design choices are delegated to the plan's outline phase with a stated
  recommendation (V-1 fix-vs-drop ECDH-ES, V-17f empty-aud semantics, C-7 enforce single-use,
  C-8c PAR wire-vs-document, Q-4 catch-vs-readiness, Q-8 reactive-vs-document); each PR records it.
- 2026-10-31 — Lesson dispositions, decided above the operator's 90% confidence bar: 8 project-owned
  messages PROMOTED into this repo's lesson store as 7 lessons (`2026-10-31-12-001` to `-007`): PLAN-01-007
  and PLAN-07-001 merged into one async-lifecycle lesson; PLAN-02-001; PLAN-03-001; PLAN-04-001, -002, -005,
  -006. Their messages are archived. Still open: PLAN-02-004, PLAN-03-002, PLAN-06-004, PLAN-06-005 (project)
  and the 24 `plan-marshall:*` messages (28 live in all). The lesson text does not fix the two root causes it
  names (`build-native-if-needed.sh` reusing a stale runner; the formatter import fixed point).

- 2026-10-31 — Operator rule for OpenRewrite markers: handle each one immediately, fix preferred,
  otherwise suppress in the correct syntax. Lesson `2026-10-31-12-008` (from PLAN-02-004) records it. The
  marker PLAN-02 committed is still on main (`TokenLifecycleManager.java:1109`), so PLAN-20 was staged to
  answer it (operator choice A). Correction to my own question text: the caught `RuntimeException` guards the
  `TokenStore` SPI, so the `AGENTS.md` SPI carve-out may apply and suppression may be legitimate; the spec
  leaves the choice to outline with that recommendation.
- 2026-10-31 — Standing operator policy: tool findings (OpenRewrite markers, Sonar issues) are fixed
  immediately, in the task that produced them. Suppression only in the rule's correct syntax with a stated
  reason, never as a way to defer. PLAN-03-002 was therefore PROMOTED as `2026-10-31-12-009`, worded that
  way, and its message is archived. The remaining recommendations are weighed against this policy.
- 2026-10-31 — PLAN-06-004 (flaky 20 ms benchmark test) dispositioned: folded into PLAN-12 as deliverable 5,
  PLAN-12 moved to the front of the queue (operator choice A). No lesson filed: the remedy is a code fix, and
  the repo-wide rule is the standing fix-immediately policy. The message is archived.
- 2026-10-31 — Residual advisories of PLAN-01..06 (about 23 items) dispositioned (operator choice C): staged
  PLAN-21 (five behaviour residuals) and PLAN-22 (docs, tests, ADR gaps, sanitiser duplication) instead of
  keeping them as Watches, per the fix-immediately policy. The residual lists in the Watches below are kept as
  the evidence trail; PLAN-07's design-level notes (wall-clock staleness, no Quarkus `maxStaleness` key,
  the unplanned cache-hit health check, duplicate id 168 owned by PLAN-13) stay as Watches.
- 2026-10-31 — The 24 `plan-marshall:*` candidate lessons were merged into six theme lessons (`2026-10-31-13-001`
  to `-006`) and filed in this repo's store with `--allow-foreign-store` (operator choice B); a consolidated
  `upstream-plan-marshall-report.md` was written and nothing was sent. PLAN-06-005 (cross-module sanitiser
  duplication) was DISCARDED (operator choice D). The candidate-lesson inbox is empty: 36 messages
  dispositioned (12 project: 10 promoted into 9 lessons incl. `-12-008` and `-12-009`, 1 folded into PLAN-12,
  1 discarded; 24 plan-marshall: 24 promoted into 6 lessons).

## Open Defects

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
- 2026-10-31 — Open: the lesson-routing gap. The candidate-lesson inbox is now empty (all 36 dispositioned),
  but the 24 plan-marshall-owned lessons could only be filed here through the foreign-store override.
  `upstream-plan-marshall-report.md` holds the evidence for the plan-marshall maintainers; it has NOT been
  sent. Whether to forward it is the operator's call.

## Watches

- Sonar, verified 2026-10-30 by a direct unresolved-issue read per PR (`sonar read_open_issues` returned
  `undecidable` / `head_revision_unavailable` for all four merged PRs; the quality gate read OK on each):
  #832 has 1 open issue, INFO `java:S1135` (the TODO marker in `TokenLifecycleManager.java`, the same one
  recorded as a PLAN-02 residual), neither fixed nor dismissed; #833, #834 and #835 have 0 open. The PLAN-02
  spec requires 0 open or every issue dismissed, so that one INFO issue is an unmet criterion, minor in
  weight. The read is a PR-scoped snapshot of the last analysis, not a re-run.
- Sonar, PLAN-06 and PLAN-07, verified 2026-10-30 the same way: quality gate OK on #840 and #844, direct
  `resolved=false` query returns 0 open on both (PLAN-07's landing also reports `new_code_issue_count=0`
  confirmed; PLAN-06's carries no Sonar keys). PR-scoped snapshots, not re-runs.
- PLAN-06 residuals and review (landing message + PR #840 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `aa72e8cb`) — CodeRabbit had not reviewed the last,
  test-only commit; `adr-propose` skipped, so single-use `FlowContext` (a rejected first `handle()` spends
  the context) and the shared `ErrorTextSanitizer` in the validation module have no ADR; the landing
  carries no code-review counts; `AuthorizationRequestBuilder.build` still appends its query after a
  fragment (PLAN-10); one merge-queue ejection on the unrelated benchmark flake.
- PLAN-07 residuals and review (landing message + PR #844 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `75dc96e0`, final commit test-and-doc-only);
  duplicate WARN identifier 168 left for PLAN-13 (V-14); no Quarkus config key for `maxStaleness`; staleness
  is measured on the wall clock; unplanned cache-hit health check in `AccessTokenValidationPipeline` (a
  behaviour change beyond the spec); the Performance Benchmark job had not finished when the post-merge
  wait timed out.
- The CodeRabbit one-review-per-hour quota is shared by concurrent plans: PLAN-06 waited roughly three hours
  behind PLAN-07's window and both merged under an override. Pairs raise the chance of this; recorded for
  the pairing decision on PLAN-08 onward.
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
  `JwkKey`; a top-level JSON `null` header is rejected but neither logged nor counted (pre-existing);
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

- TC-9 (IT part) — integration tests use `useRelaxedHTTPSValidation()` and per-IT trust-all managers;
  a real truststore for the Keycloak container is IT-cost and not staged. Re-check if IT TLS
  infrastructure changes or a TLS-related IT regression appears.
- `source-report/` line numbers are stale; plan specs carry the current locations.
