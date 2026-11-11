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
- PLAN-25 (staged 2026-11-05, WS-05; final cleanup 2026-11-07): the epic's single closing plan, ten deliverables (native-image
  `@BearerAuth` coverage, a DSL-JSON guard test, dead code, residual tests, project questions incl. the test budget, a log-record
  sanitisation sweep, the advisory residuals 7(a)-(l), GitHub #763, the e2e teardown hang, the old per-plan residuals), a Residual
  Register of every open epic item and a Done-when with the Sonar zero target. Folded and re-grounded at `d1af3ebb`; PLAN-01 to
  PLAN-24 have all merged, nothing else is staged or running, so it is emitted alone. The plan lifecycle may cut it at outline.
  SHIPPED 2026-11-09 as #883 + #884 (see Decisions and `landings/PLAN-25.md`).
- PLAN-27 (staged 2026-11-09, WS-05; slug `test-size-guideline-restore` is historical): corrects two PLAN-25 outcomes. (1) An explicit JSON null in
  `iss`, `sub`, `jti`, `client_id` and `azp` is read as absent again (code in `StrictStringMapper`, `ClaimName`, `MapRepresentation`, `TokenBuilder`, their
  tests and `security-reference.adoc`; the strict type check and the numeric-`sub` refusal stay). (2) `AGENTS.md` § Test Class Size back to the 500-line
  guideline, plus three client test Javadocs that cite a "400-line budget". Three deliverables, thirteen files, disjoint from PLAN-26. Staged on the
  operator's decision on the PLAN-25 landing (see Decisions). Emit only on the operator's word; it may run beside PLAN-26.
  SHIPPED 2026-11-10 as #887 (`aeb86e4d`), see `landings/PLAN-27.md`: 18 files merged against 13 declared (the spec's Expected Surface omitted the renamed
  pin test, `SubjectClaimTypeRejectionTest`, `test-impl-mapping.adoc` and a fourth "400-line" Javadoc; a `CLAUDE.md` build note was added outside the spec).
  Layer 3 clean by reading; the doc figures and the `CLAUDE.md` note are wrong (Open Defects).
- PLAN-28 (staged 2026-11-10, WS-05; operator: "gather all leftovers, open issues and watches and put them into a cleanup plan; after #26 lands, add corresponding
  leftovers as well"): the epic cleanup sweep, `plans/PLAN-28-epic-cleanup-sweep.md`. Seven items: (1) DPoP replay cache and proof logging, (2) the documentation PLAN-27 shipped
  wrong, (3) test pins and gaps left without evidence, (4) verify-or-soften and record, (5) native-image `@BearerAuth` coverage, (6) the e2e teardown hang and screenshot flake, (7)
  the PLAN-26 leftovers (FILLED 2026-11-10, see Decisions). 39 resolved surface entries, declarative; the cross-check finds no overlap prompt, comparison determinate; no blocking
  verdict row. Nothing else is running. Emit only on the operator's word.
  SHIPPED 2026-11-11 as #892 (`c7b2675b`; recreated from #891, closed unmerged), see `landings/PLAN-28.md`: emitted and started on the operator's word 2026-11-10, run by the lane as nine
  deliverables in one PR (35 files, +1263/-184). 10 of the 35 merged files were not in the declared surface (seven added by the lane's own Q-Gate rounds) and 14 declared files were not touched (the
  evidence-only closures D3(b), D4, D6, D7). No blocker or major by reading; the minor residue is one Open Defect. The first merge-queue entry was ejected by the e2e teardown hang (Watch).
- PLAN-26 (staged 2026-11-09, WS-01; slug `jwks-key-ids-contract-pin` is historical, the plan carries two deliverables): SHIPPED 2026-11-10 as #888 (`7520e165`), see
  `landings/PLAN-26.md`. 18 files merged against 12 declared (the Expected Surface omitted the integration test, `references.adoc`, `test-strategy.adoc`, `test-impl-mapping.adoc` and
  the two READMEs; no collision resulted). Deliverable 1 is the `getKeyIds()` contract test (test only); deliverable 2 is `InsufficientScopeChallengeParser` on a shared
  `BearerChallengeParams`, with `StepUpChallengeParser` now refusing a valueless segment, a following scheme, other control characters and a header over 8192 characters (a
  fail-closed tightening wider than the spec's list, declared in the PR body and stated in `step-up-and-logout.adoc`; the consumer effect is a Watch). Layer 3 minor findings only; carried by PLAN-28 deliverable 7 (a) to (f).

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
- 2026-11-07 — PLAN-19 SHIPPED (#879, merge `d1af3ebb`, 50 files +242/-157, documentation only, `landings/PLAN-19.md`); inbox drained
  (twelve messages `plan-19-doc-structure-hygiene-001` to `-012`, archived). Closes D-5, Q-12, D-7, D-10, D-11, D-12, D-15
  (`findings-disposition.md` updated). Three-layer reading by one read-only leaf, every `checked` point re-read where it is recorded:
  no blocker and no code defect. The documentation the landing left or newly contradicted is folded into PLAN-25 7(l) and 7(j)(3):
  COMMONS-2, `transport.adoc` and the commons threat model still say plain HTTP is always rejected beside the new cleartext opt-in
  text; the Quarkus parent README credits the wrong class; the `RefreshFailureClassification` / `RefreshFlow` Javadoc and ADR-0010
  line 187 still carry the "untouched and still valid" wording ADR-0010 retracted; no AsciiDoc link check was evidenced. The spec's
  blank-line premise was already stale at base; the Expected Surface under-declared by 33 files (50 touched) for the fourth time; the
  22 ADR status flips were a sixth deliverable no spec section authorised. Candidate-lessons 001 to 008 (documentation accuracy):
  DISCARDED, shipped in the PR, the rule is a Standing Constraint of PLAN-25 (006 and 008 are the causes of 7(j)(3) and 7(l)(4));
  009 to 011 are plan-marshall-owned: DISCARDED locally, written to a seventh addendum (2026-11-07) of
  `upstream-plan-marshall-report.md`, NOT sent. The landing is `complete: false` (missing `effort`).
- 2026-11-07 — FINAL CLEANUP (operator: "make a full cleanup, gather all open issues, check Sonar at main (0 issues), wrap the result
  into the final cleanup plan, land, emit"). Sonar `main` read directly: 0 unresolved issues, gate OK, 0 hotspots (the `search` verb's
  unfiltered 47 rows are closed issues; seventh addendum). PLAN-25 re-grounded at `d1af3ebb` (28 claims re-checked, five claims added),
  rebuilt as the epic's single closing plan: new items 7(l) (PLAN-19), 8 (GitHub #763, operator: "only #763, not the others"; #358 and
  #182 are deliberate non-goals), 9 (e2e teardown hang), 10 (old per-plan residuals), a Residual Register mapping every open item to a
  disposition, and a Done-when with the Sonar target. PLAN-25 holds ten deliverables; scope-guard rationale retaken (the operator
  asked twice for ONE plan; the plan lifecycle may cut it at outline).
- 2026-11-07 — PLAN-25 RUNNING (operator: "plan is running"), plan id `plan-25-epic-leftover-sweep`. OPERATOR DECISIONS on the four open
  questions, delivered to the plan's mailbox as `orchestrator-quality-report-001.md` (the spec is not edited under a running row):
  7(k)(1) JWE size mismatch: keep the 8 KiB parser limit and map the failure to a `TokenValidationException` (agreed default); 7(i)(2)
  explicit JSON null identifier claims: pin as observed (agreed default); 7(j)(1) back-channel egress policy: the docs follow the code
  (agreed default); 5(c) the 400-line test budget: NOT a hard rule but a guideline, and more than 500 lines should be avoided, so
  `AGENTS.md` states a 500-line guideline, no existing class is split for it, and classes this plan creates or edits stay at or under
  500 lines. The layer-3 JWE Open Defect keeps its owner (7(k)(1)) with the direction settled.
- 2026-11-09 — API SHERIFF REQUEST DISPOSITIONED (operator): A ignored; B "spec a test": PLAN-26 (`plans/PLAN-26-jwks-key-ids-contract-pin.md`,
  WS-01, staged, one deliverable, test-only: a contract test that runs the `getKeyIds()` points (never null and empty before init, active set
  only, every id resolves, blank omitted, duplicate once, immutable, snapshot stable, retired excluded in the grace period, status OK only with
  a usable set) through the HTTP, file and in-memory loaders the factory returns, with a mutation check per point; no file shared with the
  running PLAN-25, gate determinate with 0 overlap; admitted, not emitted); C: the operator wants the parser in TokenSheriff, proposal given;
  D and the reply: later, if released.
- 2026-11-09 — PLAN-26 WIDENED (operator: "make it one plan for both"): the `insufficient_scope` parser (API Sheriff part C) is folded into
  PLAN-26 as deliverable 2, so no PLAN-27 exists. Deliverable 2: `InsufficientScopeChallengeParser` in `token-sheriff-client` `flow`
  (sibling of `StepUpChallengeParser`; RFC 6750 scope-token grammar, duplicate and injected parameters rejected, the CR/LF/obs-fold/NUL
  header attack class, bounded header length and token count, no leak from a second challenge, parses and acts on nothing), one shared
  package-private `auth-param` splitter instead of a copy, `CLIENT-25` and its README, specification and threat-model rows, with a
  mutation check per guarding line. Two deliverables, so under the six-deliverable threshold. The queue slug and file name
  (`jwks-key-ids-contract-pin`) stay those of deliverable 1 (a row's slug cannot be edited). Gate: determinate, but it now shares FOUR
  files with the running PLAN-25 (`doc/client/README.adoc`, `requirements.adoc`, `specification/step-up-and-logout.adoc`,
  `threat-model.adoc`), so an overlap prompt is required at emit; recommended order: after PLAN-25 merges. Timing tradeoff recorded in
  the spec: deliverable 1 is wanted before the next release cut, deliverable 2 is new public API and not urgent.
- 2026-11-09 — PLAN-25 SHIPPED as two PRs (`landings/PLAN-25.md`): #883 (validation half, `47045031`) and #884 (client, client-Quarkus, ITs, all
  of `doc/`, quarkus-parent README, `aeb3fa49`); the plan grew to 163 files and the required review bot's 100-file cap forced a split late in
  finalize. Analysis by one read-only leaf, ten orchestrator re-reads. Measured by the orchestrator: Sonar `main` at `aeb3fa49` reads 0 unresolved
  issues (branch + resolved=false), 0 hotspots, coverage 93.2; the Maven, Integration, End-to-End and Scorecard workflows are green at both
  merge commits. The mailbox decisions of 2026-11-07 were NOT followed: on main the JWE limit is widened to 32 KiB, an explicit JSON null
  identifier is refused, the client back-channel has a default-on host-address egress policy, and `AGENTS.md` states "should stay within 400
  lines" plus "more than 1000 lines must be split" (four classes were split; the plan's own edit then took `NonValidatingJwtParserTest` to 1023
  lines). The landing says the operator answered the same questions with the opposite choices inside the run; the orchestrator cannot see that
  answer, so the divergence is an Open Defect awaiting the operator's word, not a settled decision. Slots: PLAN-26 is the only remaining plan.
- 2026-11-09 — INBOX DRAIN (60 messages from `plan-25-epic-leftover-sweep`: 1 landing, 59 candidate lessons): the landing reconciled as above
  (landing-check complete: true). 59 lessons dispositioned, all archived: 15 PROMOTED into the corpus (2026-11-09-16-001 to -015: test-class-split and
  attribution false positives, accepted-verdict carry-over, finalize-gate circular criteria, outline profile checks, review-bot file cap, merge-queue
  re-enqueue, post-merge wait on the benchmark workflow, `pr edit` body slot, loop-back close), the recurrences of two existing lessons
  (2026-11-05-23-002 split-aware test integrity, -003 head-dependent re-fire) appended to them, 37 more DISCARDED as re-filings of a promoted or
  existing lesson, and 7 script-failure signal-scan clusters DISCARDED (no cause extracted; the pickup text says file a lesson only on recurrence).
  Each has its own decision-log line. Project-owned residue from them: the Playwright screenshot flake that ejected #883 from the merge queue
  (Watch).
- 2026-11-09 — OPERATOR DECISION on the four inverted PLAN-25 decisions (walked through as mailbox A versus shipped B): (1) JWE: B stands, a
  5-part token is accepted up to the configured `maxEncryptedTokenSize`, at most 32 KiB; the operator asked what the 8 KiB limit would add when
  32 KiB is the usual configuration, and the answer (read in `JweDecryptor.java:95,239-249,589`, `JweDecryptionConfig.java:51`) was: little, since
  the 32 KiB cap was already what `TokenStringValidator` admitted, the inner JWS stays at 8 KiB, decompression is capped at 256 KB and `zip`
  must be `DEF` with compression enabled. The operator then confirmed "the JWE should be raised" (read as: the raised limit stays; the orchestrator
  took it as B, 32 KiB). (2) explicit JSON null in iss/sub/jti/client_id/azp: CORRECTED by the operator ("I said it only for the null values"):
  A applies to the NULL ONLY, so an explicit null goes back to "treated as absent" (reverting the `MISSING_CLAIM` refusal, the `MapRepresentation`
  explicit-null API and the `ClaimName.isExplicitNullRefused` hook); the strict string TYPE check STAYS: a numeric `sub`, any present non-string
  identifier and the strict userinfo `sub` remain refused ("keep it"). An earlier recording of this entry as "B stands" was wrong and is superseded.
  (3) client back-channel egress: B stands, the default-on host-address policy with `sheriff.client.allowed-egress-hosts` and
  `sheriff.client.allow-loopback-egress`. (4) test-size rule: A, the operator's 500-line guideline, so `AGENTS.md:124-131` is wrong. Decisions 2
  (the null half) and 4 are corrected by PLAN-27 (staged: `plans/PLAN-27-test-size-guideline-restore.md`, WS-05, three deliverables, thirteen files,
  none shared with PLAN-26); no class is split and the four splits already made stay. Consequence: decisions 1, 3 and the numeric-`sub` half of 2 are
  downstream-visible (a JWE up to 32 KiB accepted, a non-string identifier refused, private-range IdPs refused unless listed) and belong in any API
  Sheriff release note, held until a release and the operator's word; the null-refusal never shipped in a release (last tag `0.9.8`), so it needs none.
- 2026-11-10 — PLAN-27 ANALYZED (`landings/PLAN-27.md`, #887, `aeb86e4d`; PLAN-26 is still running, its PR #888 is open). Landing reconciled: row shipped, pr, landing
  and plan id stamped. Three-layer analysis by one read-only leaf, every `checked` point re-read: layer 1 three minor findings (the mutation recipe named a branch criteria
  (e)/(f) delete, the Expected Surface omitted five files, the per-claim refusal hypothesis is false for `jti` and conditional for `azp`/`client_id`); layer 2 four minor findings (the
  `AGENTS.md` figures, the `CLAUDE.md` note, the relative-only pin, the PR narrative's `azp`/`client_id` attribution) and one not shown by reading; layer 3 clean by reading, no
  fail-open (a null top-level member is dropped at parse, so a custom mapper and the `sub` guard never see it). No layer-3 defect, so nothing was reported as a fix candidate.
  `landing-check` was `complete: false` (three review-count keys `unknown`): recorded below. Corroboration of the null revert is by git and reading, not by a run: the
  4149-test and mutation results are the lane's claims. The 16 candidate lessons were dispositioned (inbox 001 to 016): 13 FOLDED as recurrences into three existing lessons
  (001-005 and 007-012 into `2026-11-09-16-003`, 006 into `2026-11-09-16-004`, 014 into `2026-11-05-23-007`), 2 PROMOTED (015 as `2026-11-10-09-001`, 016 as `2026-11-10-09-002`),
  1 DISCARDED (013, a one-off `CLAUDE.md` claim fixed in `abef336e`); each has a decision-log line. The Expected Surface of PLAN-27 is not corrected in the spec because the spec is
  shipped and no longer gated; the under-declaration is in the landing report (L1-2).
- 2026-11-10 — PLAN-28 STAGED (operator: gather the epic's leftovers, Open Defects and Watches into one cleanup plan, and add PLAN-26's after it lands; GitHub issues
  are NOT in scope, the operator said so). Gathered from this file's Open Defects and Watches (read in full). CARRIED into `plans/PLAN-28-epic-cleanup-sweep.md`: the DPoP
  defects, the PLAN-27 doc defects, the PLAN-25 no-evidence items, the e2e hang and its screenshot flake, the relative-only null pin. NOT CARRIED, with the reason in the spec's
  "Not carried": upstream-owned items (`effort`, incomplete landing facts, lesson routing, addenda, quota and merge-message residue), operator-owned release items, the docs-only
  Sonar skip (HYPOTHESIS: a reusable workflow in `cuioss-organization`), TC-9 (deliberately not staged since 2026-10-31, add only on the operator's word), informational notes.
  Re-reading the DPoP lines CORRECTED two claims of the 2026-11-09 Open Defect (growth is bounded by rate x TTL, and the insert cost is one full scan, not a full sort). Scope
  guard: seven items, above ~6, kept as one plan because the operator asked for one and the items are independent; the plan lifecycle may cut it along (1), (2)+(4), (3), (5), (6),
  (7), as PLAN-25 was cut. Sequencing: after PLAN-26, per the operator; PLAN-28 and PLAN-26 are not run together.
- 2026-11-10 — OPERATOR DECISION on PLAN-28 deliverable 1(a) (DPoP replay cache past `maxSize`): "soft cap is fine, document it". The soft cap STAYS: no hard cap, no refusal of new
  proofs, an in-window jti is never evicted. PLAN-28 now documents the behaviour and its bound (arrival rate x `nonceCacheTtlSeconds`, reclaimed by the periodic `evictExpired` sweep)
  in the `DpopConfig` Javadoc and in `doc/validation/threat-model.adoc` row DP1, and pins it with a test (in-window jtis past `maxSize` are all still refused as replays, and are reclaimed
  once their window closes). Re-read for this decision: that row calls the cache "bounded" and gives a 300 s TTL while `DpopConfig.java:69` defaults to 360 s; both corrected in the same
  item. Items (b), the per-insert full scan, and (c), the unsanitised `jti`/`htm`/`htu` log arguments, are unaffected and stay.
- 2026-11-10 — PLAN-26 ANALYZED (`landings/PLAN-26.md`, #888, `7520e165`; inbox `jwks-key-ids-contract-pin-001` to `005`). Landing reconciled: row shipped, pr 888, landing and
  plan id `jwks-key-ids-contract-pin` stamped; `landing-check` was `complete: true`. PR state, the merge commit and the 24 green checks were read through `ci`. Three-layer analysis by one
  read-only leaf, every `checked` point re-read at its line: layer 1 four minor findings (the spec asks for a behaviour-preserving splitter guarded by the unchanged sibling test while
  criterion (h) cannot be met without tightening the sibling; the (h) hypothesis names the wrong leak, the real one being a valueless segment that `parseParams` skipped; `performBackgroundRefresh`
  is package-private so criterion (f) was unusable from the factory test; the sibling test count was 14 by annotation, not 13) and an Expected Surface that omitted six files; layer 2 the
  sibling tightening wider than the spec's list with an existing test inverted (the leaf rated it major; adjudicated minor because the PR body declares it and its mutation record shows it
  load-bearing), two extra public constants, a thin statelessness test and an IT in no deliverable; layer 3 five minor findings and one info, none a blocker (all returned fields are sanitised,
  a second challenge never leaks in STRICT mode, bounds apply before the work, the scope grammar is exact). Runtime claims (4287 tests, the mutation runs, the e2e hang) are the lane's.
  The real defect the plan closed: `Bearer error="insufficient_user_authentication", Basic, acr_values="x"` gave `StepUpChallengeParser` an actionable challenge with `acr_values=x`.
  The five candidate-lesson and landing messages were dispositioned: 001 PROMOTED as `2026-11-10-14-001` (merged with 002, its follow-up), 002 FOLDED into it, 003 FOLDED into
  `2026-11-10-09-002` (the second invented manage-findings surface, now a verb), 004 PROMOTED as `2026-11-10-14-002`, 005 reconciled; each has a decision-log line. The leftovers were
  ADDED to PLAN-28 as deliverable 7 (a) to (e), with 10 new Expected Surface entries (all existing at `7520e165`), plus the e2e hang recurrence on deliverable 6 and the reactor-baseline
  evidence on 2(b). The gate re-read: PLAN-28 declarative, 39 entries, comparison determinate, no overlap prompt, `blocking_count` 0. PLAN-28 is NOT emitted; the operator's word emits it.
- 2026-11-10 — PLAN-28 EMITTED and STARTED on the operator's word ("emit PLAN-28", then "is running"), after the ledger was landed first (#889, `a16b20af`) at the operator's instruction. Gate on `a16b20af`:
  declarative, 39 entries, comparison determinate, no overlap prompt, `blocking_count` 0; one of three slots filled. The row was recorded `launched` then `running` on the operator's confirmation (`auto_emit` false).
  A cleanup pass ran next (corpus clean, invariants ok, settled-narrative relocation deferred, archive drain refused, restart verdict `not_ready` for the running plan and `registry_parity`) and the ledger was landed again (#890, `e61ceb42`).
- 2026-11-11 — PLAN-28 ANALYZED (`landings/PLAN-28.md`, #892, `c7b2675b`; inbox `plan-28-epic-cleanup-sweep-001` to `021`). Landing reconciled: row shipped, pr 892, landing and plan id stamped; `landing-check` was
  `complete: false` (three review-count keys `unknown`, recorded in the existing incomplete-landing Open Defect). PR state, the merge commit, the 24 green checks and the closed #891 were read through `ci`. Three-layer analysis by
  one read-only leaf, the cited points re-read at `c7b2675b`: layer 1 two minor findings (three spec premises stale on `main`: the new class-level endpoint exists, the screenshot comparison does not, the SpecIT is named in
  two docs; the unquoted-scope refusal rests on RFC 6750 text nobody could read) plus two info; layer 2 seven minor findings (the PR body says the thumbprint, replay, htm and htu messages were left out while the diff sanitises them,
  the over-cap test pins a probe not the cost, the concurrent test cannot reach the evict race, the embedded-payload attack test is mostly vacuous for `realm` and `error_description`, D3(d) closed with four rows and no recorded reason
  for three cases, D3(b) closed by a Javadoc record, the single-owner doc goal only partly met); layer 3 five minor findings and two info, none a blocker (every remote-text sink in `DpopProofValidator` is sanitised once for the log and the thrown
  message, the replay cache keeps an in-window `jti` and examines one node on an over-cap insert, STRICT refuses any unquoted scope and LENIENT is unchanged, the surrogate cut is correct). The residue is one Open Defect. Runtime
  claims (4363 tests, the scratch-run failure counts, the rsvg render, the 884-link check, the native run, the e2e hang) are the lane's. The `StepUpResultVerifier` hypothesis (PLAN-26 L3-3) is settled by reading: a polluted LENIENT value can only name tokens
  the resource server chose. The 20 candidate lessons were dispositioned (inbox 001 to 020): 6 PROMOTED as new lessons (`2026-11-11-12-001` the outline's affected files from a repository-wide search, `-002` the test-integrity phantom
  removals, `-003` the argparse class, `-004` the SVG render, `-005` raw Unicode in test literals, `-006` documented build commands), 14 FOLDED (001-008 and 011 into `-001`, 014 and 015 into `-002`, 018-020 into `-003`, 013 and themes 2 and 3 of 016
  into `2026-11-10-14-001`); each has a decision-log line. The two earlier plan-marshall lessons filed here on 2026-11-10 (`2026-11-10-09-002`, `2026-11-10-14-002`), and the other plan-marshall-owned ones this epic filed with the
  foreign-store override, were no longer in the lesson store on 2026-11-11 (`list` shows only `token-sheriff-*` components and `get` returns `not_found` here; the plan-marshall repository's lessons directory holds no file for
  `2026-11-10-09-002`, `-14-002`, `-09-001` or `2026-11-09-16-003`; nothing was searched beyond those two stores): the content of the first two was carried into `2026-11-11-12-003`. EXPLAINED by the operator 2026-11-11: the plan-marshall-owned lessons were consumed (by plan-marshall's own lesson handling), so
  their absence is expected and nothing is lost.

## Open Defects

> ↪ Relocated to `settled.md` § "Incomplete landings with an unstamped effort (PLAN-01 to PLAN-19)" — upstream-owned (plan-marshall stamps `effort`); one consolidated live entry remains; nothing further from this epic

- 2026-11-07 — Unstamped `effort` on fourteen landings (PLAN-01, 02, 04, 05, 09, 10, 12, 14, 15, 16, 17, 18, 19, 22; PLAN-03, 06, 07, 13,
  20, 21, 24 carried it; PLAN-08 and PLAN-11 were not checked): `landing-check` reports `complete: false`, missing key `effort`, on
  each. Plan-marshall owns the stamp; the per-landing entries are in `settled.md`. Retire when upstream stamps `effort` at plan init.
  PLAN-25 carried `effort` (`landing-check` complete: true), so it is not the fifteenth.
- 2026-10-31 — Open: the lesson-routing gap. The candidate-lesson inbox is now empty (all 36 dispositioned),
  but the 24 plan-marshall-owned lessons could only be filed here through the foreign-store override.
  `upstream-plan-marshall-report.md` holds the evidence for the plan-marshall maintainers. FILED 2026-11-05 at the
  operator's instruction as `quality-report-001.md` in the plan-marshall repo's `live-blockers` inbox (kind `finding`,
  the whole report to the PLAN-23 addendum; the target inbox's own validation was not run, since the `inbox validate`
  verb works on this repo's epic). Addenda written after it are not sent.
- 2026-11-09 — PLAN-25 inverted four mailbox decisions (`landings/PLAN-25.md`, layer 2, re-read on `aeb3fa49`); DECIDED by the operator the
  same day (see Decisions): (1) the raised JWE limit (32 KiB), the strict numeric `sub` and (3) the default-on client egress policy STAND as shipped;
  the explicit-null refusal in (2) and the test-size rule (4) are reverted by staged PLAN-27 (null read as absent again; `AGENTS.md:124-131` back to a
  500-line guideline). RETIRED 2026-11-10: PLAN-27 landed as #887 (`landings/PLAN-27.md`): `MapRepresentation` and `StrictStringMapper` are byte-identical to
  `2eceb09e` (re-run), `git grep` finds no `isExplicitNullRefused`/`explicitNullKeys` in tracked code or docs, the numeric-`sub` refusal and the strict-`sub` guard
  stay, and `AGENTS.md` § Test Class Size is the 500-line guideline. The doc figures that landed with it are a new Open Defect below. The 2026-11-06 JWE Open Defect (an unhandled `IllegalArgumentException` above 8 KiB) is gone: the 8 to 32 KiB case is typed and
  accepted when it is a JWE.
- 2026-11-09 — Defects in merged code, reported to the operator in the analysis turn (PLAN-25 layer 3; not fixed inline; a fix plan only on the
  operator's word): (1) the DPoP replay cache never evicts an in-window jti, so it grows past `maxSize` under a flood of fresh
  jtis (`DpopReplayProtection.java:99-101,122-144`, a documented choice; pre-existing since #565; the flood needs a valid DPoP-bound token;
  CORRECTED 2026-11-10 by a re-read at `aeb86e4d`: the growth is bounded by arrival rate x TTL window, not unbounded, and a periodic
  `evictExpired` sweep reclaims it);
  (2) the same flood makes every insert beyond `maxSize` scan the whole map while nothing is evictable, a CPU amplification (same
  lines, not named by the landing; CORRECTED 2026-11-10: `evictOldest` filters to expired entries BEFORE it sorts, so the cost is one full scan per insert, not a sort of the whole map);
  (3) `DpopProofValidator.java:351,441,457` log the proof's `jti`, `htm` and `htu` as raw arguments (CWE-117; whether anything downstream sanitises them is a HYPOTHESIS, re-read at those lines), and
  `LogRecordSanitizationGuardTest.java:58-64` does not name those fields, so the guard cannot see them. CARRIED 2026-11-10 by staged PLAN-28 deliverable 1. DECIDED 2026-11-10 (operator) for (1): the
  soft cap stays and is documented, not changed. Retire when it lands with tests.
  RETIRED 2026-11-11: PLAN-28 shipped (#892, `landings/PLAN-28.md`), re-read at `c7b2675b`: (1) the soft cap is kept and documented (`DpopConfig`, class Javadoc, threat-model DP1); (2) the over-cap insert pops from the head and ends at the first
  in-window entry, examining one node when nothing is evictable (`DpopReplayProtection.java:174-216`); (3) every remote-text sink in `DpopProofValidator`, the `jti`, `htm`, `htu`, thumbprint and each `rejectWith` text, is sanitised
  once for the log and the thrown message (`:343-362`, `:446-473`, `:630-636`). New minor residue, not a regression: a global monitor on every over-cap insert (see the PLAN-28 residue defect).
- 2026-11-10 — Wrong or contradicting documentation shipped by PLAN-27 (#887; `landings/PLAN-27.md` L2-1, L2-2, re-read on `aeb86e4d`), no fix plan staged, a doc-fix plan
  only on the operator's word: (1) `AGENTS.md:133` says 30 test classes are above 500 lines and the largest has 1024; at `aeb86e4d` the largest is 1023 and the count is 39 over
  all tracked `src/test` Java (34 `*Test.java`, 31 in validation and client). (2) `CLAUDE.md:26-34` says a reactor-wide `mvnw test-compile` or `mvnw test` "always" fails and that
  only `token-sheriff-client` and `benchmark-core` consume the `validation:generators` test jar, but `AGENTS.md:82,151` still tell agents to run `./mvnw test`, and five poms
  carry the `generators` classifier (the validation pom produces it; `token-sheriff-validation-quarkus`, `quarkus-integration-tests`, `token-sheriff-client` and `benchmark-core`
  consume it); the "always fails on `main`" claim is not shown by reading (a build the orchestrator does not run). (3) `security-reference.adoc:34` says "no later step can tell
  the two apart" for a null and an omitted member, which holds for top-level members only (a nested null is kept, as before; re-read at `MapRepresentation.fromJson`, lines 100-118). CARRIED 2026-11-10 by staged
  PLAN-28 deliverable 2. Retire when a doc fix lands or the operator accepts the residue.
  RETIRED 2026-11-11 by PLAN-28 (#892), re-read at `c7b2675b`: `AGENTS.md:172-173` is the 500-line guideline with no stale count; `security-reference.adoc:34` now limits the null-equals-omitted claim to top-level members; the build-command
  claims moved from `CLAUDE.md` into `AGENTS.md:82-130` and match the poms (the leaf's reading; whether a reactor `test-compile` fails stays a build the orchestrator does not run). The wording of the pre-clean stop is still broader than its
  mechanism (see the PLAN-28 residue defect).
- 2026-11-10 — PLAN-27 landing facts incomplete: `landing-check` `complete: false`, missing keys `unresolved_review_threads`, `review_body_findings_total`,
  `review_body_findings_handled` (all `unknown`; the lane says no finalize step publishes them); `surface_delta` `unmeasured`. The landing was reconciled as far as it goes and the
  CodeRabbit state was read from the PR directly (two inline findings, both fixed in `abef336e`; the last commit merged under the recorded `barrier-ask-override` ruling).
  Plan-marshall owns the facts; same class as the `effort` residue. Retire when upstream publishes them or the operator accepts the gap.
  RECURRED 2026-11-11 (PLAN-28 landing, #892): `landing-check` `complete: false`, missing keys `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (all `unknown`; `unresolved_review_threads` was present, 0);
  `surface_delta` `unmeasured`. The lane says no finalize step parses review bodies and `sonar-roundtrip` does not read security hotspots. The landing was reconciled as far as it goes; the check state was read from `ci` directly (24 green,
  `build / sonar-build` success, CodeRabbit success). Same class, upstream-owned.
- 2026-11-10 — Minor looseness and test gaps left by PLAN-26 (#888; `landings/PLAN-26.md` L2-3, L2-4, L3-1 to L3-6, re-read on `7520e165`), no blocker, none reported as a fix plan:
  `BearerChallengeParams.readValue` accepts an unquoted `scope=read write` in STRICT mode (`:207-210`) and `CLIENT-25` is silent on it; a Unicode-whitespace-only `scope` yields an empty
  scope set while the same character beside a token is refused (`InsufficientScopeChallengeParser.java:112`); the LENIENT `unquote` pollutes `acr_values="a" Basic realm="x"` (inherited) and
  its Javadoc misstates an escaped final quote; the statelessness test cannot catch static state; the OWASP and ModSecurity corpus tests are refused by the scheme check alone; point (f) of
  the contract test does not fail for a live view over the old loader's map; the second assertion of point (c) can never fail; points (g) and (h) are timing-bound (1 s refresh, 3 s grace);
  a raw U+2028 sits in a test literal; the shared `ErrorTextSanitizer` can split a surrogate pair and does not escape bidi controls (pre-existing). CARRIED 2026-11-10 by PLAN-28 deliverable 7
  (a) to (e). Retire when PLAN-28 ships and each point is fixed or dropped with the line that closes it.
  RETIRED 2026-11-11 by PLAN-28 (#892) except the points the residue defect names, re-read at `c7b2675b`: STRICT refuses any unquoted `scope` (`BearerChallengeParams.java:179-183`, operator decision); a Unicode-blank `scope` goes through the
  token grammar and is refused (`InsufficientScopeChallengeParser.java:121-157`); `ErrorTextSanitizer` cuts surrogate-safe and escapes the nine bidi controls (`:168-214`); the public constants stay public and are named; the `StepUpResultVerifier`
  split was read and cannot widen (`:105-128`); the point (c) positive control and the wider grace period are in the contract test (leaf). No raw U+2028, U+2029 or U+2003 is left in the three test files (re-counted, 0). Point (f) is closed by saying so, not by a test: a live view over the OLD loader's map is still not caught, and the
  test Javadoc now states that (`JwksLoaderKeyIdsContractTest.java:75-80`). Not closed: the LENIENT `unquote` Javadoc still says a value that never closes its quote is returned unchanged (L3-1); the
  OWASP and ModSecurity corpus rows stay largely vacuous for `realm` and `error_description` (L2-4).
- 2026-11-11 — Minor residue after PLAN-28 (#892; `landings/PLAN-28.md` L2-1 to L2-7 and L3-1 to L3-5, re-read at `c7b2675b` where marked), no blocker, none reported as a fix plan, a doc-and-test plan only on the operator's word:
  (1) the LENIENT `unquote` Javadoc still claims a value "that never closes its quote" is returned unchanged, but `"a\"` is stripped to `a` (`BearerChallengeParams.java:241-268`, re-read; spec item 7(c) named it);
  (2) `JweDecryptor.sanitizeHeaderValue` Javadoc (`:311-318`, re-read) lists control characters and U+2028/2029 only, the shared sanitiser also replaces the bidi controls (the `LogSanitizer` Javadoc likewise, per the PR);
  (3) `ErrorTextSanitizer` leaves U+200E, U+200F, U+061C, the zero-width characters U+200B to U+200D and U+2060 to U+2064, U+FEFF and the tag characters un-neutralised (re-read `:184-214`): a residual invisible-character spoof of a log line;
  (4) `AGENTS.md:95-98` and the integration-tests `README.adoc:64-68` say a failing compose command does not fail the build, but the pom accepts exit codes 0 and 1 only and the script runs under `set -e` (re-read); the PR names it; and
  `AGENTS.md` says it is "not the only place commands appear" while the module READMEs say it owns them (the single-owner goal is half met);
  (5) one global monitor `evictionLock` is taken by every over-cap `jti` insert, and the periodic sweep holds it for a whole-queue walk (the sweep not re-read): over-cap inserters stall once per 60 s in proportion to the queue,
  the magnitude only a run can show (`DpopReplayProtection.java:76,155-168,174-216`); the cap itself is the operator's soft cap;
  (6) test pins: the over-cap test asserts a test-only probe, not the cost; the concurrent replay test uses a 300 s TTL so no evict race runs; the embedded-payload attack-database test returns early for most corpus payloads and `isEscaped`
  re-lists the bidi ranges; no row for an empty unquoted `scope=`; the statelessness additions miss a static final mutable collection; D3(d) has four epk rows and no recorded reason that apu/apv, unsupported compression and an inner-JWS
  decode failure need none; D3(b) is closed by a Javadoc record of a scratch run; `JweDecryptorEcdhEsAndCompressionTest` is 516 lines (guideline 500, reason in the PR body);
  (7) the PR body is stale: its Non-goals say the thumbprint, replay, htm and htu messages are left out (the diff sanitises them), and it states 186 lines for a 197-line test. Retire when the operator accepts the residue or a plan fixes each point
  with the line that closes it.
- 2026-11-09 — PLAN-19 doc drift RETIRED by the PLAN-25 landing: COMMONS-2 now documents the `allowInsecureHttp` opt-in
  (`doc/commons/requirements.adoc:57-64`) and the Quarkus parent README attributes the mapping to `ClientConfigMapper`
  (`token-sheriff-quarkus-parent/README.adoc:14`), both re-read; the Javadoc and ADR-0010:187 corrections are leaf-reported and not re-read.

## Watches

- 2026-11-09 — API Sheriff downstream request (cross-repo message `api-sheriff-kidicap-gateway-requirements-001.md` from the orchestrator of
  `cuioss/API-Sheriff`, epic `kidicap-gateway-requirements`; a lead, read at `f85c5ab3`, not a change request against PLAN-25, nothing
  urgent except A). OPERATOR-OWNED, nothing staged: (A) before the 0.9.9 cut API Sheriff wants the version number, the date, whether
  PLAN-25 is in, and a release note of downstream-visible changes since 0.9.8: loader status contract (#830, `OK` only with a usable key
  set), opt-in max staleness and re-discovery (#844), a JWKS with no usable key never replaces an installed set, log records now carry
  sanitised text and no exception object (a downstream test asserting a stack trace can break), and, only if PLAN-25 ships, a non-string
  `sub` refused and a token above the 8 KiB parser limit failing with `TokenValidationException`; plus the Quarkus and `cui-quarkus-parent`
  versions the release is built on. (B) they depend on the `JwksLoader.getKeyIds()` contract (active set only, empty before init and never
  `null`, blank ids omitted, duplicates collapse) and ask for confirmation that it stays stable and that each point is pinned by a test;
  they found the rotation and duplicate cases pinned but did not confirm "retired excluded while `getKeyInfo` still resolves", "empty
  before init" and "blank omitted". (C) a question: should `token-sheriff-client` own a parser for the RFC 6750 `insufficient_scope`
  challenge (today `StepUpChallengeParser` handles only `insufficient_user_authentication`), or should API Sheriff parse it; they will
  parse it themselves unless told otherwise and need the answer before their PLAN-20 outline. (D) information: they will call
  `RevocationClient.revoke` on logout and ask to hear of signature or best-effort changes before 1.0. VERIFIED at `f85c5ab3`: the project
  version is `0.9.9-SNAPSHOT` with `0.9.8` the latest tag; `StepUpChallengeParser` has only the `insufficient_user_authentication` case;
  `RevocationClient.revoke(String, String, @Nullable String, ...)` exists; the empty-before-init case is asserted in
  `IssuerConfigDiscoveryIssuerTest` and the retired-key lookup through `getKeyInfo` in `HttpJwksLoaderGracePeriodTest`; no test found
  for blank ids omitted AT THE HTTP LOADER. CORRECTED 2026-11-09 (full read of the tests): `JWKSKeyLoaderTest` pins blank omitted (method at 268), duplicate once, unmodifiable and empty before init, and `HttpJwksLoaderKeyRotationTest` lines 176-190 pins retired excluded with `getKeyInfo` still resolving and an unchanged earlier snapshot, so the contract is pinned piecemeal; what is missing is one place that asserts it through the three factory entry points (no `getKeyIds` assertion in the file, in-memory or factory tests, none for blank or duplicate ids at `HttpJwksLoader`): staged as PLAN-26. NOT verified: the
  PLAN-25 branch state the message quotes (`816bf66e`), the 0.9.8 comparison, and the Quarkus versions. The reply channel is a write
  into another repository's epic inbox, so no reply is sent without the operator's word. OPERATOR ANSWERS 2026-11-09: (A) ignore; (B) spec a test: STAGED as PLAN-26 (`jwks-key-ids-contract-pin`); (C) the operator
  would see the `insufficient_scope` parser in TokenSheriff and asked for the orchestrator's proposal (proposal made, awaiting the
  operator's word before any spec is staged); (D) information only, no action; reply to API Sheriff: later, if released. UPDATE 2026-11-09
  (PLAN-25 shipped): the downstream-visible changes of the unreleased cut now also include a default-on client egress policy (private-range
  IdPs refused unless `allowed-egress-hosts` lists them), explicit JSON null identifier claims and non-string `sub` refused, a JWE of 8 to 32 KiB
  accepted (not an error), and a token-endpoint success with a non-JSON Content-Type treated as a redeemed-response failure; the first three
  were decided 2026-11-09 (the raised JWE limit, the strict non-string and numeric-`sub` refusals and the default-on egress policy stand; the
  explicit-null refusal was reverted by PLAN-27, shipped 2026-11-10 as #887, so it is in no release and needs no note), so any release note describes the other three as shipped and still waits on a release and the operator's word. UPDATE 2026-11-10 (PLAN-26
  shipped as #888, `7520e165`; `landings/PLAN-26.md`): (B) is pinned by `JwksLoaderKeyIdsContractTest` through the factory for points (a) to (h), and (C) is answered in code by
  `InsufficientScopeChallengeParser` (new public API, `CLIENT-25`), both on `main` and in no release. A release note would also state that `StepUpChallengeParser` now refuses a
  valueless segment, a following scheme, other control characters and a header over 8192 characters. The reply and the release note stay held until a release and the operator's word.
  UPDATE 2026-11-11 (PLAN-28 shipped as #892, `c7b2675b`; `landings/PLAN-28.md`): the release note for (C) would also say that the STRICT `insufficient_scope` parser now refuses ANY unquoted `scope` value (operator decision; a resource
  server that sends `scope=read` unquoted loses `insufficient_scope` handling, an availability effect, stated in `CLIENT-25`) and a Unicode-blank scope, and that the shared `ErrorTextSanitizer` now escapes the nine bidi controls and cuts
  at a surrogate boundary (log and exception text only). DPoP proof rejections now carry sanitised text in the thrown message too. All on `main` and in no release.
  Retire when the reply is done or declined.
- 2026-11-09 — PLAN-25 items with NO evidence in either PR (leaf reading, `landings/PLAN-25.md`; held, not carried by PLAN-26): 1(a) and 1(b)
  (the unannotated-override fail-open verdict; `BearerTokenInterceptor` untouched; no native run), the CI-runner container question, 4(b)'s
  scratch-run result, 7(l)(5) ADR-0026 soften-or-verify, 7(l)(6) the AsciiDoc link check over the 16 PLAN-19 files, 10(c) the health-check catch
  placement and `TokenValidatorStartupInitializerTest`, 10(d) the `JweDecryptorTest` gap rows (rows only moved), 10(h) the SVG render, and the
  e2e root cause. Also not shown: the `verify -Ppre-commit` whole-tree run and the native IT run. Beyond the spec and so not in the mailbox: the
  non-JSON Content-Type refusal at token, PAR and userinfo (a success with another type becomes `RedeemedResponseException`), `expires_in` must be
  a JSON number, and about 20 created or edited test classes over 500 lines. PR bodies were not read, so whether each narrowing was stated
  there is unknown. Retire an item when the operator accepts it or it is verified. CARRIED 2026-11-10 by staged PLAN-28: 1(a)/(b), the CI-runner question
  and the native `StrictStringMapper` run as deliverable 5; 4(b), 10(c) and 10(d) as deliverable 3; 7(l)(5), 7(l)(6), 10(h) and the beyond-spec narrowings as deliverable 4.
  RETIRED 2026-11-11 by PLAN-28 (#892, `landings/PLAN-28.md`), with the closure kind named per item, not as a blanket: shipped with code or text re-read at `c7b2675b` (ADR-0026 text and `AlternativeValidatorOrphanedPropertyTest`, the
  `step-up-and-logout.adoc` and `quarkus-config-doc.adoc` edits, the doc wording); closed by EVIDENCE ONLY with no repository change, the evidence being the lane's and not re-run here: 1(a)/(b) and the native `StrictStringMapper` run
  (the class-level endpoints already exist, `reflect-config.json` untouched), 4(b) (a Javadoc record of a scratch run, `PsychicSignatureAttackTest`), 10(c) (the health-check catch placement, files untouched; whether the OpenRewrite
  recipe honours it is not shown by reading), 10(d) (four epk rows; three cases with no recorded closure), 10(h) (the `rsvg-convert` render, a lane claim), 7(l)(6) (the 884-link check, a lane claim) and the CI-runner question. Retired on the
  operator's cleanup instruction; the lane-claim items are not verified by this analysis.
- 2026-11-09 — Informational from the PLAN-25 landing: the lane's Done-when assumed one PR and the split left no owner for "report both
  numbers in the PR body" of part two (spec premise, informational). The 2026-11-09 PLAN-25 landing message's `archive-plan:pending` step
  is the lane's own. Nothing further from this epic.
- 2026-11-07 — PLAN-19 landing residue (#879; landing message, partly verified): (1) docs-only PRs skip the Sonar build in this repository's
  change check, so the open-issue count at the head was undecidable and the operator had to accept the gap (read directly afterwards:
  `main` 0 unresolved). (2) CodeRabbit's one-review-per-hour allowance was spent on the first head, so the loop-back head `56710e6a` was
  merged with no fresh bot review and the participation check still read "participated" (upstream-owned, seventh addendum). (3) A
  finalize loop-back inline commit wrote no change-ledger row (candidate-lesson 009, upstream-owned). (4) The squash message may carry
  the PR body with CodeRabbit's summary (same cause as the PLAN-18 Watch). (5) No AsciiDoc link check was evidenced for 16 converted
  files (PLAN-25 7(l)(6)). Retire (1) to (4) once the operator accepts them; they need nothing further from this epic.
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
  nothing further. The CodeRabbit quota stall recurs (see the PLAN-13 and PLAN-14 residuals in `settled.md`).
- 2026-11-06 — e2e teardown hang (PLAN-24 landing, #876): the `e2e-tests` job was cancelled by its 30-minute job timeout
  after all 30 tests passed, and the landing says it recurs on `main`; a rerun cleared it (`SUCCESS` at this analysis). Unverified
  beyond the landing message and the PR's final check state; OWNED since 2026-11-07 by PLAN-25 deliverable 9. UPDATE 2026-11-09: PLAN-25
  shipped a hardened stop script (`stop-dev-environment.sh`: validated PID, whole tree TERM, 20 s grace, KILL, bounded `docker compose
  down`); the cause was never named and the criterion was waived to CI. The End-to-End workflow is green on both main pushes (`47045031`,
  `aeb3fa49`), which meets the "two clean runs" condition, but a green pair does not show the hang is gone. The #883 merge-queue run was also
  ejected once by a Playwright screenshot comparison flake (an accessibility spec; project-owned, no flake policy). RECURRED 2026-11-10 (PLAN-26 landing, #888): the `e2e-tests` job hung
  30 minutes on head `4c19ef2d` and was cancelled with an empty log AFTER the hardened stop script was on `main`; the approved re-run passed in 240 s (the hung run is the lane's claim,
  `ci checks status 888` shows only the passing re-run). The hardened script therefore did not cure it, and the "two clean runs" condition below is not met by a green pair. CARRIED 2026-11-10 by staged PLAN-28 deliverable 6. Retire it
  when a clean e2e run on `main` is observed twice or a cause is found. Seen once before as the PLAN-13 merge-queue ejection on a
  hung `e2e-tests` job (see the PLAN-13 and PLAN-14 residuals in `settled.md`).
  RECURRED AGAIN 2026-11-11 (PLAN-28 landing, #892), on the merge queue: the first entry was ejected after the End-to-End `merge_group` run 42990351708 hit the 30-minute job timeout (orphan java and playwright processes on the runner,
  per the lane; the run was not read here), while the identical tree passed the PR-level run in about 4 minutes each time and the re-enqueue (`merge_group` run 43012955220) landed in 363 s. PLAN-28 deliverable 6 (item 6) closed with
  evidence only and NO repository change: the lane records the cause as the reusable workflow in `cuioss-organization` and not fixable in this repository (a lane claim, HYPOTHESIS here; `stop-dev-environment.sh` is untouched and
  cannot name a hang that precedes teardown). Two data points now say it is intermittent, not deterministic: PR-level runs pass, merge-group runs hang. Still not retired: retire it when a clean merge-group run is observed twice or a cause is found.
> ↪ Relocated to `settled.md` § "Per-plan landing residuals (PLAN-01 to PLAN-23, dispositioned 2026-11-07)" — every item is carried by a PLAN-25 deliverable, dropped, or recorded as informational in its Residual Register

- > ↪ Relocated to `settled.md` § "Sonar read 2026-10-30 (PLAN-01 to PLAN-05 PRs)" — point-in-time read of closed PRs, superseded by the 2026-11-06 direct reads
- > ↪ Relocated to `settled.md` § "Sonar read 2026-10-30 (PLAN-06 and PLAN-07)" — point-in-time read of two merged PRs, superseded by the 2026-11-06 direct reads
- > ↪ Relocated to `settled.md` § "Sonar read 2026-11-01 (PLAN-12 and PLAN-20)" — point-in-time read of two merged PRs, superseded by the 2026-11-06 direct reads
- > ↪ Relocated to `settled.md` § "Lesson store 2026-11-02" — the missing lessons were DROPPED on the operator say-so 2026-11-05
- > ↪ Relocated to `settled.md` § "Sonar read 2026-11-02 (PLAN-21, 44 open on main)" — the 44 open issues were closed by PLAN-23 and PLAN-15; main reads 0 open on 2026-11-06
- > ↪ Relocated to `settled.md` § "Executor drift (resolved)" — resolved as of PLAN-21 (2026-11-02), the executor and plugin versions match
- The CodeRabbit one-review-per-hour quota is shared by concurrent plans: PLAN-06 waited roughly three hours
  behind PLAN-07's window and both merged under an override. Pairs raise the chance of this; recorded for
  the pairing decision on PLAN-08 onward.
- TC-9 (IT part) — integration tests use `useRelaxedHTTPSValidation()` and per-IT trust-all managers;
  a real truststore for the Keycloak container is IT-cost and not staged. Re-check if IT TLS
  infrastructure changes or a TLS-related IT regression appears.
- `source-report/` line numbers are stale; plan specs carry the current locations.
- 2026-11-09 — Operator answers given INSIDE a running plan never reach the orchestrator: PLAN-25's landing says the operator answered the four mailbox
  questions the opposite way in the run, but nothing in the inbox, the landing's record or the ledger carried those answers, so the orchestrator
  recorded an Open Defect and asked again, and the operator's second set of answers differed from the first on one point (null versus type). A plan
  that overrides a mailbox decision should say so in its landing message with the question and the answer verbatim. Upstream observation for the
  plan-marshall maintainers (addendum nine, operator-owned, NOT sent); no change here. Retire when sent or declined.
- 2026-11-10 — PLAN-27's null pin is relative only (`IdentifierClaimNullEquivalenceTest.java:150-185`, `landings/PLAN-27.md` L2-3): it proves null equals omitted but does not assert an omitted
  `jti` or `client_id` is ACCEPTED, and the removed PLAN-25 test "An absent optional identifier claim still validates" got no direct replacement; a keyword grep of
  `token-sheriff-validation/src/test` found no other such test, which is a lead, not a finding (another test may cover it). Also by reading only: `azp: null` plus a matching
  `client_id` now validates through the RFC 9068 fallback exactly like an omitted `azp` (`AuthorizedPartyValidator.java:80-122`, intended by the revert). Retire when a test is read or
  added that pins an omitted optional claim as accepted, or the operator accepts it. CARRIED 2026-11-10 by staged PLAN-28 deliverable 3(a).
  RETIRED 2026-11-11 by PLAN-28 (#892): `IdentifierClaimNullEquivalenceTest.java:166-180` now has an absolute pin, `shouldAcceptAccessTokenOmittingOptionalIdentifierClaim`, parameterised over `jti` and `client_id` (an omitted claim validates,
  `assertNull(outcome.eventType())`), re-read at `c7b2675b`. Whether the `validClaims()` fixture holds both claims was not read. The `azp: null` fallback note stays a reading, intended by the PLAN-27 revert.
- 2026-11-09 — PLAN-26 was re-grounded at `aeb3fa49` before its emit (see Decisions): eleven claims re-stamped corroborated, two spec statements corrected
  (`StepUpChallengeParser` already refuses CR, LF and NUL since PLAN-25, so deliverable 2(f) reuses `HeaderValues.containsLineBreakOrNul` and the
  sibling test is 13 tests, 266 lines), three hypotheses stay `unverifiable` for the plan's own outline. The re-grounding was done by the orchestrator
  with direct reads and greps, not by a leaf, and the spec text was edited with a Python replace instead of the Edit tool (process deviation).
  RETIRED 2026-11-10: PLAN-26 shipped (#888). Its landing analysis settled the three hypotheses (the second-challenge leak was a valueless segment, not `Basic realm=`; the
  step-up test count was 14 by annotation, not the 13 this re-grounding wrote; `performBackgroundRefresh` is package-private and unusable from the factory test) and found
  the re-grounding had not read the access modifier. The process deviation itself stands as a recorded note.
- 2026-11-10 — `StepUpChallengeParser` refuses more than it did (PLAN-26 layer 2, `landings/PLAN-26.md` L2-1): a valueless segment, any following scheme, a non-token `auth-param`
  name, other control characters and a header over 8192 characters now yield no challenge. Fail-closed and declared in the PR body, but a legitimate multi-scheme response such as
  `Bearer error="insufficient_user_authentication", acr_values="x", Basic realm="y"` no longer triggers step-up for a consumer, where it did before. A real consumer effect, not a
  defect. Watch whether API Sheriff or another consumer reports a step-up that stopped working; no change proposed. Retire when a consumer has confirmed it is unaffected or the operator
  accepts it. Stated for the release note (see the API Sheriff Watch).
