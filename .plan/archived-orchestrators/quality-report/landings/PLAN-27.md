# Landing Analysis: PLAN-27 — Correct two PLAN-25 outcomes (explicit JSON null read as absent again, 500-line test-size guideline)

epic: quality-report
workstream: WS-05
pr: #887 (merge commit `aeb86e4d0a8b5930b1eb426916e703d2481303dc`, merged 2026-11-10T08:56:38Z)

> Landing record for one shipped plan. Analyzed 2026-11-10 from the plan's inbox landing message
> (`plan-27-test-size-guideline-restore-017.md`), corroborated against git on `main` at `aeb86e4d`, the
> PR state and the merged code. The slug `test-size-guideline-restore` is historical: the plan also carries the
> null-handling revert, which is its main code change.

## Deliverable Fidelity vs Spec

The spec (`plans/PLAN-27-test-size-guideline-restore.md`) has three deliverables; the lane's own outline cut four
(`deliverables_total=4`, `deliverables_done=4`). The PR touched 18 files; the spec declared 13.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 (a) null equals an omitted member | shipped-as-specified | `MapRepresentation.java:100-118` drops a null member in `fromJson`; `TokenBuilder.java:145-162` loops over `keySet()` only; one parameterised pin `IdentifierClaimNullEquivalenceTest.java:170-185` over iss/sub/jti/client_id/azp x access/ID token compares the full outcome of null versus omitted |
| D1 (b) a body of only nulls is empty | shipped-as-specified | `TokenBuilderExplicitNullTest.java:84-92` (empty token, counter 0, access and ID) plus the `MapRepresentationTest` only-null case |
| D1 (c) present non-string stays refused, `sub` guard stays | shipped-as-specified | `StrictStringMapper.java:44-58` throws `MISSING_CLAIM` for a non-String; `TokenBuilder.java:58-59,150-154` keeps `STRICT_STRING_GUARD` for `sub`; `TokenBuilderExplicitNullTest.java:128-146` pins numeric `sub` under a custom mapper |
| D1 (d) null for a non-identifier claim reads as absent | shipped-as-specified | `TokenBuilderExplicitNullTest.java:104-126` (strict and stringifying mapper) |
| D1 (e) `MapRepresentation(Map data)` again | shipped-as-specified | `git diff 2eceb09e aeb86e4d` on `MapRepresentation.java` and `StrictStringMapper.java` is EMPTY (byte-identical, re-run); no `explicitNullKeys`, `isExplicitNull`, `ofParsed` left in any tracked file |
| D1 (f) `isExplicitNullRefused` and the null branch go | shipped-as-specified | `git grep` finds no `isExplicitNullRefused` / `refuseExplicitNulls` outside `.plan/` ledger prose |
| D1 (g) userinfo path unchanged | shipped-as-specified | `UserInfoResponse.java` is not in the diff (`git show --stat`); leaf read `:101-115` (not re-read) |
| D1 (h) `security-reference.adoc` and Javadocs | shipped-as-specified | `security-reference.adoc:34,47-49,64` re-read: null read as an omitted member, "last occurrence is JSON null is treated as absent"; the doc also claims "no later step can tell the two apart", which holds for top-level members only (a nested null is still kept, identical to `2eceb09e`; the PR says so, the doc does not) |
| D1 fail-closed statement + non-vacuity | shipped-modified | the PR body has the table; the mutation site the spec named (the `isExplicitNull` branch in `StrictStringMapper`) no longer exists once (e)/(f) remove it, so the lane used `MapRepresentation.fromJson` and records 10/10 pin rows failing (a PR claim, only a run shows it) |
| D2 `AGENTS.md` § Test Class Size as a 500-line guideline | shipped-modified | `AGENTS.md:124-131` re-read: guideline, not a gate, no 1000-line rule, no duty to split, split mechanics as advice. The `Current state` line (`AGENTS.md:133`) is wrong, see L2-1 |
| D3 reword the "400-line budget" Javadocs | shipped-as-specified (4 files, spec named 3) | `RefreshInFlightFailureTest.java:72`, `RefreshIdTokenValidationQuarantineTest.java:65`, `RefreshPostRedemptionQuarantineTest.java:82`, `RefreshTestSupport.java:49`; `git grep` for 400/1000-line wording outside `.plan/` is empty |
| not in spec: `CLAUDE.md` Maven reactor note | added-unplanned | `CLAUDE.md:26-34`, +9 lines; see L2-2 |

Operator decisions kept (spec, PLAN-25 landing Decisions): numeric `sub` and any present non-string identifier refused, the
JWE limit (32 KiB), the client egress policy and the userinfo converter are not in the diff; no test class was split.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, the `orchestrator.analyze` surface),
and the orchestrator re-read every point recorded as `checked` before writing it here (list below). The reading is **by reading,
no build, no test run, no write**: what only a run could show is tagged `not shown by reading`.

- **Dispatch:** one read-only leaf per landing — reason: reading the merged code (the null path, the mandatory-claim and issuer
  paths, the tests) is the depth the dispatch rule exists for, and it keeps this landing's read out of the lessons drain's context.
- **Verdict:** layer 1 findings (3); layer 2 findings (4, plus one not shown by reading); layer 3 clean by reading (runtime claims not
  shown by reading).

**Re-read by the orchestrator:** `MapRepresentation.java:100-118`; `StrictStringMapper.java:44-58`; `TokenBuilder.java:56-60,140-164`;
`AuthorizedPartyValidator.java:80-122`; `MandatoryClaimsValidator.java:80-120`; `AccessTokenValidationPipeline.java:206-220`;
`IdentifierClaimNullEquivalenceTest.java:120-186`; `TokenBuilderExplicitNullTest.java:80-146`; `security-reference.adoc:30-36,45-50,62-66`;
`AGENTS.md:80-84,122-135,149-153`; `CLAUDE.md:24-36`; the five `pom.xml` consumers of the `generators` classifier; the four client Javadoc
lines; `git diff 2eceb09e aeb86e4d` on the two restored files; the whole-tree line count (a read-only python count over tracked
`src/test` Java: 1023 for `NonValidatingJwtParserTest`, 1000 for `AccessTokenCacheTest`, 39 above 500 over all of them, 34 of them `*Test.java`,
31 in the validation and client modules). **Leaf-reported, not re-read:** `UserInfoResponse.java:101-115`, `IdTokenValidationPipeline.java:126-129`,
`ClaimName.java` edits and Javadoc, `doc/developer/test-impl-mapping.adoc:113-121`, `SubjectClaimTypeRejectionTest` imports, the nested-null path.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-0 | The spec's premises match `main` at `aeb86e4d^` (two-component `MapRepresentation`, the null loop, the userinfo converter, no tag containing `47045031`) | checked, confirms | leaf; the tag check `git tag --contains aeb86e4d` is empty (re-run) |
| L1-1 | The non-vacuity recipe is inconsistent with the spec's own criteria: it names the `isExplicitNull` branch in `StrictStringMapper`, which criteria (e)/(f) delete | checked, refutes (minor) | spec deliverable 1 "Non-vacuity" versus `StrictStringMapper.java:44-58` |
| L1-2 | The Expected Surface omits files that had to change: `IdentifierClaimNullRejectionTest` (renamed to `IdentifierClaimNullEquivalenceTest`), `SubjectClaimTypeRejectionTest`, `doc/developer/test-impl-mapping.adoc`, `RefreshPostRedemptionQuarantineTest` (a fourth "400-line" Javadoc; the spec's sweep clause covers it), and its own list of PLAN-25 tests to report omits the renamed class | checked, refutes (minor) | `git show --name-status aeb86e4d` versus the spec's `## Expected Surface` |
| L1-3 | The hypothesis that each of the five claims has a refusal path is false for `jti` (optional, nothing refuses it, `security-reference.adoc:50`) and conditional for `azp`/`client_id` (`AuthorizedPartyValidator` only when `expectedClientId` is set); the fail-closed wording "names the check per claim" presumed one | checked, refutes (minor) | `AuthorizedPartyValidator.java:80-122` |

**Layer 1 verdict:** findings — 3 (all minor). The spec's behaviour intent was sound and consistent with `main`.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 null as absent | `MapRepresentation.java:100-118`, `TokenBuilder.java:145-162` | `IdentifierClaimNullEquivalenceTest.java:170-185` compares the full outcome (event type, message, accepted claim names, `MISSING_CLAIM` delta) of null versus omitted for 10 rows; it fails if the refusal returns | none beyond L2-2 | checked |
| D1 non-string refusal | `StrictStringMapper.java:44-58`, `TokenBuilder.java:150-154` | `TokenBuilderExplicitNullTest.java:128-146` | none | checked |
| D2 test-size guideline | `AGENTS.md:124-131` | n/a (prose) | `AGENTS.md:133` figures wrong (L2-1) | checked (defect) |
| D3 Javadocs | four client test files | n/a (comment-only) | the spec named three, four changed (L1-2) | checked |

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L2-1 | The `Current state` line says 30 test classes are above 500 lines and the largest has 1024. At `aeb86e4d` the largest is 1023 lines and the count is 39 over all tracked `src/test` Java (34 `*Test.java`, 31 in validation and client), never 30. The PR body admits the 1023/1024 gap and the shipped text was not corrected; the spec (2(e)) asked for a measured, not copied, figure | checked, refutes (minor) | `AGENTS.md:133`; line count re-run |
| L2-2 | `CLAUDE.md` gained a Maven reactor note outside the spec that contradicts `AGENTS.md`, which still tells agents to run `./mvnw test` (`AGENTS.md:82,151`); `CLAUDE.md` is meant to defer to `AGENTS.md` for build commands. The note also names two consumers of the `validation:generators` test jar (`token-sheriff-client`, `benchmark-core`), but five poms consume it (adding `token-sheriff-validation-quarkus` and `quarkus-integration-tests`; `token-sheriff-validation/pom.xml` produces it). The claim that a reactor `test-compile`/`test` "always fails ... on `main` as well" is not shown by reading | checked, refutes (minor) | `CLAUDE.md:26-34`, `AGENTS.md:82,151`, the `pom.xml` grep |
| L2-3 | The pin is relative only (null equals omitted). It does not assert that an omitted `jti`/`client_id` is ACCEPTED, and the removed PLAN-25 test "An absent optional identifier claim still validates" pinned behaviour that did not go away and got no direct replacement. A regression that made `jti` mandatory would leave null and omitted equal and the pin green. The only absolute check is the all-claims token (`:150-163`). The orchestrator's grep of `token-sheriff-validation/src/test` for such a test found none by keyword; another test may cover it | not shown by reading | `IdentifierClaimNullEquivalenceTest.java:150-185` |
| L2-4 | The PR narrative lists `azp` and `client_id` as optional claims with "deliberate widening". `azp`/`client_id` are refused when absent if `expectedClientId` is configured (`AuthorizedPartyValidator.java:80-122`, "Missing required authorized party claim (azp or client_id)"), which is the check that refuses a null `azp` exactly like an omitted one; the check per claim is misattributed, and "widening" is relative to PLAN-25, not to `2eceb09e` or to an omitted member | checked, refutes (minor) | `AuthorizedPartyValidator.java:80-122` |

**Layer 2 verdict:** findings — 4 (minor), plus 1 not shown by reading (L2-3).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| none | — | — | — | — |

A null top-level member is dropped in `fromJson` before any consumer, so `extractClaims` iterates `keySet()` only and a null key reaches neither a
mapper (built-in or custom) nor `STRICT_STRING_GUARD`. `iss` and `sub`: refused when absent (issuer extraction, `AccessTokenValidationPipeline.java:208-218`;
`MandatoryClaimsValidator.isClaimMissing` is `!containsKey`, skipped for `sub` only under `claimSubOptional`, the same for an omitted member). `jti`:
nothing refuses it either way. `azp`/`client_id`: the same `AuthorizedPartyValidator` path as omitted. A consequence worth knowing, not a defect: an `azp: null`
together with a matching `client_id` now validates through the RFC 9068 fallback exactly like an omitted `azp`. No fail-open was found.

**Layer 3 not shown by reading:** the pin tests passing, the mutation failing 10/10, the 4149-test and `-Ppre-commit` results, that DSL-JSON keeps a null inside a
`Map` on every payload shape (the `fromJson` filter relies on it; `MapRepresentationTest.shouldAnswerAccessorsForParsedExplicitNullLikeAbsent` is the guard), the `CLAUDE.md` reactor-failure claim, and the Sonar state.

**Layer 3 verdict:** clean by reading — 0 defects; runtime claims not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` § Test Class Size | a new or grown class stays at or under 500 lines | conforms | `IdentifierClaimNullEquivalenceTest` is 186 lines; the three edited test classes shrank |
| `AGENTS.md` § Testing | JUnit 5, AAA, `@DisplayName`, parameterised for 3+ variants | conforms | `IdentifierClaimNullEquivalenceTest.java:170-185` |
| `AGENTS.md` | pre-1.0: remove unused API directly, no deprecation | conforms | the null API was deleted; no tag contains the adding commit |
| `AGENTS.md` | Javadoc accuracy, no dangling `{@link}` | conforms | `git grep` clean |
| `AGENTS.md` | logging: no new log messages | conforms | no logging code in the diff |
| `AGENTS.md` / `CLAUDE.md` | consistent build guidance, `AGENTS.md` owns commands | deviates | L2-2 |
| `AGENTS.md` | documentation accuracy of the test-class statistics | deviates | L2-1 |

## Metrics and Anomalies

- Tokens: 12,922,347 (`total_tokens`, as the landing message states them); the per-phase split is not in the message.
- Duration: 42,276 s of wall time (about 11 h 45 min), most of it waiting on review bots and CI.
- Finalize steps, all `done`: sync-baseline, functional-review, code-review, architecture-refresh, pre-push-quality-gate, push, create-pr, ci-verify, automatic-review, sonar-roundtrip, branch-cleanup, lessons-capture, preference-emitter, record-metrics, print-phase-breakdown (last-colon split applied).
- Anomalies: (1) functional-review and code-review each raised the same five blocking `weakened_test` findings for the spec-mandated test removals at two heads, and the operator had to close each (inbox 001-012; recorded in lesson `2026-11-09-16-003`). (2) Two verifier refusals looped the review steps back once each (message 006, 012). (3) A finalize-time source commit was refused for lack of a sanction (message 015, new lesson `2026-11-10-09-001`) and the run took loop-back iteration 1/5. (4) The `landing-check` is `complete: false` (below). (5) The post-merge CI wait timed out on the running post-merge benchmark job (the known `checks wait --adaptive` blocking on the JMH workflow, lesson `2026-11-09-16-013`).

## Routing and Merge Behavior

- Review: CodeRabbit raised two inline findings, both fixed in `abef336e` (`CLAUDE.md:31` claimed `verify -Ppre-commit` runs all tests; `security-reference.adoc:34` overstated the refusal for refresh tokens and named the wrong refusal point). The last commit `abef336e` merged under a recorded ruling (`barrier-ask-override`, review-barrier-gap): CodeRabbit (rate-limited) and Sourcery (quota) had not reviewed it. CodeRabbit had reviewed `757856a`; `abef336e` changes only `CLAUDE.md` and one `security-reference.adoc` paragraph. The check shows `CodeRabbit` `NEUTRAL` and `Sourcery review` `SKIPPED`.
- CI/merge: every PR check green (`build (21)`, `build (25)`, `integration-tests`, `e2e-tests`, `dependency-review`, `Run JMH Benchmarks`), merged through the merge queue as a single squash commit `aeb86e4d`; no rebase conflict. No release tag contains it.
- Sonar: the lane reports `sonar_open_new_issues=0`; this analysis read `build / sonar-build` `SUCCESS` on the PR and did not read the Sonar count itself. No contradiction seen.
- Landing completeness: `landing-check` `complete: false`; `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` are `unknown` (the lane says no finalize step publishes them). The landing is reconciled as far as it goes; recorded as an Open Defect. `surface_delta` is `unmeasured` (declared and realized surfaces not supplied).
- Parallelization: PLAN-27 ran beside PLAN-26 on disjoint surfaces, as gated; no collision observed. The declared surface under-stated the realized one by five files (L1-2), all inside PLAN-27's own area.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-27 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-27 --field pr --value 887`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-27 --field landing --value landings/PLAN-27.md`
- [x] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-27 --field plan_marshall_plan_id --value plan-27-test-size-guideline-restore`
- [x] epic.md narrative reconciled: the inverted-decisions Open Defect retired, the queue annotation and the API Sheriff Watch updated, new Open Defect for the doc inaccuracies, new Watches for the unverified leads and the incomplete landing facts
- [x] inbox: 16 candidate lessons dispositioned (2 promoted, 13 folded into three existing lessons, 1 discarded) and the landing message archived
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- Doc fix for `AGENTS.md:133` (figures), the `CLAUDE.md` reactor note (contradiction with `AGENTS.md:82,151`, incomplete consumer list, unverified claim) and the `security-reference.adoc` nested-null nuance: Open Defect, no plan staged; becomes a small doc-fix plan only on the operator's word. It can fold into any later doc plan.
- Whether an omitted `jti`/`client_id` is still pinned as accepted (L2-3): Watch, unverified lead; closed by a read of the validator tests or one added assertion in a later plan.
- Test removals declared by a spec and the weakened_test gate: carried as a recurrence in lesson `2026-11-09-16-003`, upstream-owned.
- The DPoP defects of the PLAN-25 landing stay open and unstaged; nothing in this landing changes them.
