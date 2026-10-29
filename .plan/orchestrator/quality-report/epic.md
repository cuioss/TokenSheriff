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
- PLAN-05 → PLAN-09 — `NonValidatingJwtParser.java` shared.
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
- 2026-10-28 — Open design choices are delegated to the plan's outline phase with a stated
  recommendation (V-1 fix-vs-drop ECDH-ES, V-17f empty-aud semantics, C-7 enforce single-use,
  C-8c PAR wire-vs-document, Q-4 catch-vs-readiness, Q-8 reactive-vs-document); each PR records it.

## Open Defects

- 2026-10-29 — Incomplete landing `plan-01-jwks-loader-lifecycle-009.md` (PLAN-01): `landing-check`
  reports `complete: false`, missing key `effort` (written as `unknown` by the producer). Drained and
  reconciled as far as it goes.
- 2026-10-29 — Incomplete landing `plan-04-quarkus-startup-health-009.md` (PLAN-04): missing key `effort`
  (never stamped, written as `unknown`). Drained and reconciled as far as it goes. Third producer in a
  row with an unstamped `effort`; PLAN-03 was the exception.
- 2026-10-29 — Incomplete landing `implement-plan-02-refresh-family-rotation-006.md` (PLAN-02): missing
  key `effort`; the plan's `refresh-runtime-stamp` could not refresh (`model_unknown`). The message also
  ends before `emit-landing` and `archive-plan` recorded an outcome.
- 2026-10-29 — 26 `candidate-lesson` messages from PLAN-01 (001–008), PLAN-02 (001–005), PLAN-03
  (001–005) and PLAN-04 (001–008) (folded in on recurrence) are queued and NOT yet dispositioned. Most name `plan-marshall:*`
  components, which this repository's lesson store does not own, so promoting them here is a routing
  decision for the operator. Project-relevant ones: PLAN-02-001, PLAN-03-001 (wall-clock boundary tests
  need wide margins plus a cache-hit assertion) and PLAN-03-002 (Sonar S5778/S2093 in new tests, which
  the sender itself flags as possibly already covered); PLAN-04-001 (native ITs run against stale
  `~/.m2` extension jars), -002 (pre-commit formatter flips star/explicit imports), -005 (documented
  startup/liveness guarantees broader than enforced) and -006 (new tests asserting weaker properties
  than intended). They stay in `inbox/` un-archived until dispositioned.

## Watches

- PLAN-02 Sonar: the landing reports `new_code_issue_count=1` (`count_status=confirmed`) with 0 filed
  for triage and no dismissal; the spec requires 0 open or every issue dismissed at the merge head.
  Unverified lead — check the open Sonar issue on #832's head before treating PLAN-02 as clean.
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
  required bot. Sonar: the landing carries no open-issue figure (unknown, not 0); `java:S5778` was
  fixed and `java:S2093` suppressed in code, not dismissed through Sonar. The review quota will recur
  on every plan — a process cost to weigh when pairing plans.
- PLAN-04 residuals (landing message + PR #834 body, not independently verified): merged under a
  `barrier-ask-override` (`review-barrier-gap`, head `0053b11c`) with functional and code review closed by
  operator override at that head; accepted functional-review coverage gap D5.C3; four advisory notes
  (HealthCheck catch-suppression placement, README wording for apps with their own `TokenValidator`,
  `CLIENT-21` vs `CLIENT-20` Javadoc, initializer test does not pin the proxy dereference); Sonar
  open-issue count unknown; the "mappers inactive at runtime" hypothesis was refuted as a cause but
  never observed failing without the registration.
- PLAN-01 residuals (landing message, not independently verified): three test classes over the
  400-line budget; a retained suppressed catch-all in `TokenValidatorProducer.destroy()`; an
  `initialize()` unexpected-exception path that leaves `LOADING`; a shared `HttpJwksLoaderConfig`
  owned-executor caveat for V-5. ADRs 0016–0018 carry provisional numbers.

- TC-9 (IT part) — integration tests use `useRelaxedHTTPSValidation()` and per-IT trust-all managers;
  a real truststore for the Keycloak container is IT-cost and not staged. Re-check if IT TLS
  infrastructure changes or a TLS-related IT regression appears.
- `source-report/` line numbers are stale; plan specs carry the current locations.
