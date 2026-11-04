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
- PLAN-12 — shipped as #846 (`17a3ef62`), see `landings/PLAN-12.md`. 16 merged paths vs 10 declared: the five
  concurrency benchmarks (three JFR ones not declared) moved to `Scope.Benchmark`, metrics exported once per
  trial under a per-method key, the BOM scope fixed, the flaky timing bound replaced by synthetic durations.
  `LibraryMetricsExporter.java` was deliberately not touched. No staged spec declares a path it changed.
- PLAN-20 — shipped as #847 (`5349a3bc`), see `landings/PLAN-20.md`. Two files: the marker became the
  sanctioned suppression (SPI carve-out) and `AGENTS.md` was reworded. The project-wide `java:S1135` query now
  returns 0, so the open INFO from #832 is gone. PLAN-21 (`TokenLifecycleManager.java`) and PLAN-19
  (`AGENTS.md`) declare a path it changed. Two `AGENTS.md` advisories moved to PLAN-22.
- PLAN-21 — shipped as #849 (`c308f7c0`), see `landings/PLAN-21.md`. 21 merged paths vs 8 declared: all five
  residuals fixed (loader init status, producer `destroy` and `TokenValidator.close`, `applyRefresh` base-token
  compare-and-set as a breaking signature change, `iat`-less entry no longer cached under a max age, null header
  and bad Base64Url part logged and counted). Declared paths changed: PLAN-09, PLAN-13, PLAN-17, PLAN-19, PLAN-22.
  PLAN-09, PLAN-13 and PLAN-22 were extended with the PLAN-21 code-review residuals (see Decisions 2026-11-02).
- PLAN-08 — shipped as #850 (`ed172e43`), see `landings/PLAN-08.md`. 19 merged paths vs 12 declared: class-level
  `@BearerAuth` honoured under ArC, `@DefaultBean` client producers, strict JWE configuration, env-var-only issuer
  discovery (fix arm), TTL default documented as 360. Two operator-approved scope exceptions (integration-tests
  build script and POM property; extra JWE doc rows). No staged spec declares a path it changed. PLAN-17's
  overlap with it (`BearerAuth.java`, one file) is no longer a conflict.
- PLAN-09 — shipped as #851 (`49ba673d`), see `landings/PLAN-09.md`. 21 merged paths vs 11 declared (11 added, the
  declared `JwksParserTest.java` untouched): the three prose restatements of the expiry boundary, review-driven
  tests, and the register and threat-model rows. Staged specs that declare a path it changed — PLAN-15
  (`ExpirationValidatorTest.java`), PLAN-17 (`IssuerConfig.java`, `NonValidatingJwtParser.java`), PLAN-18
  (`threat-model.adoc`), PLAN-19 (`cve-lessons-register.adoc`, `microprofile-jwt-compatibility.adoc`,
  `threat-model.adoc`) and PLAN-23 (`ValidationContext.java`, `WiredNegativePathAttackTest.java`) — re-verify at
  outline; PLAN-23 in particular names Sonar issues by file and line. PLAN-10 was clean against it.
- PLAN-11 — shipped as #853 (`a9ce05df`), see `landings/PLAN-11.md`. 10 merged paths vs 5 declared (8 added, 3 missing:
  the `MandatoryClaimsValidator` hypothesis was refuted and `ClaimValue` was left alone): a new `StrictStringMapper`
  for `azp`, `ClaimName`, native-image metadata and a `security-reference.adoc` paragraph. Of the staged specs only
  PLAN-18 and PLAN-19 declare a path it changed (`security-reference.adoc`); both re-read it at outline.
- PLAN-23 — staged 2026-11-04 (WS-05, the 44 pre-existing SonarCloud issues on `main`; see Decisions 2026-11-04).
  Its start condition, PLAN-09 merged (it shares `ValidationContext.java`), is met as of 2026-11-04. It runs before
  PLAN-22 (shares `AccessTokenCacheTest.java` and `TokenValidator.java`) and PLAN-17 (`TokenValidator.java`).
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
- 2026-11-01 — PLAN-12 (#846) and PLAN-20 (#847) reconciled as shipped from their inbox landings,
  corroborated against PR state, merge commits, merged footprints, Sonar (gate OK, 0 open on both; the
  project-wide `java:S1135` count is 0). Findings closed per the PRs: Q-7, Q-10, Q-11, Q-13 (PLAN-12);
  PLAN-20 closes a PLAN-02 residual with no finding ID. `findings-disposition.md` marks the four FIXED,
  noting the code itself was not re-read. PLAN-20 took the suppression arm. The 9 candidate-lesson messages
  (PLAN-12-001 to -005, PLAN-20-001 to -004) were all dispositioned by me above the 90% bar as recurrences or
  addenda of the existing six theme lessons, written into `upstream-plan-marshall-report.md` (addendum
  2026-11-01), and archived; no new lesson was filed. Two unfixed code-review advisories on `AGENTS.md`
  (PLAN-20) and the benchmark token-pool rotation counter (PLAN-12) were added to PLAN-22 under the
  fix-immediately policy.
- 2026-11-02 — PLAN-21 (#849) reconciled as shipped from its inbox landing, corroborated against PR state, the
  merge commit, the merged footprint and Sonar (gate OK, 0 open on the PR). No finding ID, so
  `findings-disposition.md` is unchanged. The 7 candidate-lesson messages (PLAN-21-001 to -007) were all
  dispositioned by me above the 90% bar: -004 (lock/publish race, under-pinned tests) PROMOTED as the local
  lesson `2026-11-02-10-001`; the other six (`plan-marshall:*` components) went into addendum 2026-11-02 of
  `upstream-plan-marshall-report.md` as recurrences (-002, -003, -005, -006) or new items (-001, -007); no new
  theme lesson was filed. The six code-review advisories PLAN-21 left unfixed were routed under the
  fix-immediately policy: the `jwks_uri` retry advisory to PLAN-13 (deliverable 6, same class), the `DecodeMode`
  gap to PLAN-09 (deliverable 6, same parser), and the Javadoc, test-row, ADR-0017 and `TokenValidator.close`
  items to PLAN-22. The `close` fix is a behaviour change placed in a docs/tests plan on my judgement (about
  85% confident); it is a one-line move to another plan if the operator prefers.
- 2026-11-02 — PLAN-08 (#850) reconciled as shipped from its inbox landing, corroborated against PR state, the
  merge commit, the merged footprint and Sonar (gate OK, 0 open on the PR). Findings closed per the PR: Q-1, Q-5,
  Q-6, Q-9, D-8, J-8; `findings-disposition.md` marks all six FIXED, noting the code was not re-read. PLAN-09 was
  recorded `running` on the strength of its plan worktree existing (the operator did not say "started"). The 11
  candidate-lesson messages (PLAN-08-001 to -011) were dispositioned by me above the 90% bar: -002, -004, -006
  merged into the local lesson `2026-11-02-14-001`, -005 folded into it, -007 filed as `2026-11-02-14-002`; the
  other six (-001, -003, -008, -009, -010, -011) went into the second 2026-11-02 addendum of
  `upstream-plan-marshall-report.md`. Lesson `2026-10-31-12-004` (stale `~/.m2` native IT) now records that its
  root cause was fixed by this PR. Landing residue routed under the fix-immediately policy: the `DpopConfig`
  Javadoc, the untested `ConfigValueParser` guard and four uncreated ADRs to PLAN-22; the native-image fixture
  question is an open operator decision (below).
- 2026-11-02 — Parallelism: kept at the epic's `parallelization_scope` of 2 and emitted one plan (PLAN-10, next
  in queue order, disjoint from the running PLAN-09). The surfaces would allow three; the reasons for not
  exceeding two are the shared CodeRabbit one-review-per-hour quota (PLAN-08's last fix commit could not be
  re-reviewed inside the hour, PLAN-21 and PLAN-08 both merged under pair-induced waits) and token cost
  (24 to 31 million tokens per plan, plus the 21-hour weekly-limit pause on PLAN-12). A third plan is a one-word
  operator override; the best candidate is PLAN-22 (documentation, tests and ADRs, no behaviour risk).
- 2026-11-04 — Operator: "Add the sonar issue to an existing or a cleanup plan - stage it now and give me the
  command." I staged a new plan, PLAN-23 (WS-05, "Close the pre-existing SonarCloud backlog on main"), instead of
  extending an existing one, because the 44 issues sit in 32 files over four modules and PLAN-22 is already
  item-heavy (about seven ADR drafts). Spec built from a fresh `branch=main` issues read (44 open, the newest
  created 2026-10-10): 27 in test sources, 17 in main sources, each with its issue key, in two tables. Order of
  work inside the plan: test-code first (mechanical), then cognitive complexity, always-true/false conditions
  (decide per site: remove, or dismiss with a reason where the check is a deliberate defence), nullness, minor
  rules. Admission: one shared file with running PLAN-09 (`ValidationContext.java`), two with staged PLAN-22
  (`AccessTokenCacheTest.java`, `TokenValidator.java`), one with PLAN-17 (`TokenValidator.java`); every other
  overlap is with a shipped plan. So PLAN-23 starts only after PLAN-09 has merged, and runs before PLAN-22 and
  PLAN-17, which re-read those files at their own outline. Not a third parallel plan: it goes in when PLAN-09
  merges.
- 2026-11-04 — PLAN-09 (#851) reconciled as shipped from its inbox landing, corroborated against PR state (`merged`),
  the merge commit on `origin/main`, the 21-path merged footprint, the merged source for each of the six spec
  deliverables, and Sonar read directly (gate OK, 0 open on the PR; `main` still 44). Findings closed per the PR:
  V-11, V-12, V-17b, V-17c, V-17e, plus the decode-mode residual (no finding ID); `findings-disposition.md` marks the
  five FIXED, and this time the call sites were read in the merged source, not only taken from the PR. The landing's
  one unfixed code-review advisory (guards "shadowed by `initVerify`") is contradicted by the merged code, where the
  range check runs before `initVerify`; see the Watch. The 6 candidate-lesson messages (PLAN-09-001 to -006) were
  dispositioned by me: -002 and -005 PROMOTED as the local lessons `2026-11-04-13-001` and `-002`; -001, -003, -004
  and -006 went into the 2026-11-04 addendum of `upstream-plan-marshall-report.md` (-006 as a recurrence, the other
  three as new items); no spec was folded and no new theme lesson was filed. About 85% confident on -004, which
  could instead have been merged into the local lesson `2026-11-02-10-001` (a recurrence of under-pinned tests);
  I kept it upstream because its suggested component is the marketplace skill `pm-dev-java:junit-core`, and moving
  it is a one-step change. PLAN-23's start condition (PLAN-09 merged) is met.
- 2026-11-04 — PLAN-11 (#853) reconciled as shipped from its inbox landing (complete, `effort=medium`), corroborated
  against PR state (`merged`), the merge commit on `origin/main`, all 24 PR checks green, the 10-path merged
  footprint, the merged source for each of the three spec deliverables, and Sonar read directly (gate OK, 0 open on
  the PR). Findings closed: V-17f, TV-2, gaps VAL 1 and VAL 4 (`findings-disposition.md` marks them FIXED). The spec's
  one hypothesis, that `MandatoryClaimsValidator` also treats `aud: []` as missing, is refuted: that file has no
  audience handling. The two candidate-lessons (PLAN-11-001, -002) both recur items already in the upstream report
  and went into its 2026-11-04 PLAN-11 addendum as DISCARDED locally; no local lesson, no fold, nothing sent.
  I found one thing the landing only half says: `client_id` is still string-coerced and can satisfy the same
  comparison when `azp` is absent. It is the operator's recorded decision, but no staged spec covers it, so it is a
  Watch and an open operator question rather than a decision I took (confidence about 75% that a small standalone fix
  is the right home, below the 90% bar).
- 2026-11-04 — Two operator decisions on the PLAN-11 open items. (1) The `client_id` residual becomes a staged plan,
  PLAN-24 (`plans/PLAN-24-client-id-strict-typing.md`, WS-01, ordinal 24 as the next free): three deliverables (map
  `client_id` through the existing `StrictStringMapper`; settle `iss`, `sub` and `jti` at outline, where a hypothesis
  says none is exposed; tighten the PLAN-11 `azp` assertions), so below the six-deliverable split threshold. The
  parser reads it as declarative with 5 paths, comparison determinate, no overlap with a running plan; its one shared
  file with PLAN-18 and PLAN-19 (`security-reference.adoc`) does not block and PLAN-24 runs before both. It can run
  in any free slot. I added the PLAN-11-001 and -002 lessons to its Standing Constraints (no conditional "add a log
  identifier if needed" clause; do not file the failed stand-alone `test-compile` arm as findings). (2) `CLAUDE.md`
  rule 7 (no auto-merge unless instructed) is replaced by permission to merge through the merge queue once CI is green
  and every reviewer comment is resolved, never bypassing protection or a failing check. This is the one edit I made
  outside the epic tree, made because the operator asked for it directly; it sits on the local branch
  `chore/claude-md-allow-merge` (`49f8bb3f`, one file, 3 insertions and 1 deletion) from `origin/main`. The operator
  then asked to land everything: the branch was pushed and opened as PR #857 the same day (see the Watches item).

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
- 2026-11-01 — Incomplete landing `plan-12-benchmark-build-006.md` (PLAN-12): missing key `effort` (never
  stamped, written as `unknown`). Drained and reconciled as far as it goes. Fifth of the nine landings so far
  without it (PLAN-01, 02, 04, 05, 12); PLAN-03, 06, 07 and 20 carried it.
- 2026-11-04 — Incomplete landing `plan-09-dpop-parser-hardening-007.md` (PLAN-09): `landing-check` reports
  `complete: false`, missing key `effort` (written as `unknown`; the landing says the stamp was absent). Drained and
  reconciled as far as it goes. Sixth known producer with an unstamped `effort` (PLAN-01, 02, 04, 05, 12, 09). The
  PLAN-08 and PLAN-21 landings were not re-checked for it.

## Watches

- PLAN-11 residuals (read in the merged source 2026-11-04 unless marked): (1) `client_id` is still mapped by
  `IdentityMapper`, which stringifies any non-string value, and `AuthorizedPartyValidator` (line 100) falls back to it
  when `azp` is absent and the RFC 9068 fallback is on, so a numeric or list-typed `client_id` on an `azp`-less token
  can still satisfy the configured-id comparison PLAN-11 closed for `azp`. Operator decision at PLAN-11. STAGED as
  PLAN-24 on 2026-11-04 (strict mapping, an `iss`/`sub`/`jti` sweep, tightened `azp` assertions); retire this item
  when PLAN-24 ships. (2)
  `ClaimValue.isNotPresentForClaimValueType()` now has no production caller, only `ClaimValueTest`: dead code, a
  cleanup candidate. (3) The three code-review advisories (landing message, not independently verified): the
  non-string `azp` tests assert only the exception supertype; a `StrictStringMapper` rejection is not counted or
  logged beyond the `MISSING_CLAIM` exception it throws (whether the pipeline counts it was not traced); `client_id`
  as in (1); items (1) and the first advisory are carried by PLAN-24. (4) `.plan/marshal.json` line 133 sets
  `final_merge_without_asking: true`, which conflicted with the `CLAUDE.md` rule against auto-merge. The operator
  decided on 2026-11-04 to change `CLAUDE.md` to allow the merge; the change is PR #857 (branch
  `chore/claude-md-allow-merge`, `49f8bb3f`), open and NOT merged: all CI checks passed but `license/cla` is
  pending ("not signed" from the CLA bot; the operator must sign or ask it to recheck; I cannot). Sourcery's
  "needs a human reviewer" remark was answered on the PR as an accepted trade-off. Until #857 merges, running
  plans read the old rule; retire this item when it does. (5) The native-image metadata for `StrictStringMapper` (`reflect-config.json`) was not exercised in a
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
  itself) was not tested. Pre-push `module-tests` and a valid whole-tree `test-compile` arms degraded (same cause
  as PLAN-21 and PLAN-08; covered by the whole-tree verify, 3592 tests per the landing and 3568 per the PR body, not
  re-run). The post-merge `Run JMH Benchmarks` workflow had not finished when the run's wait hit its deadline; not
  re-read. Two metrics rows carry placeholder figures later corrected (landing message). Sonar read directly: gate
  OK and 0 open on #851 (a PR-scoped snapshot, not a re-run).

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
- Sonar, 2026-11-01: gate OK and 0 open on #846 and #847; the project-wide `java:S1135` query returns 0, so
  the one open INFO on #832 recorded above is resolved (by PLAN-20). PR-scoped snapshots, not re-runs.
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
  operator-level question, not blocking.
- PLAN-08 residuals (landing message + PR #850 body, not independently verified): OPEN, security-relevant: Q-1
  (class-level `@BearerAuth`) was verified in a real native container only with the two fixtures that carry
  `@RegisterForReflection`; CodeRabbit asked for a fixture without it, plus two notes marked unverified (native
  reflection metadata for the class-level lookup; a fail-open when ArC reports an unannotated generated
  override). Whether the unregistered case can fail open is untested and needs a native build. Also: env-only
  multi-key JWE configuration is skipped without a startup signal (documented and pinned by a test); the
  whole-tree `test-compile` and `module-tests` are red on unmodified `main` (test-jar not visible before
  packaging; `verify:quality-gate` accepted in place, same cause as PLAN-21); a post-merge `Run JMH Benchmarks`
  run had not finished.
- Lesson store, 2026-11-02: the local store holds 7 lessons. `2026-10-31-12-003`, `-006`, `-007`, `-009` and the
  six `2026-10-31-13-00x` theme lessons recorded as filed on 2026-10-31 are no longer there, and I do not know
  why (no trace in this ledger's log beyond their filing). Their drafted bodies are still in the session
  scratchpad. Not re-filed without the operator's say-so.
- Sonar, 2026-11-02: gate OK and 0 open on #849. The project-wide query on the default branch returns 44 open
  issues (23 MAJOR, 13 MINOR, 5 CRITICAL, 3 INFO; 41 code smells, 3 bugs; rules `java:S8714` x11, `S5778` x7,
  `S4449` x4, `S8688`, `S8786`, `S3776`, `S2589` x3 each, others), the newest created 2026-10-10, so all predate
  this epic's plans. None belongs to a plan's own PR, but the standing "fix every Sonar finding immediately"
  policy covers them. Owned since 2026-11-04 by PLAN-23 (staged). Done when a `branch=main` query returns 0 open
  or only issues with a recorded dismissal; the orchestrator re-reads it when PLAN-23 lands.
- Executor drift: RESOLVED as of PLAN-21 (2026-11-02): `executor_version` 0.1.2046 equals `plugin_version`
  0.1.2046 after the operator's marshall-steward run (#848). It affected PLAN-06, 07, 12 and 20. Plans started
  while the regeneration was in flight may still show mixed versions; PLAN-08 is one of them.
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
