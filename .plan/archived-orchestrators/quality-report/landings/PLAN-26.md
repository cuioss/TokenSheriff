# Landing Analysis: PLAN-26 — API Sheriff downstream requests: the `getKeyIds()` contract test and the `insufficient_scope` challenge parser

epic: quality-report
workstream: WS-01
pr: #888 (merged, queue-merged as `7520e165` on main, 2026-11-10)

> Landing record for one shipped plan. Lives at `landings/PLAN-26.md`. Written by the `analyze` verb (inbox scan) after
> verifying claims against ground truth (the merge commit, the PR through the `ci` abstraction, the merged code) — the landing
> message is a lead, never a fact. Sender plan id `jwks-key-ids-contract-pin`; messages 001-005 of that sender.

## Deliverable Fidelity vs Spec

Corroborated against git and `ci`: `ci pr view 888` reports `merged`, merge commit `7520e165c93fd25704394cfcb8020e9fb28ca1ee`; the diff
`7520e165^1..7520e165` is 18 files, +1796/-125; `ci checks status 888` reports 24 checks, `overall_status: success` (20 pass, 4 skipped,
`e2e-tests / test` 240 s, `build / sonar-build` and `CodeRabbit` success). The landing message's `landing-check` is `complete: true`.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `JwksLoader` key-id contract test, points (a)-(h) through the factory, test only | shipped-as-specified | `JwksLoaderKeyIdsContractTest` (310 lines) + `JwksLoaderKeyIdsContractSupport` (167); (a)-(e) parameterised over FILE / IN_MEMORY / HTTP, (f)-(h) HTTP only; no line under `token-sheriff-validation/src/main` changed (`git diff --stat` over that path empty); PR body lists a mutation per point (b)-(g) |
| 2. RFC 6750 `insufficient_scope` parser, one shared splitter, `CLIENT-25` docs, tests | shipped-modified | `InsufficientScopeChallengeParser` (186), `BearerChallengeParams` (290), `StepUpChallengeParser` 131 lines (was 224 at `aeb86e4d`: +16/-109 in the diff), `InsufficientScopeChallengeParserTest` (389), `InsufficientScopeChallengeAttackDatabaseTest` (162); criteria (a)-(i) each trace to code and a test; modified: the sibling tightening is wider than the spec's list (L2-1), the attack test has a different name than the spec's, two public constants were added (L2-2) |
| Added, unplanned | added-unplanned | `InsufficientScopeChallengeSpecIT` (73) and `references.adoc`, `test-strategy.adoc`, `test-impl-mapping.adoc`, the two READMEs: all explained by the CLIENT-25 convention, none named in the spec's Expected Surface (L1-5) |

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, the `orchestrator.analyze` surface) read the merged code, the spec and the diff, by reading
only. The orchestrator then re-read every point recorded as `checked` below at its `file:line` in the main checkout (clean, at
`7520e165`) or with `git show`, and re-counted the two figures the leaf gave (test annotations at `aeb86e4d`, file line counts).
Nothing was built or run, so every runtime claim stays a lane claim.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code, a spec and an 18-file diff is the depth the dispatch rule exists for, and this is the only landing in this drain.
- **Verdict:** layer 1 findings (4 minor, 3 info); layer 2 findings (1 minor adjudicated down from the leaf's major, 4 minor, several info); layer 3 findings (5 minor, 1 info, no blocker, no major). No layer-3 defect above minor.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 | The spec is inconsistent: it asks for a shared splitter that is "behaviour-preserving" for the sibling and guarded by the UNCHANGED `StepUpChallengeParserTest`, and it lists the allowed sibling tightening as other control characters and size only, yet criterion (h) (a second scheme must not leak) cannot be met in a shared splitter without also refusing a valueless segment, which rewrites an existing test | checked | spec `PLAN-26-…md:89-101`; `StepUpChallengeParserTest.java:189-196` now `shouldRefuseSegmentWithoutEquals`, was `shouldSkipSegmentWithoutEquals` at `aeb86e4d` |
| L1-2 | The (h) HYPOTHESIS (`Basic realm="x"` after a comma leaks) names the wrong path: the splitter keyed it as `basic realm`. The real leak was a VALUELESS segment: `Bearer error="insufficient_user_authentication", Basic, acr_values="x"`, where `parseParams` did `if (eq <= 0) continue`, so `Basic` was skipped and `acr_values` read as a Bearer parameter. The plan found and closed a real defect in the sibling | checked (old code re-read at `aeb86e4d`); the PR's scratch run that observed the leak is a lane claim | spec `:162-163`; `aeb86e4d:…/StepUpChallengeParser.java` `parseParams` (`if (eq <= 0) { continue; }`); now `BearerChallengeParams.java:139-142` refuses |
| L1-3 | Criterion (f) says to drive the rotation with `performBackgroundRefresh`; that method is package-private in `jwks.http` (`void performBackgroundRefresh()`), and the contract test sits in `jwks` and goes through the factory, so the premise was unusable; the test uses the 1 s scheduler and Awaitility | checked | spec `:47-48`; `HttpJwksLoader.java:1024`; `JwksLoaderKeyIdsContractTest.java:79-81,204,228` |
| L1-4 | The spec says `StepUpChallengeParserTest` has 13 tests; a count of `@Test`/`@ParameterizedTest` lines at `aeb86e4d` is 14 (the PR body also says "the other 13 of the original 14") | checked (re-counted) | spec `:99`; `git show aeb86e4d:…/StepUpChallengeParserTest.java` |
| L1-5 | The Expected Surface under-declares: the IT, `references.adoc`, `test-strategy.adoc`, `test-impl-mapping.adoc` and the two READMEs are absent, and the attack test is named `…AttackDatabaseTest`, not `…AttackTest`. No collision resulted (PLAN-27 touched none of them), so no pairing was mispredicted | checked | spec `:169-183`; PR body "Changes" |
| L1-6 | The line claims re-grounded at `aeb3fa49` hold (`StepUpChallengeParser:65`, `CLIENT-12` at `requirements.adoc:228`, no `CLIENT-25` before, `getKeyIds` at `JwksLoader:78`) | checked (by the leaf; not all re-read) | spec `:141-167` |
| L1-7 | The empty-scope-set choice for an absent `scope` is defensible; whether the PR body records it is not visible to the reading. The PR body does: "A challenge with no `scope` parameter yields an empty scope set rather than a refusal" | checked (PR body read) | `InsufficientScopeChallengeParser.java:110-114`; PR body "Outline decisions" |

**Layer 1 verdict:** findings — 4 minor (L1-1, L1-3, L1-4, L1-5) plus the info rows L1-2, L1-6, L1-7. The spec is a shipped one, so no verdict is persisted by `corpus set-verdict`.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 (a)-(e) | `JwksLoader` via `JwksLoaderFactory` | `JwksLoaderKeyIdsContractTest` `pointA`-`pointE`, parameterised over three loader types | none | checked |
| 1 (f), (g), (h) | `HttpJwksLoader.java:925-940` (rotation, no-usable-key guard) | `pointF`, `pointG`, `pointH` (HTTP only) | timing: refresh 1 s, grace 3 s, await 10 s (`:79-81`); a CI stall over 3 s between `awaitKeyIds` and the immediate `getKeyInfo(FIRST_KID).isPresent()` in (g) would fail it | checked; flakiness not shown by reading |
| 2 (a)-(e), (g), (h) | `BearerChallengeParams.java:92-169`, `InsufficientScopeChallengeParser.java:91-147` | `InsufficientScopeChallengeParserTest` nested classes, the PR's mutation list names (b), (e), (f), (h) | none | checked |
| 2 (f) | `BearerChallengeParams.java:96-109` | `InsufficientScopeChallengeAttackDatabaseTest` `shouldRefuseControlCharacter` and the line-break rows | the OWASP and ModSecurity corpus tests (`:126-138`) feed the payload as the whole header, none starts with `Bearer `, so the scheme check alone refuses them and they would pass without the new guards (the PR body lists this as its own follow-up 2) | checked |
| 2 (i) | stateless class, `static final LOGGER` only | `Statelessness` (`InsufficientScopeChallengeParserTest.java:365-387`): no instance fields and two equal calls | no concurrency test; the test would still pass if a static mutable field were added | checked |
| Docs | `requirements.adoc` `CLIENT-25`, README row, `step-up-and-logout.adoc`, `threat-model.adoc` | n/a | `CLIENT-25` omits the strict-mode refusal of a quote or backslash in an UNQUOTED value (the PR body's follow-up 4) | checked |

- **L2-1 (leaf: major; adjudicated: minor).** The `StepUpChallengeParser` tightening is wider than the spec's list (`:99-101`): a valueless segment, a following scheme and a non-token name are now refused; before, a valueless segment was skipped and a non-token name kept as a key (`aeb86e4d` `parseParams`). An existing guard test was INVERTED (`shouldSkipSegmentWithoutEquals` → `shouldRefuseSegmentWithoutEquals`, `StepUpChallengeParserTest.java:189-196`). The leaf rated it major because the spec called the unchanged test the guard. Adjudication: the PR body declares tightening 3 and the test removal ("Removed test (declared, operator-accepted)") and the lane's mutation record shows the refusal is load-bearing, so the departure is declared and fail-closed, not hidden; it is minor spec drift, plus a consequence the operator should know (see the Watch added in Follow-Ups). That the removal was "operator-accepted" is a lane claim; the plan's decision log is not readable from here.
- **L2-2 (minor).** `INSUFFICIENT_SCOPE` and `MAX_SCOPE_TOKENS` are public constants (`InsufficientScopeChallengeParser.java:76,79`); the PR body and the spec call the class "the plan's only new public API". The sibling also exposes its error constant, so this is drift, not a break.
- **L2-3 (minor).** The statelessness test (above) cannot catch a static mutable field or a race.
- **L2-4 (minor).** `InsufficientScopeChallengeSpecIT` is in no deliverable; it extends `BaseIntegrationTest` for a pure string function. Per the PR body it ran with tests=2, failures=0 in the integration profile; its cost is not shown by reading.
- **L2-5 (info).** No `LogRecord` was added, `doc/LogMessages.adoc` is untouched, the new classes log at debug only, and `git grep` finds no `reflect-config` entry naming the parser or its record; native-image behaviour is not shown by reading.

**Layer 2 verdict:** findings — 5 minor (L2-1 to L2-4 and the timing row), plus info.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `BearerChallengeParams.java:207-210` | STRICT mode, an UNQUOTED value without quote or backslash: `scope=read write`, `realm=a b` | accepted; the unquoted `scope` splits into two tokens. RFC 6750 makes `scope` a quoted string. Not exploitable (comma, quote and backslash are blocked), but looser than the documented grammar, and `CLIENT-25` is silent on it | minor |
| L3-2 | `InsufficientScopeChallengeParser.java:112` | `scope="<U+2003>"` (Unicode whitespace only) | `String.isBlank()` is Unicode-aware, so it yields a challenge with an EMPTY scope set, while the same character beside a token fails the ASCII grammar; inconsistent, and within the accepted empty-set choice | minor |
| L3-3 | `BearerChallengeParams.java:219-237` | LENIENT mode (the step-up parser): `acr_values="a" Basic realm="x"` | `unquote` yields the value `a" Basic realm="x`: a polluted value, not a leaked parameter. Inherited from before the extraction, not a regression. `StepUpResultVerifier` requires the ID token's `acr` to be among the required values (`StepUpResultVerifier.java:126`), so the pollution can only name tokens the resource server itself chose; how the value is split into tokens (`:106-110`) was not read | minor |
| L3-4 | `ErrorTextSanitizer.java:89,105-108` | `error_description` or `realm` over 256 characters with a surrogate pair at the cut | `substring(0, 256)` can split a pair; an escaped value cannot be told from literal `\u0085` text; bidi controls (U+202E) are not in the escaped set the Javadoc lists (`:25-26`). `LogSanitizer` is the shared class, so this is not specific to this plan | info |
| L3-5 | `JwksLoaderKeyIdsContractTest.java:203-218` | point (f), a live view over the OLD loader's map: `Collections.unmodifiableSet(keyInfoMap.keySet())` | not caught, because rotation swaps in a NEW `JWKSKeyLoader` (`HttpJwksLoader.java:936`) and the old map is never mutated. The PR's own mutation for (f) is a live view over `HttpJwksLoader.currentKeys`, which IS caught; so (f) is non-vacuous against the HTTP-level view only, and file and in-memory loaders get no (f) case | minor |
| L3-6 | `JwksLoaderKeyIdsContractTest.java:148-158` | point (c), second assertion | `assertTrue(loader.getKeyInfo(BLANK_KID).isEmpty())` can never fail, because `JWKSKeyLoader.getKeyInfo` returns empty for a blank kid (`:297-301`); the guarded line is the `getKeyIds` blank filter (`:321`), asserted by the `Set.of(FIRST_KID)` equality and by the PR's mutation (c) | minor |

What is clean by reading: every returned attacker-controlled string passes `LogSanitizer.sanitize` (`InsufficientScopeChallengeParser.java:105-106`, the escaped set includes C0, DEL, C1 including U+0085, U+2028 and U+2029, cut at 256 plus a marker, `ErrorTextSanitizer.java:25-31,51-55`); the second-challenge cases are refused in STRICT mode (`BearerChallengeParams.java:139-142,150-155`); duplicates are rejected case-insensitively (`:162-167`); quoted commas and `", error="` stay inside their value (`splitTopLevel`, `:176-201`); unterminated quote and trailing backslash refuse in STRICT (`unquoteStrict`, `:244-261`); the 8192 bound runs before the control scan, strip and split (`:96-99`); the scope grammar is exact (`InsufficientScopeChallengeParser.java:138-147`); the parser is stateless and null-safe.

**Layer 3 not shown by reading:** whether the point (g) and (h) timing is stable on a slow runner; whether the mutations the PR lists actually fail (scratch runs, lane claims); how `StepUpResultVerifier` splits a polluted `acr_values` value (L3-3); native-image behaviour.

**Layer 3 verdict:** findings — 6 (5 minor, 1 info), no blocker or major. Reported to the operator in the same turn.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` Logging Standards | `CuiLogger` as `private static final LOGGER` | conforms | `BearerChallengeParams.java:64`, `InsufficientScopeChallengeParser.java:81` |
| `AGENTS.md` Logging Standards | `LogRecord` only for INFO/WARN/ERROR, `%s` placeholders, documented in `LogMessages.adoc` | conforms | debug only, `%s` only, `LogMessages.adoc` untouched |
| `CLAUDE.md` / java-security | no raw untrusted text in log calls | conforms | untrusted names pass `LogSanitizer.sanitize` (`BearerChallengeParams.java:152-153,159,165`); `realm` and `error_description` are never logged |
| `AGENTS.md` Code Standards | JSpecify `@NullMarked`, `@Nullable` | conforms | `flow/package-info.java`, `@Nullable String wwwAuthenticate` |
| `AGENTS.md` Documentation | Javadoc `@since` on new public types | conforms | `InsufficientScopeChallengeParser.java:69`, `BearerChallengeParams.java:56` |
| spec | Javadoc usage example that compiles and a "does not do" statement | not_shown_by_reading | the `{@snippet}` block is present; compilation was not run |
| `AGENTS.md` Test Class Size | new or grown test classes at or under 500 lines | conforms | 389, 162, 340, 310, 167, 73 (re-counted with `wc -l` at `7520e165`); the largest new main class is 290 |
| `AGENTS.md` Parameterized Tests | parameterise three or more similar variants | conforms | `LoaderType` and `@ParameterizedTest` throughout |
| spec Standing Constraints | deliverable 1 test only; no `LogRecord`; sibling changed only by a stated fail-closed tightening | conforms with the L2-1 caveat | diff and `git diff --stat` |
| spec | one splitter, not two | conforms | `splitTopLevel` and `unquote` live only in `BearerChallengeParams` |

## Metrics and Anomalies

- Tokens: 19,271,348 (lane figure, `total_tokens`; no per-phase split in the message).
- Duration: 64,416 s wall (about 17.9 h, including waits for CI and the operator; lane figure).
- Model and effort: `claude-sonnet-5-5`, `medium`.
- Anomalies: two finalize loop-backs for review findings (iterations 1 and 2 of 5), one more because `branch-cleanup`'s rebase onto a newer `main` produced an ungated head so every head-dependent gate re-ran on `4c19ef2d`; the e2e job hung (below); the pre-push gate's reactor `test-compile` arm reproduced the documented main-baseline failure (20 identical errors on `main` per the PR body) and three build-error findings were accepted on that provenance.

## Routing and Merge Behavior

- Review: CodeRabbit gave two substantive reviews (commits `a615991c`, `44d5e01a`), each finding fixed; `unresolved_review_threads=0`, `review_body_findings_total=2`, `handled=2` (lane facts). CodeRabbit was rate-limited on the rebased head, so no re-review wait was taken (the operator's standing instruction). The two findings are the candidate lessons (inbox 001, 002): `realm` was returned raw while `error_description` was sanitised (caught by the bot after implementation, functional review and code review passed it), then the matching `realm` truncation test was missing.
- Functional review: 3 of 3 deliverables met, 0 unevidenced. Code review: 0 blocking, 6 advisory (the PR body lists four follow-ups: raw U+2028 in a string literal at `InsufficientScopeChallengeParserTest.java:223` — re-read, it is a raw character in the source; the OWASP and ModSecurity tests reach only the scheme check; the `unquote` Javadoc says a value that never closes its quote is returned unchanged but an escaped final quote is stripped; `CLIENT-25` omits the strict-mode refusal of a quote or backslash in an unquoted value).
- CI/merge: all 24 checks green at the end (`ci checks status 888`: 20 pass, 4 skipped, none failing). The `e2e-tests` job hung for 30 minutes on `4c19ef2d` and was cancelled with an empty log; the operator-approved re-run passed in 240 s (the passing run is the one `ci` shows, `e2e-tests / test` 240 s). The hung run itself is not visible here, so the hang is a lane claim; it matches the epic's e2e teardown hang Watch and is the first recurrence after PLAN-25's hardened stop script. The operator ran the post-rebase force-push themselves after the agent was denied it.
- Sonar: the lane states `sonar_open_new_issues=0` at head `4c19ef2d`; `build / sonar-build` is `SUCCESS` here, but the open-issue count itself was not read by this analysis. Recorded as the lane's measured figure, not re-read.
- Parallelization: PLAN-26 ran beside PLAN-27 with no collision; PLAN-26's six undeclared files overlap nothing PLAN-27 or PLAN-28 declares.

## Reconciliation Actions

- [ ] row `status` → `shipped` — `orchestrator queue --transition PLAN-26 --status shipped`
- [ ] row `pr` stamped — `orchestrator queue --set-row PLAN-26 --field pr --value 888`
- [ ] row `landing` stamped — `orchestrator queue --set-row PLAN-26 --field landing --value landings/PLAN-26.md`
- [ ] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-26 --field plan_marshall_plan_id --value jwks-key-ids-contract-pin`
- [ ] epic.md narrative reconciled against the queue rows (queue annotation, API Sheriff Watch, new Open Defects and Watches)
- [ ] PLAN-28 deliverable 7 filled with this landing's leftovers; Expected Surface and Claim Labels updated in the same edit
- [ ] inbox messages 001-005 dispositioned and archived (two promoted, one folded, one merged into a promotion, the landing reconciled)
- [ ] resume anchor updated in `resume_anchor.md` — `manage-status update-field --field resume_anchor --store orchestrator`
- [ ] `queue-view.md` regenerated — `orchestrator regenerate-view`

## Follow-Ups

- Leftovers L2-2, L2-3, L2-4, L3-1 to L3-6 and the four PR follow-ups: CARRIED by PLAN-28 deliverable 7 (a) to (f) (a test and doc group, no behaviour change except where item 7 says a fix is verified first). L1-1, L1-3, L1-4 and L1-5 are findings about the shipped SPEC (the inconsistent behaviour-preserving clause, the unusable `performBackgroundRefresh` premise, the 13-versus-14 count, the under-declared surface): they need no repository work and are recorded here only. L2-1 (the wider sibling tightening) is documented in `step-up-and-logout.adoc` and `CLIENT-25` and is carried as a Watch, not as work.
- The e2e hang recurrence: folded into the existing Watch and into PLAN-28 deliverable 6.
- The StepUp availability consequence of L2-1 (a multi-scheme `WWW-Authenticate` that carries a step-up challenge no longer triggers step-up): recorded as a Watch; no change proposed.
- The reactor `test-compile` baseline failure reproduced at the pre-push gate: evidence added to PLAN-28 deliverable 2(b); the claim there stays to be settled by a real run.
- Held on the operator's word: the API Sheriff reply and release note (the parser, the contract test and the sibling tightening are now in `main` and in no release).
