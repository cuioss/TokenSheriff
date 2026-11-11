# Landing Analysis: PLAN-28 — the epic cleanup sweep

epic: quality-report
workstream: WS-05
pr: #892 (merged, queue-merged as `c7b2675b` on main, 2026-11-11; recreated from #891, closed and not merged)

> Landing record for one shipped plan. Lives at `landings/PLAN-28.md`. Written by the `analyze` verb (inbox scan) after
> verifying claims against ground truth (the merge commit, the PR through the `ci` abstraction, the merged code) — the landing
> message is a lead, never a fact. Sender plan id `plan-28-epic-cleanup-sweep`; messages 001-021 of that sender (20 candidate lessons and the landing).

## Deliverable Fidelity vs Spec

Corroborated against git and `ci`: `ci pr view 892` reports `merged`, merge commit `c7b2675b47b5e530aa9977341971d694fbe38e34`, single parent
`e61ceb42` (the ledger land #890); `ci pr view 891` reports `closed`, no merge commit (the PR was recreated so the required reviewer starts a fresh review);
the diff `e61ceb42..c7b2675b` is 35 files, +1263/-184 (re-run); `ci checks status 892` reports 24 checks, `overall_status: success`
(`e2e-tests / test` 235 s, `build / sonar-build` 288 s, `CodeRabbit` success, `Run JMH Benchmarks` success). The landing message's `landing-check` is `complete: false`
(`review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` are `unknown`; `surface_delta` `unmeasured`).

The lane cut the spec's seven items into nine deliverables in one PR (its own cut: A=D1 DPoP, B=D2+D5 docs and verifications, C=D3+D4 test pins and health-check pins,
D=D6 native-image evidence, E=D7 e2e, F=D8+D9 PLAN-26 parser and sanitiser leftovers). The mapping below is by reading the PR body and the diff.

| Deliverable (spec item) | Verdict | Evidence |
|-------------------------|---------|----------|
| D1 / item 1: DPoP replay cache and proof logging | shipped-as-specified, with the operator's two decisions | `DpopReplayProtection.java:174-216` over-cap path pops from the head and ends at the first in-window entry (no whole-map scan); soft cap kept and documented (`DpopConfig`, class Javadoc, threat-model DP1); every remote-text sink in `DpopProofValidator` sanitised once (`:343-362`, `:446-473`, `rejectWith` `:630-636`), the thumbprint, replay, htm and htu messages included, which the PR body's Non-goals line says were left out (L2-1) |
| D2 / item 2: the documentation PLAN-27 shipped wrong | shipped | `AGENTS.md` `§ Test Class Size` now states the 500-line guideline with no stale count (`:172-173`); `security-reference.adoc` now limits the null-equals-omitted claim to top-level members (`:34`); the build-command claims match the poms (leaf); the wording of the pre-clean stop is broader than its mechanism (L3-4) |
| D3 / item 3: test pins and gaps | shipped; D3(b) closed by a recorded scratch run only | (a) absolute pin `IdentifierClaimNullEquivalenceTest.java:166-180` (an omitted `jti` or `client_id` is accepted); (b) a Javadoc sentence in `PsychicSignatureAttackTest`, no code change, `SignatureVerificationUtil` untouched; (d) four epk rows only (L2-5) |
| D4 / item 4: verify-or-soften and record | evidence-only closure | ADR-0026 text and the new `AlternativeValidatorOrphanedPropertyTest`; the health checks were NOT changed, and `JwksEndpointHealthCheck` carries no directive at all (the lane's inbox 003) |
| D5: SVG render and link check | lane claim | `client-auth-code-pkce.svg` changed; the `rsvg-convert` render and the 884-link check cannot be checked by reading (inbox 009) |
| D6 / item 5: native-image `@BearerAuth` coverage | evidence-only closure, no repository change | the class-level endpoints already exist on the base (`ClassLevelUnregisteredAuthEndpoint.java:36-38,52`: no `@RegisterForReflection`); `reflect-config.json` untouched; the native run is a lane claim |
| D7 / item 6: e2e teardown hang and screenshot flake | evidence-only closure, owner recorded | no repository change; the reusable workflow is in `cuioss-organization` (lane claim); the hang recurred on this PR's first merge-queue entry (Metrics) |
| D8, D9 / item 7 (a) to (f): PLAN-26 leftovers | shipped | `BearerChallengeParams.java:179-183` STRICT refuses any unquoted `scope` (operator decision); `InsufficientScopeChallengeParser.java:121-157` `isAsciiSpacesOnly` replaces `isBlank`; `ErrorTextSanitizer.java:171-214` surrogate-safe cut and the nine bidi controls; the public constants kept and named; the SpecIT referenced at `test-strategy.adoc:470` and `test-impl-mapping.adoc:390`; the `unquote` Javadoc was not corrected (L3-1) |

**Surface delta (re-computed from the declared Expected Surface and the diff).** 39 entries declared, 35 files merged. 25 merged files are covered by a declared entry. 10 merged
files are NOT declared: `benchmark-integration-wrk/README.adoc`, `ADR-0022`, `step-up-and-logout.adoc`, `LogSanitizerTest.java`, `token-sheriff-quarkus-parent/README.adoc`, the integration-tests
`README.adoc`, `JwtPropertyKeys.java`, `quarkus-config-doc.adoc`, `AlternativeValidatorOrphanedPropertyTest.java` and `InboundErrorNormalizerTest.java`. Seven of them were added by the lane's own
Q-Gate rounds (candidate lessons 002, 004, 005, 008): the spec's surface was understated by the very "search for every surface" gap those lessons describe. 14 declared files were NOT touched, which
is the evidence-only closures (D3(b), D4, D6, D7). The spec is shipped and no longer gated, so its Expected Surface is not corrected. No collision resulted: nothing ran beside PLAN-28.

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, the `orchestrator.analyze` surface, resolved `level-3`) read the merged code, the spec, the PR body and the diff by reading only, at `c7b2675b` through
`git show`. The orchestrator then re-read at the commit the points marked "re-read" below, and re-counted the figures. A point not re-read is stated as the leaf's. Nothing was built or run, so every runtime
claim stays a lane claim.

- **Dispatch:** one read-only leaf per landing — reason: a 35-file diff, nine deliverables and two security-relevant classes is the depth the dispatch rule exists for, and this is the only landing in this drain.
- **Verdict:** layer 1 findings (4: 2 minor, 2 info); layer 2 findings (7 minor, 1 info); layer 3 findings (5 minor, 2 info). No blocker, no major. No layer-3 defect above minor.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 (minor) | Three spec premises were stale on `main`: item 5 asks for a NEW class-level endpoint without `@RegisterForReflection`, but one exists (so the lane's no-change closure is sound); item 6 speaks of a "screenshot comparison" while the specs only call `takeStartScreenshot` (leaf; not re-read); the `InsufficientScopeChallengeSpecIT` (item 7(f)) was "named nowhere", but `test-strategy.adoc:470` and `test-impl-mapping.adoc:390` name it | checked (two re-read) | spec items 5, 6, 7(f); `ClassLevelUnregisteredAuthEndpoint.java:36-38,52`; the two doc lines |
| L1-2 (minor) | The operator's decision "STRICT refuses ANY unquoted scope" is fail-closed and consistent with this repository's own resource-server side (it quotes `scope`: `BearerTokenResponseFactory.java:217-218`, leaf, not re-read). Its justification rests on RFC 6750 §3, which neither the leaf nor this analysis could read, and `scope=read` is a valid RFC 9110 token: a resource server that sends it unquoted loses `insufficient_scope` handling. An availability cost, stated in `CLIENT-25`, the threat model and `step-up-and-logout.adoc` | not shown by reading (the RFC text) | `BearerChallengeParams.java:179-183` (re-read) |
| L1-3 (info) | The spec's seven items, above the ~6 guard, were kept as one plan on the operator's word; the lane cut them into nine deliverables in one PR and the PR body records it | checked | spec header; PR body |
| L1-4 (info) | The D1 premises and the keep-and-document soft cap are consistent with `main` (the `DpopConfig` defaults 10 000 and 360 s, the old filter-then-sort shape, the DP1 "300 s" text); the peer sinks are sanitised too; the constants `INSUFFICIENT_SCOPE` and `MAX_SCOPE_TOKENS` stay public and are named in `CLIENT-25` | checked (leaf) | spec items 1, 7(f) |

**Layer 1 verdict:** findings — 2 minor (L1-1, and L1-2 not shown by reading), 2 info. The spec is a shipped one, so no verdict is persisted by `corpus set-verdict`.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 (b) over-cap cost | `DpopReplayProtection.java:174-216` | `DpopReplayProtectionTest.java:201-212` asserts a package-private probe `lastOverCapExamined == 1` | the probe does not exist on the unfixed code, so "fails before" can only be a compile failure; the test pins the shape, not the cost | checked |
| D1 (c) log and message sanitising | `DpopProofValidator.java:343-362,446-473,630-636` | `DpopProofValidatorLogSanitizationTest` (197 lines, re-counted; the PR body says 186): seven tests, each asserts the escaped form and the absent raw form in the WARN and in the thrown message | none | checked |
| D1 race freedom | `evictionLock` | `DpopReplayProtectionTest.java:215-246` runs with TTL 300 s, so no entry expires: insert contention only | evict-versus-insert and evict-versus-sweep are argued by reading, not tested | not shown by reading |
| D3 (d) JWE gaps | `JweDecryptor` | four epk rows with a vacuity guard | no recorded reason why apu/apv, unsupported compression and an inner-JWS decode failure need no row; apu/apv and compression are pinned elsewhere, no inner-JWS decode-failure row was found; the class is now 516 lines (re-counted; guideline 500) | checked |
| D8, D9 parser and sanitiser | `BearerChallengeParams`, `InsufficientScopeChallengeParser`, `ErrorTextSanitizer` | `shouldRefuseUnquotedScope` (one and several tokens plus a quoted control), nine bidi cases, surrogate cut | no row for an empty unquoted `scope=` (STRICT now refuses it); the statelessness additions catch a static non-final field but not a static final mutable collection; `assertEmbeddedPayloadContained` returns early on refusal, so for most corpus payloads the realm and description branch is never reached | checked |
| D3 (b) ECDSA range | `SignatureVerificationUtil` untouched | a Javadoc sentence records a scratch run with the check disabled | no code, and a transient run result in a class comment will rot | not shown by reading |

- **L2-1 (minor).** The PR body is stale or silent in four places: its Non-goals line says the thumbprint, replay, htm and htu exception messages are deliberately left out, but the diff sanitises all four (re-read); `DpopProofValidatorLogSanitizationTest` is 197 lines, not 186 (re-counted); the title is "close epic defects and watches carried from PLAN-27"; the decisions on D3(d), on item 7(c) and on the SpecIT are not recorded.
- **L2-2 (minor).** D1(b) is pinned by a test-only probe and asserts shape, not cost (table).
- **L2-3 (minor).** The concurrent replay test cannot exercise the evict-versus-insert race (table).
- **L2-4 (minor).** The attack-database embedded-payload test is largely vacuous for `realm` and `error_description`, and its `isEscaped` helper re-lists the bidi ranges instead of reaching them through production.
- **L2-5 (minor).** D3(d) closed with four rows and no recorded closure for three cases; `JweDecryptorEcdhEsAndCompressionTest` is 516 lines.
- **L2-6 (minor).** D3(b) is closed by a Javadoc record of a run, not by a repository change.
- **L2-7 (minor).** `AGENTS.md` now says it is "not the only place commands appear" while module READMEs say "Build commands are owned by AGENTS.md": the spec's single-owner goal is only partly met (the PR lists it as a follow-up).
- **L2-8 (info).** Every one of the 35 merged files is explained by a deliverable (leaf); the ten undeclared ones are the Q-Gate additions listed in the surface delta. The evidence-only closures (D3(b), D4, D6, D7) are allowed by the spec ("record the evidence and the owner").

**Layer 2 verdict:** findings — 7 minor, plus info.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `BearerChallengeParams.java:241-268` | LENIENT mode, the value `"a\"` | the `unquote` Javadoc still says a value "that never closes its quote" is returned unchanged; the value starts and ends with `"`, so it is stripped to `a`. Spec item 7(c) named this exact case | minor |
| L3-2 | `JweDecryptor.java:311-318` | none (a stale comment) | the Javadoc of `sanitizeHeaderValue` lists control characters and U+2028/2029 only, while the shared sanitiser now also replaces the bidi controls; behaviour and `token-sheriff-validation` docs are right. The PR lists the `LogSanitizer` Javadoc as the same follow-up | minor |
| L3-3 | `ErrorTextSanitizer.java:184-214` | a log or exception text carrying U+200E, U+200F, U+061C, U+200B to U+200D, U+2060 to U+2064, U+FEFF or a tag character (U+E0001, U+E0020 to U+E007F) | not neutralised: the nine bidi controls U+202A to U+202E and U+2066 to U+2069 are (the CVE-2021-42574 set), the marks above reorder only weakly or are invisible; the Javadoc names exactly the nine, so it does not overclaim. A residual homoglyph or invisible-character spoof of a log line remains possible | minor |
| L3-4 | `integration-tests/pom.xml:210-214`, `stop-integration-container.sh:3,36`, `AGENTS.md:95-98`, integration-tests `README.adoc:64-68` | `compose down` failing with an exit code other than 1 | the pom accepts only 0 and 1 and the script runs under `set -e`, so the docs' "a failing compose command ... does not fail the build" is broader than the mechanism. The other half is right: the script exits 0 without stopping anything when Docker Compose or the daemon is absent (`:20-27`). The PR body names it as a known follow-up | minor |
| L3-5 | `DpopReplayProtection.java:76,155-168,174-216` | a flood of valid in-window proofs above `maxSize` | one global monitor `evictionLock` is taken by every over-cap insert even when nothing is evictable, and the periodic sweep holds it for a walk of the whole queue (leaf; the sweep at `:155-168` not re-read), so over-cap inserters stall once per 60 s in proportion to the queue length. The structure is certain, the magnitude only a run can show. The PR does not mention it | minor |
| L3-6 | `DpopReplayProtection.java:64-74,108-133,175,215` | a client holding a DPoP-bound token and its key mints unique `jti`s | growth is rate x 360 s with no hard bound, plus a queue node per entry on top of the map entry. Operator-decided, and documented accurately; per-client rate limiting is outside this class. `System.currentTimeMillis` (re-read) means a forward clock step expires in-window jtis early: pre-existing, not touched | info |
| L3-7 | `DpopProofValidator.java:585-590`, `ErrorTextSanitizer.java:168-178` | a very long remote `alg` | `rejectWith` truncates the whole message at 256 characters, which can cut off the trailing "which requires algorithm" tail of the curve-mismatch message. Cosmetic: the event type and the sanitising are unaffected | info |

What is clean by reading (re-read at `c7b2675b` unless noted):

- **Every remote-text sink in `DpopProofValidator` passes `ErrorTextSanitizer.sanitize` before both the WARN and the thrown message:** thumbprint (`:343-351`), replay (`:357-361`), htm and htu (`:446-473`), and every `rejectWith` rejection once (`:630-636`), with no double escape (the DSL-JSON decode site no longer sanitises a second time; leaf). The only unsanitised log argument left is `computedThumbprint`, a locally computed digest.
- **The replay cache never forgets an in-window `jti` on the over-cap path** (`DpopReplayProtection.java:174-216`: it pops only a stale or an expired head and ends at the first in-window entry, and a removal uses `remove(key, entry)`); with nothing evictable it examines one node. The two map removals are gated by `isExpired` (the sweep removal is the leaf's reading, not re-read).
- **The surrogate-safe cut is correct** (`ErrorTextSanitizer.java:168-178`): when the last kept character is a high surrogate one fewer is kept, for the 256 and the 64 forms.
- **STRICT refuses any unquoted `scope` and LENIENT is unchanged** (`BearerChallengeParams.java:177-183`: the check needs `mode == STRICT`; the two-argument `parse` passes an empty set; `StepUpChallengeParser` is untouched). **A Unicode-blank scope** now goes through the token grammar and is refused (`InsufficientScopeChallengeParser.java:121-157`), as is a tab-only scope.
- **`StepUpResultVerifier` is unaffected** (`:105-128`): the required set is a whitespace split of the challenge's own `acr_values` and the ID token's `acr` must equal one of them, so a polluted LENIENT value can only name tokens the resource server itself chose. This settles the PLAN-26 L3-3 hypothesis by reading.
- `BearerTokenInterceptor` and the two health checks are untouched by the diff and read clean for fail-open; `reflect-config.json` is not changed by this PR (leaf).

**Layer 3 not shown by reading:** whether the OpenRewrite recipe honours the catch-directive placement in `TokenValidatorHealthCheck`; the native and ArC behaviour of the interceptor; the e2e hang before teardown (`stop-dev-environment.sh` is untouched and cannot name the 30-minute hang); test counts, the scratch-run failure counts (4/7, 3/7, 3/3, 2 of 34, 10 of 55), the 4363-test figure, the roughly 110 reactor errors against the spec's "20 identical errors" (a different command and error cap, likely); the rsvg render; the 884-link check; the RFC 6750 §3 text; Sonar and CodeRabbit threads beyond the check state.

**Layer 3 verdict:** findings — 7 (5 minor, 2 info), no blocker or major. Reported to the operator in the same turn.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` Logging Standards | `CuiLogger`, `LogRecord` for WARN, `%s` placeholders, exception first | conforms | `DpopProofValidator` WARNs use `LogRecord`s; the new DEBUG lines use `%s` (`BearerChallengeParams.java:180`, `DpopReplayProtection.java:204`) |
| cui-logging review rule | a changed `LogRecord` needs a `LogAsserts` check | conforms | `DpopProofValidatorLogSanitizationTest` uses `LogAsserts` on the WARN lines |
| `CLAUDE.md` / java-security | no raw remote text in logs or exception messages | conforms | all remote-text sinks sanitised (see clean by reading); `InsufficientScopeChallengeParser` stores `realm` and `error_description` escaped and never logs them |
| java-security | the shared sanitiser neutralises the formatting characters that spoof a log line | deviates (minor) | L3-3: the nine bidi controls are covered, LRM, RLM, ALM, zero-width, tag characters and U+FEFF are not |
| `AGENTS.md` Test Class Size | new or grown test classes at or under 500 lines, or a reason | conforms | `JweDecryptorEcdhEsAndCompressionTest` 516 (was 492), reason in the PR body per the leaf; `InsufficientScopeChallengeParserTest` 449, `DpopProofValidatorLogSanitizationTest` 197 (re-counted) |
| java-null-safety | a `@Nullable` return is handled | not_shown_by_reading | `ErrorTextSanitizer.sanitize` is `@Nullable` on its return and the result feeds non-null parameters (`DpopProofValidator.java:345-351,357-361,448-454,632`); only the compiler configuration decides, and the PR reports pre-commit green |
| java-core concurrency | check-then-act on shared state uses an atomic method | conforms | `putIfAbsent`, `replace` and `remove(key, value)` on the map; queue consumption under `evictionLock`; the volatile probe is a test aid |
| junit-core | Awaitility and no `Thread.sleep` | conforms | `DpopReplayProtectionTest.java:180-193` uses `pollDelay` for the wall-clock wait that is the subject of the test |
| junit-core review rule | a negative assertion that can go vacuous | deviates (minor) | L2-4 |
| junit-core review rule | a hard-coded set that mirrors a set defined elsewhere | deviates (minor) | `isEscaped` and `bidirectionalControls` in the attack-database test re-list the bidi ranges; a drift trap |
| junit-core review rule | a security test names the clause it exercises | conforms | `DpopProofValidatorLogSanitizationTest` cites ADR-0022 and CWE-117; the parser tests cite RFC 6750 §3 and RFC 6749 §3.3 |
| JUnit 5 + cui-test-generator | parameterise three or more variants | conforms | `@ParameterizedTest` for the four epk rows, two omitted identifier claims and nine bidi controls |
| `AGENTS.md` / `CLAUDE.md` | never gate on a reactor `test-compile`/`test`; `AGENTS.md` owns the commands | conforms, with the L3-4 and L2-7 caveats | the `CLAUDE.md` gotcha moved to `AGENTS.md:82-130`; the generator-jar scopes, `successCodes` 0 and 1 and the `wrk` surefire setting match the poms (leaf) |

## Metrics and Anomalies

- Tokens: 45,147,084 (lane figure, `total_tokens`; no per-phase split in the message).
- Duration: 115,195 s wall (about 32 h, including waits for CI, the operator and the quota; lane figure).
- Model and effort: `claude-sonnet-5-5`, `medium`; `refresh-runtime-stamp` returned `refreshed: false` (`model_unknown`), so these are the start-time self-reported values. Plugin and executor `0.1.2090`.
- Anomalies: the pr-comment pipeline filed 14 findings (all `fixed`) across #891 and #892, driving fix tasks TASK-020 to TASK-025; three of the six review-fix loops came from build commands that did not do what the prose said (candidate lesson 016); the code review looped back once for a sink the sweep missed (finding d42731); the functional review looped back for the SVG render (D5.R1) and the operator had to install `rsvg-convert`; the test-integrity check raised the same 27-assertion phantom three times (candidate lessons 012, 014, 015); the argparse class recurred four times in this run (017 to 020).

## Routing and Merge Behavior

- Review: CodeRabbit gave a review on #891 and was rate-limited on the incremental re-review of the last commits, so #891 was closed and #892 recreated to start a fresh one (read from the PR body and `ci`: #891 `closed`, no merge commit). `unresolved_review_threads=0` (read from the provider after the merge, 6 threads); `review_body_findings_total` and `_handled` are `unknown`, because no finalize step parses review bodies. `sonar-roundtrip` confirmed 0 new-code issues but does not read security hotspots, so `sonar_open_new_issues` is `unknown`, not 0.
- Functional review: 9 of 9 deliverables met, 0 residual, 0 not met, 0 unevidenced (lane facts, `step.functional-review.*`). Code review: 0 blocking, 0 advisory (lane facts).
- CI/merge: all 24 checks green at the end (`ci checks status 892`: none failing). The first merge-queue entry was ejected after the End-to-End `merge_group` run 42990351708 hit the 30-minute job timeout (orphan java and playwright processes on the runner, per the lane; the run itself was not read by this analysis); the identical tree passed the PR-level End-to-End run in about 4 minutes each time, and the re-enqueue (`merge_group` run 43012955220) landed in 363 s. This is the e2e teardown-hang family the epic already carries, now recurring on the merge queue, not only on a PR run. The post-merge `ci checks wait` timed out at 611 s on the non-required `Run JMH Benchmarks` job; `ci checks status` shows it `SUCCESS` now.
- Parallelization: PLAN-28 ran alone (PLAN-26 and PLAN-27 had shipped); no collision.

## Reconciliation Actions

- [ ] row `status` → `shipped` — `orchestrator queue --transition PLAN-28 --status shipped`
- [ ] row `pr` stamped — `orchestrator queue --set-row PLAN-28 --field pr --value 892`
- [ ] row `landing` stamped — `orchestrator queue --set-row PLAN-28 --field landing --value landings/PLAN-28.md`
- [ ] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-28 --field plan_marshall_plan_id --value plan-28-epic-cleanup-sweep`
- [ ] epic.md narrative reconciled against the queue rows (queue annotation, Decisions, the Open Defects and Watches PLAN-28 carried, new residue defect, incomplete-landing recurrence)
- [ ] inbox messages 001-021 dispositioned and archived (6 promoted, 14 folded, the landing reconciled)
- [ ] resume anchor updated in `resume_anchor.md` — `manage-status update-field --field resume_anchor --store orchestrator`
- [ ] `queue-view.md` regenerated — `orchestrator regenerate-view`

## Follow-Ups

- Layer-3 and layer-2 residue (L3-1 to L3-5, L2-1 to L2-7, the `AGENTS.md` wording, the `JweDecryptor` Javadoc): minor, no fix plan staged; recorded as one Open Defect, a doc-and-test plan only on the operator's word.
- The e2e hang now recurs on the merge queue (run 42990351708): folded into the existing Watch; the owner is `cuioss-organization` (the lane's D7 evidence).
- Incomplete landing facts (`review_body_findings_*`, `sonar_open_new_issues`): folded into the existing incomplete-landing Open Defect; upstream-owned.
- Held on the operator's word: the API Sheriff reply and release note (the parser, the contract test, the sibling tightening and now the STRICT unquoted-scope refusal are on `main` and in no release).
