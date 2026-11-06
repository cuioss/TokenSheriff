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
  SHIPPED as #855 (`0c56583e`), see `landings/PLAN-23.md`: 39 merged paths vs 32 declared (32 matched, 7 added tests
  and a fixture, 0 missing). Main's open Sonar issues went from 44 to 1 (a residual `S8786`, folded into PLAN-15).
  PLAN-22 and PLAN-17 are unblocked; both re-read the changed files at outline.
- PLAN-10 — shipped as #856 (`02c0fd3a`), see `landings/PLAN-10.md`. 23 merged paths vs 11 declared (17 added, 4
  missing): the DPoP issuance check sits at the token-endpoint seam, not in `AuthorizationCodeFlow`; PAR was settled
  as documentation, so the PAR files are untouched; eleven doc paths and a pre-existing DSL-JSON fix came along.
  Staged specs that declare a path it changed: PLAN-14 (`TokenEndpointClientTest.java`, now unblocked), PLAN-18 and
  PLAN-19 (`doc/client/*` pages, the client README, `architecture.adoc`, `retrieval-flow.adoc`, `threat-model.adoc`),
  PLAN-23 (`RefreshFailureClassificationTest.java`, a collision the gate did not predict, see Watches). All re-read
  those at their own outline.
- PLAN-13 — shipped as #861 (`7145aebb`), see `landings/PLAN-13.md`. 20 merged paths vs 14 declared (8 added, 2
  missing): all six deliverables corroborated in the diff (templates aligned, `DUPLICATE_JWKS_KID` 168 to 171, separate
  `maxHttpResponseSize`, three new egress ranges, new record 172). PLAN-15 is unblocked; it re-reads the two converters
  PLAN-13 changed.
- PLAN-14 — shipped as #862 (`f29ca9cb`), see `landings/PLAN-14.md`. 16 merged paths vs 8 declared (10 added, 2
  missing). Four deliverables done plus two fail-closed production fixes the spec did not name (a repeated member in a
  token response; non-https endpoints in an https discovery document, and a `Content-Type` check). PLAN-25 re-reads
  `DiscoveryResolver.java`.
- PLAN-22 — shipped as #863 (`38aefc0a`), see `landings/PLAN-22.md`. 46 merged paths vs about 20 declared. All five
  deliverables corroborated; two items dropped as already correct (the rotation-counter wrap, the `AccessTokenCache`
  iat-less Javadoc). Seven ADRs (0020 to 0026) and the ADR-0017 amendment landed. Two fail-closed behaviour changes:
  `TokenValidator.close` (in the spec) and a refreshed ID token that fails validation now quarantines a rotated session
  in `TokenLifecycleManager` (NOT in the spec, which said Javadoc only). PLAN-17 re-reads `TokenValidator.java` and
  `TokenSheriffClientProcessor.java`; PLAN-19 re-reads `doc/adr/`, `doc/README.adoc` and `AGENTS.md`.
- PLAN-15 — shipped as #867 (`262369d6`), see `landings/PLAN-15.md`. 23 merged paths vs 14 declared. Five of six deliverables
  done; TV-4 (the enum-test removal) was undone by operator decision after the `weakened_test` check flagged it. Beyond the spec: the
  DSL-JSON truncated-escape fix reached `DpopProofValidator` and the three client sites PLAN-25 had planned to sweep, so PLAN-25
  deliverable 2 became a class-level guard test. `main` Sonar is now 0 open.
- PLAN-16 — shipped as #868 (`306e6053`), see `landings/PLAN-16.md`. 12 merged paths vs 5 declared. All five deliverables
  corroborated, plus a production change the spec did not name: `ClientAuthenticationFailedException`, raised by
  `TokenEndpointClient` for a 4xx `invalid_client` or exactly 401 `unauthorized_client` (the spec had the wrong code).
  The plan's ADR draft was parked and is FOLDED into PLAN-25 deliverable 7(h).
- PLAN-17 — Javadoc-only but touches many code-plan files; run after all code plans in WS-01/WS-03.
- PLAN-18 → PLAN-19 — multi-file doc overlap (D-11 xref sweep); strictly sequential.
- PLAN-25 (staged 2026-11-05, WS-05): the terminal leftover sweep, seven deliverables (native-image `@BearerAuth`
  coverage, client DSL-JSON sites, dead code, residual tests, two project questions, a log-record sanitisation sweep,
  the PLAN-13 and PLAN-14 advisory residuals). Runs last, after PLAN-15 to PLAN-24; the orchestrator folds those plans'
  unowned residuals into it and takes the split decision again before emitting.

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
- 2026-11-05 — PLAN-13 recorded as `launched` then `running` on the operator's word that it started (the plan
  worktree `plan-13-log-transport-hygiene` exists; the `plan_marshall_plan_id` is not yet stamped because the plan's
  directory under `.plan/local/plans` was not there to read; stamp it from the first inbox message sender).
- 2026-11-05 — PLAN-10 (#856) reconciled as shipped from its inbox landing (`landing-check`: incomplete, only
  `effort`, see Open Defects), corroborated against the PR state (`merged`), the 23-path merge diff, all 24 PR
  checks green, the merged source for each of the three spec deliverables, and Sonar read directly (gate OK, 0 open on
  the PR). Findings closed: C-3, C-8c, C-8d. Two deviations from the spec, both stricter: the issuance check sits
  at the token-endpoint seam (all grants inherit it) and also rejects an absent or malformed `cnf.jkt`; PAR was
  settled as documentation. Nine candidate-lessons dispositioned: 001, 003, 005 to 009 are plan-marshall-owned and went
  into the 2026-11-05 PLAN-10 addendum of `upstream-plan-marshall-report.md` as DISCARDED locally (nothing sent, no
  local lesson); 002 is FOLDED into PLAN-22 (its follow-up: the step-up spec text and the `StepUpHandler.verifyResult`
  `@throws` omit the negative-`max_age` rejection; expected surface updated, +2 entries); 004 (DSL-JSON unchecked
  exception) is FOLDED into PLAN-15 on the operator's choice among four options, as new deliverable 6 for the four
  validation-module sites (expected surface updated, +4 hypotheses; the plan reaches six deliverables and stays
  unsplit with a recorded rationale: one seam repeated four times, test-only with a minimal-fix clause). I had
  recommended a separate plan; the operator chose the fold. The three client-side sites are not covered by it (Watch).
  Not acted on: the `3f1ab1b` commit message that overstates CLIENT-11 coverage (a merged commit message cannot be
  corrected), and the deeply-nested-JSON stack-overflow observation (pre-existing, outside the epic's findings).
- 2026-11-05 — PLAN-23 (#855) reconciled as shipped from its inbox landing (complete, `effort=medium`), corroborated
  against PR state (`merged`), the 39-path merge diff, all 24 PR checks green, the merged source for the five
  bug-class and nullness sites, both operator-flagged tests, and Sonar read directly (PR gate OK, both PR issues
  `CLOSED`/`FIXED`; `main` at the merge analysis: 1 open issue, down from 44). The landing's headline is slightly
  stronger than the tree: one issue, `java:S8786` at `JwtTokenTamperingUtil.java:450`, is still open on `main`
  because the analyser flags the possessive-quantifier rewrite too. Disposition: FOLDED into PLAN-15 deliverable 1
  (the same helper TV-6 edits), expected surface +1 entry; confidence above 90% (a tool finding, a test-only file, a
  plan that already edits it), so I decided it without asking. Three candidate-lessons: 001 and 002 are
  plan-marshall-owned and went into the 2026-11-05 PLAN-23 addendum of `upstream-plan-marshall-report.md` as
  DISCARDED locally; 003 (an owed `architecture enrich` hint) is outside the epic, discarded here and kept as an
  operator-owed item in Watches. I also found that PLAN-23's complexity extraction changed `TokenBuilder.mapClaim` to
  count a `StrictStringMapper` rejection (the PLAN-11 advisory), so I added a re-read note to PLAN-24's claim labels
  (its counter assertions should expect the count; no surface change). PLAN-22 is unblocked.
- 2026-11-05 — Two operator instructions after the PLAN-22 start. (1) The missing lessons (`2026-10-31-12-003`, `-006`,
  `-007`, `-009` and the six `13-00x` theme lessons): the operator said to ignore them if they are the ones plan-marshall
  already fetched. I could not confirm that premise from the ledger (their disappearance was never traced), so I record
  the item as DROPPED on the operator's say-so and flag the unconfirmed premise. Nothing is re-filed. (2) "Create a
  final followup plan for all the leftovers": nothing of the kind existed (PLAN-22 is a residual hygiene plan for
  PLAN-01 to PLAN-06 items, not a terminal sweep). STAGED PLAN-25 (`plans/PLAN-25-epic-leftover-sweep.md`, WS-05,
  ordinal 25): five deliverables, the epic's last plan. Carried: (1) native-image coverage of class-level
  `@BearerAuth` without `@RegisterForReflection` plus the two unverified CodeRabbit notes and `StrictStringMapper`
  native exercise (PLAN-08 and PLAN-11 residuals); (2) the three client-side DSL-JSON sites that PLAN-15's
  validation-only sweep does not reach (the operator's call, so not folded into PLAN-14); (3) delete
  `ClaimValue.isNotPresentForClaimValueType()`, which has no production caller; (4) three residual tests (the
  `params == null` ECDSA branch, `PsychicSignatureAttackTest` non-vacuity, the JWE top-level `null` header);
  (5) two project-level questions to settle at outline (`test-impl-mapping.adoc`, a Quarkus key for `maxStaleness`).
  Verified in `0c56583e` and DROPPED rather than carried: the query-after-fragment ordering (fixed), the `iat`-less
  `put` (fixed), the `LOADING` status and `destroy()` catch (fixed), env-only multi-key JWE (documented, pinned by a
  test). The parser reads it as declarative, 19 paths, comparison determinate, no overlap flagged. Five deliverables,
  below the six threshold (deliverable 5 holds two questions); the rationale for staying unsplit is in the spec. It is sequenced LAST (after PLAN-13 to PLAN-24) and I must fold the residuals of those landings into it
  before emitting. Not carried because they are not plan work: forwarding `upstream-plan-marshall-report.md`, and the
  owed `architecture enrich` hint; both stay with the operator.
- 2026-11-05 — PLAN-13 (#861, `7145aebb`) and PLAN-14 (#862, `f29ca9cb`) analysed, `landings/PLAN-13.md` and
  `landings/PLAN-14.md` written. Both merged, both PR gates OK, `main` Sonar still 1 open (the known `S8786`, PLAN-15).
  I read the merged diffs rather than trusting the messages: PLAN-13's six fixes and PLAN-14's two production
  narrowings are real; the `weakened_test` flag on the TC-9 move is a false positive (same two test methods before and
  after). Rows stamped, seven PLAN-13 findings (V-13, V-14, V-15, V-17a, V-17h, D-3, D-4) and TC-7 marked FIXED, TC-9
  unit hygiene fixed. Ten candidate lessons: 001 to 005 of PLAN-13 and 001 to 003 of PLAN-14 are plan-marshall-owned
  (build-maven junk findings, the change ledger not recorded for finalize commits, a pending non-required check
  stalling the arms, merge-queue wait and post-merge benchmark wait, usage accounting across the phase boundary,
  argparse verb paraphrase, the `weakened_test` check on a file move) and went into a 2026-11-05 addendum of
  `upstream-plan-marshall-report.md`, NOT sent (the report was filed upstream on 2026-11-05 up to the PLAN-23
  addendum). The two project-owned lessons (PLAN-13-006 log-injection sweep, PLAN-14-004 header line-break variants)
  and the two landings' advisory residuals were FOLDED into PLAN-25 as new deliverables 6 and 7, on the operator's
  standing instruction that all leftovers go into the one final plan; confidence above 90%, so I did not ask. That
  takes PLAN-25 to seven deliverables, above the six threshold: the rationale and the emit-time re-decision are in its
  spec. I read the sanitisation sites at `7145aebb` first: `JWKSKeyLoader` line 423, `JwksParser` line 114 and about a
  dozen `KeyProcessor` calls log remote text unsanitised. PLAN-14 is the eighth producer with an unstamped `effort`.
  Slots: 2 of 3 are free (PLAN-22 still running). PLAN-24 and PLAN-15 are the candidates for `next`; not emitted.
- 2026-11-06 — PLAN-22 (#863, `38aefc0a`) analysed on the operator's `analyze`, `landings/PLAN-22.md` written. Merged
  through the merge queue; 24 checks green, Sonar gate OK on the PR, `main` Sonar still 1 open (the known `S8786`,
  PLAN-15). I read the merged diffs instead of trusting the message: the sanitiser consolidation is behaviour-preserving
  (the control-character sets are identical), `TokenValidator.close` is as specified, two spec items were already
  correct and are dropped, and `TokenLifecycleManager` carries a behaviour change the spec did not name (a refreshed ID
  token that fails pipeline validation quarantines a rotated session; tested, fail-closed). Row stamped and shipped. No
  candidate lessons. Open: `JweDecryptorTest` and `AccessTokenCacheTest` stay over 400 lines with no "why not" recorded,
  and the budget itself is not in any repo document I can read: recorded as a Watch, not a plan, because it needs the
  operator to say whether the budget is real. PLAN-25 deliverable 6 told to reuse the new `ErrorTextSanitizer`
  overload. PLAN-17 and PLAN-19 told what changed under their declared paths. Slots: 2 of 3 occupied (PLAN-15,
  PLAN-16); PLAN-24 is admissible for `next`, not emitted.
- 2026-11-06 — PLAN-16 (#868, `306e6053`) analysed on the operator's `analyze`, `landings/PLAN-16.md` written. Merged
  through the merge queue; 24 checks green, Sonar gate OK on the PR, `main` Sonar still 1 open (the known `S8786`,
  PLAN-15). I read the production diff rather than trusting the message: `clientAuthenticationFailed` is an exact-shape
  allow-list, evaluated before `credentialRejected`, with a fixed-text message (CWE-117), and it widens no acceptance.
  The spec said a wrong client secret answers `invalid_client`; Keycloak answers 401 `unauthorized_client`, which turned
  a test-only plan into a library change. My spec stated that wire value in a deliverable with no claim label, so it was
  never checked: an authoring lapse of mine. From now on a spec that names an external wire value (an error code, a
  status) carries it as a HYPOTHESIS claim label. Rows stamped (pr 868, not the closed predecessor 866), TC-1 residual,
  TC-3, TC-4, TC-8 and IT gap 10 marked FIXED. Five messages archived. Candidate lessons: 001 (spec wire value), 002 (IT
  assertions must name the specific rejection), 003 (a nitpick commit re-runs every head-bound gate, about 1.5 hours)
  and 004 (an ADR number collided with #863) are plan-marshall-owned and went into a 2026-11-06 addendum of
  `upstream-plan-marshall-report.md`, NOT sent. The plan's parked ADR draft (its number 0020 is taken) is preserved at
  `drafts/adr-draft-failed-client-authentication.adoc` and FOLDED into PLAN-25 as deliverable 7(h) with the missing
  client-spec sentence and the index rows, on the standing "all leftovers in the one final plan" instruction;
  confidence above 90%, so I did not ask. A grep of the IT sources finds no weak assertion shape left, so lesson 002
  needs no project sweep. Slots: 1 of 3 occupied (PLAN-15); PLAN-24 is admissible for `next`, not emitted.
- 2026-11-06 — PLAN-15 (#867, `262369d6`) analysed on the operator's `analyze landed`, `landings/PLAN-15.md` written. It merged while
  the cleanup pass ran. Merged as `262369d6`; 24 checks green, Sonar gate OK on the PR, and `main` Sonar is 0 open (the `S8786`
  is gone). I read the production diffs of all nine main-source files: every change is a catch widened to
  `IOException | ArrayIndexOutOfBoundsException`, plus sanitised log or exception text in `DiscoveryResolver`, `DpopProofValidator`
  and `JwksParser`. The landing's claim that `RevocationClient`, `JWKSKeyLoader` and `JweDecryptor` still hold unguarded DSL-JSON
  reads is refuted by a `git grep` (none of them call DSL-JSON directly); the one remaining read is `MapRepresentation.fromJson`,
  covered by its two callers. Rows stamped (pr 867); TV-6, TV-7 and gaps VAL 2, 3, 5, 6, 7 marked FIXED, TV-4 NOT FIXED by operator
  decision (trivial enum tests stay as a project convention). Eight candidate lessons: 001, 002, 005, 006 and 007 are
  plan-marshall-owned (sanctioned test removal vs `weakened_test`; post-push criteria; non-discriminating test fixtures; finalize-time
  commits without a change-ledger row, a second report of the PLAN-13 cause; `effort resolve-target --dispatch-context` drift) and went
  into a third 2026-11-06 addendum of `upstream-plan-marshall-report.md`, NOT sent; 003 (defect-class survey) is split between that
  addendum and PLAN-25 deliverable 2 (now a guard test over every DSL-JSON call); 004 (a CWE-117 comment over a raw exception) is
  FOLDED into PLAN-25 deliverable 6; 008 (client tests need the validation test-jar) was run as an `architecture enrich` hint and
  goes through its own small PR. All on the standing "leftovers go into the one final plan" instruction, confidence above 90%, so I did
  not ask. PLAN-25 stays at seven deliverables. Slots: 0 of 3 occupied; PLAN-24 is the next candidate, not emitted.
- 2026-11-06 — `next` emitted PLAN-17, PLAN-18 and PLAN-24 as one block (N=3, R=0, `auto_emit` false; the gate admitted all
  three, no overlap prompt: PLAN-18 and PLAN-24 share only `security-reference.adoc`, one file). PLAN-19 held back (depends on
  PLAN-18, 8 shared doc files), PLAN-25 last by design. The operator then reported all three started; recorded as `launched`,
  not `running`.
- 2026-11-06 — PLAN-24 (#876, `90f8474a`) analysed from its inbox landing (complete, `effort=medium`, `model` self-reported),
  `landings/PLAN-24.md` written. Corroborated against the merge diff (6 files), PR state (merged 16:42:18Z, 24 checks, none
  failing), and Sonar read directly (PR gate OK, its one `java:S5778` issue `CLOSED`/`FIXED`, `main` 0 open). One read-only
  leaf did the three-layer reading and I re-read every `checked` point. No blocker or major defect: the production change is one
  line (`ClaimName.java:179`) and fail-closed. Minor findings: the spec had no Done-when and its "an azp-present token is
  unchanged" contradicts its own mechanism (the PR states the narrowing); the `sub` sentence at `security-reference.adoc:35`
  is overstated (`IdentityMapper` stringifies, `TokenLifecycleManager` compares the two subjects as strings); an explicit JSON
  `null` is dropped by `MapRepresentation.fromJson` and reads as absent (verified, pre-existing from #511), so "must be a JSON
  string" is slightly broad; the eight-row client_id matrix tests one behaviour; fixture names drifted. Row stamped (pr 876).
  PLAN-24's Expected Surface now names `AudienceValidatorTest.java` (the gate under-declared it; mechanical call-site edit).
  The doc and test residuals are folded into PLAN-25 as new item 7(i) (the `sub` sentence, null-is-absent wording and test
  with one operator question on rejecting null, test hygiene), surface +7 entries, `corpus surfaces` reads it declarative with
  38 paths; confidence above 90% (standing "leftovers go into the one final plan" instruction; a question, not a behaviour
  change), so I did not ask. Four candidate-lessons: 001 (`architecture search` has no `--module`), 002 (`qgate query` is not a
  verb) and 003 (`java:S5778` slips past the pre-commit profile, suggested for `pm-dev-java:junit-core`) are plan-marshall-owned
  and went into a fourth 2026-11-06 addendum of `upstream-plan-marshall-report.md` as DISCARDED locally, NOT sent; 004 (null
  claim dropped before any mapper) FOLDED into PLAN-25 item 7(i). Slots: PLAN-17 and PLAN-18 launched (PRs #878, #877 open),
  PLAN-24 shipped, so 1 of 3 free. PLAN-19 waits for PLAN-18.
- 2026-11-06 — PLAN-18 (#877, `af19b9c2`) analysed from its inbox landing (`landing-check` `complete: false`, `effort` missing,
  see Open Defects), `landings/PLAN-18.md` written. Corroborated against the merge diff (20 files, +134/-79), PR state (merged
  18:30:25Z, 24 checks, none failing) and Sonar read directly (PR gate OK, `main` 0 open). One read-only leaf did the three-layer
  reading and I re-read the `checked` points I rely on, including all four major ones. The six findings are FIXED in
  `findings-disposition.md` (D-2, D-6, D-9, D-13, D-14, V-17d). Layer 1: the spec's D-13 drop was mis-grounded (the cleanup
  re-grounding checked the contrast file `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc`, not the defect site
  `jwt-validation-performance.adoc:18` the source report named), so D-13 is recorded as fixed by #877, not by `4f8f3eb6`; the
  Expected Surface under-declared 9 of 20 files. Layer 3, REPORTED TO THE OPERATOR: four major documentation contradictions of
  the code remain in docs #877 did not edit or half-edited (the COMMONS-3 SSRF allow-list scope across three docs, the
  `DiscoveryResolver` class Javadoc, and two client test-strategy claims), plus six minor wording points; none is a code defect.
  FOLDED into PLAN-25 as new item 7(j) on the standing "leftovers go into the one final plan" instruction (confidence above
  90%), surface +15 entries, one new corroborated claim, with ONE operator question recorded inside it (apply a host-address
  egress policy to the client back channel, or make the docs follow the code; default: docs follow the code). The seven
  candidate-lessons are plan-marshall-owned process notes: DISCARDED locally and written to a fifth 2026-11-06 addendum of
  `upstream-plan-marshall-report.md`, NOT sent; 006 (`qgate query`) is a recurrence of PLAN-24-002. PLAN-19 was re-grounded at
  `af19b9c2` (six stale verdicts re-stamped; the `<<` count is 69 not 68, `doc/client/requirements.adoc` gained one from #877, plus
  one `<<` outside the 12 files; `security-reference.adoc` and `threat-model.adoc` `cui-http-client` lines moved or newly listed),
  its text and surface updated, and it is now emittable: 2 of 3 slots free, no shared path with the launched PLAN-17.
  PLAN-25 runs last.

- 2026-11-06 — PLAN-17 SHIPPED (#878, merge `d0fa0c7f`, 19 files +198/-162, `landings/PLAN-17.md`); inbox drained (seven
  messages `plan-17-javadoc-examples-001` to `-007`, archived). Closes J-1 to J-7 and J-9 to J-12 (`findings-disposition.md`
  updated). Three-layer reading by one read-only leaf, every `checked` point re-read: ONE layer-3 DEFECT (major, pre-existing,
  fail-closed): with JWE configured `TokenStringValidator` admits up to 32 KiB while `NonValidatingJwtParser.decodeInternal`
  rejects above 8 KiB with `IllegalArgumentException`, which the refresh pipeline and `BearerTokenProducer` do not catch; reported to
  the operator at once and folded into PLAN-25 item 7(k) with a required fail-before test, NOT fixed inline (the lane's `finding`
  message 006 had it unverified; the reading confirmed it). Eight minor Javadoc residuals (the lane's four follow-ups plus
  `BearerTokenStatus`, `IssuerConfig` "order of preference", `NonValidatingJwtParser` `@throws`, two test-coverage overclaims)
  folded into 7(k); the empty `Bearer ` statement is not settled by reading. The Expected Surface under-declared by three files
  (19 touched, 16 declared) for the third time in this epic. Candidate-lessons 001 to 005 are plan-marshall-owned: DISCARDED locally
  and written to a sixth 2026-11-06 addendum of `upstream-plan-marshall-report.md`, NOT sent; 001's project-owned half (read the branch
  before writing behaviour prose) is carried by 7(k). The landing is `complete: false` (missing `effort`). PLAN-19 is still running.

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
  `upstream-plan-marshall-report.md` holds the evidence for the plan-marshall maintainers. FILED 2026-11-05 at the
  operator's instruction as `quality-report-001.md` in the plan-marshall repo's `live-blockers` inbox (kind `finding`,
  the whole report to the PLAN-23 addendum; the target inbox's own validation was not run, since the `inbox validate`
  verb works on this repo's epic). Addenda written after it are not sent.
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
- 2026-11-06 — Open defect in merged code (PLAN-17 three-layer reading, not yet owned by a running plan): JWE tokens of 8193 to 32768
  bytes fail with an unhandled `IllegalArgumentException` instead of a `TokenValidationException` (`NonValidatingJwtParser.java:294-295`
  against `TokenStringValidator.java:65-67,96`); fail-closed but unusable above 8 KiB; PLAN-25 item 7(k)(1), operator decision on the
  fix direction recorded there. Retire when a fix plan lands with its test.

## Watches

- 2026-11-06 — PLAN-17 landing residue (#878; landing message, partly verified): (1) `boundary_monotonicity` flagged `6-finalize` and
  the execute-phase tokens were counted twice (about 186k), so the 22,258,542 total is an overstatement; the lane noted it in its
  decision log and did not fix it. (2) Phase subagents self-transitioned the plan and skipped the orchestrator's Q-gates and the
  `phase-boundary` stamp, an inline review-fix commit wrote no change-ledger row, and CodeRabbit's quota refusal needed a manual
  re-review (all upstream-owned; sixth addendum). (3) The CodeRabbit check ended `NEUTRAL`; ten of its eleven comments needed a fix
  commit, which is the cost of prose written from names and intent (see 7(k)). (4) The 400-line test budget Watch and the e2e teardown
  hang are unchanged. Retire (1) to (3) once the operator accepts them; they need nothing further from this epic.
- 2026-11-06 — PLAN-18 landing residue (#877; landing message, partly verified): (1) the squash commit on `main` carries the
  CodeRabbit "Summary by CodeRabbit" block and a second `Co-authored-by` trailer; the merge-queue path offers no per-enqueue
  message override and `ci repo` has no verb for the default merge-message setting (commit body read on `af19b9c2`, the cause not
  checked). (2) Two phase-6 usage figures were entered as estimates, so the finalize token total (19,870,958 overall) is a slight
  overstatement. (3) Functional-review round 1 was closed by operator override over an unevidenceable PR-body criterion; later
  rounds evidenced it. Retire (1) when a merge-message mechanism exists or the operator accepts the residue; (2) and (3) need
  nothing further. The CodeRabbit quota stall recurs (see the PLAN-13 and PLAN-14 residuals below).
- 2026-11-06 — e2e teardown hang (PLAN-24 landing, #876): the `e2e-tests` job was cancelled by its 30-minute job timeout
  after all 30 tests passed, and the landing says it recurs on `main`; a rerun cleared it (`SUCCESS` at this analysis). Unverified
  beyond the landing message and the PR's final check state; no plan staged, a follow-up on the teardown is owed. Retire it
  when a clean e2e run on `main` is observed twice or a cause is found. Seen once before as the PLAN-13 merge-queue ejection on a
  hung `e2e-tests` job (see the PLAN-13 and PLAN-14 residuals below).
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

- > ↪ Relocated to `settled.md` § "Sonar read 2026-10-30 (PLAN-01 to PLAN-05 PRs)" — point-in-time read of closed PRs, superseded by the 2026-11-06 direct reads
- > ↪ Relocated to `settled.md` § "Sonar read 2026-10-30 (PLAN-06 and PLAN-07)" — point-in-time read of two merged PRs, superseded by the 2026-11-06 direct reads
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
- > ↪ Relocated to `settled.md` § "Sonar read 2026-11-01 (PLAN-12 and PLAN-20)" — point-in-time read of two merged PRs, superseded by the 2026-11-06 direct reads
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
- > ↪ Relocated to `settled.md` § "Lesson store 2026-11-02" — the missing lessons were DROPPED on the operator say-so 2026-11-05
- > ↪ Relocated to `settled.md` § "Sonar read 2026-11-02 (PLAN-21, 44 open on main)" — the 44 open issues were closed by PLAN-23 and PLAN-15; main reads 0 open on 2026-11-06
- > ↪ Relocated to `settled.md` § "Executor drift (resolved)" — resolved as of PLAN-21 (2026-11-02), the executor and plugin versions match
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

- TC-9 (IT part) — integration tests use `useRelaxedHTTPSValidation()` and per-IT trust-all managers;
  a real truststore for the Keycloak container is IT-cost and not staged. Re-check if IT TLS
  infrastructure changes or a TLS-related IT regression appears.
- `source-report/` line numbers are stale; plan specs carry the current locations.
