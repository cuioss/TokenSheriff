# PLAN-17: Sonar debt to zero

epic: api-sheriff-seams
workstream: WS-02

> **RETIRED the turn it was staged (2026-11-21), never emitted, never landed to `main` as a staged plan. Do not launch it.** Its premise is false. It was written from `sonar_rest search --project cuioss_TokenSheriff`, which sends no `resolved=false` and therefore lists resolved issues too: the 47 rows are the issues the archived `quality-report` epic's PLAN-23 (#855) already closed (the merged tree has its fixes: `ClientIpExtractorTest` is parameterised, `NativeTokenValidatorProducerIT` has no `assert true`), and the one genuinely open finding is `sonar read_open_issues --project cuioss_TokenSheriff` (`count_status: confirmed`, `open_issue_count: 1`: `java:S8714` in `BackChannelHttpBodyPhaseTest`, key `AaH5fQ48N3wbMb6uxphI`), which PLAN-16 carries. The file stays as the audit record of the mistaken reading (no spec file is deleted); every claim below that says "47 open" is contradicted.

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-17-sonar-debt-to-zero.md` and is queued as one row file, `queue/PLAN-17.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the operator's word of 2026-11-21 ("Are we at 0 sonar finding? if not, stage this as well") after the PLAN-15 landing. The answer was no: `sonar_rest search --project cuioss_TokenSheriff` on `main` (the analysis of 2026-11-21T12:34Z, after #940) lists 47 open issues (44 code smells, 3 bugs; 5 critical, 24 major, 14 minor, 4 info), the quality gate on `main` is `OK` (all six conditions), and the project has 0 security hotspots. The per-PR "new code" count of every plan of this epic was `0` or unknown; this is the debt that is older than the plans. One of the 47 (`java:S8714` in `BackChannelHttpBodyPhaseTest`) is PLAN-16's, because PLAN-16 edits that file, so this plan carries 46.
> Scope: four deliverables, below the guard's limit; they split by kind of change, not by file.
> NOT in this spec (not this repository's files, a decision the operator already took, or not a plan's work): the finding in `BackChannelHttpBodyPhaseTest` (PLAN-16), the marketplace-side findings, the stale `marshal.json`, API Sheriff's own deletions, and any change of public behaviour: every deliverable is a behaviour-preserving cleanup unless an operator decision at outline says otherwise.

## Objective

Bring the open Sonar issue count of the project to `0`, or to a set in which every remaining issue is dismissed on the operator's selection with a recorded reason, without changing what any class does. The read that counts is the one at the PR head after the plan's own analysis; a read that cannot be made (`undecidable`, a failed read, a merged PR whose head is gone) is recorded as unknown, never as `0`. No public API is added and no default behaviour changes.

## Deliverables

1. **Test code meets the Sonar rules the project's tests break (27 issues, all in tests).** By rule: `java:S8714` (MINOR, use `assertThrows()` or `assertDoesNotThrow()` instead of try/catch with `fail()`) ×11 — `AccessTokenCacheTest` (`AZ7xhrmpXGjoVdkwztNg`, `...Nh`), `IssuerConfigCacheConcurrencyTest` (`AZ7xhrnHXGjoVdkwztNi`), `IssuerConfigCachePerformanceTest` (`AZ7xhrmTXGjoVdkwztNc` to `...Nf`), `IssuerConfigCacheSynchronizationTest` (`AZ7xhroEXGjoVdkwztNj`, `...Nk`), `TokenValidatorPerformanceIntegrationTest` (`AZ7xhrluXGjoVdkwztNa`, `...Nb`); `java:S5778` (MAJOR, one invocation possibly throwing in a lambda) ×7 — `RefreshFailureClassificationTest` (`AaCppPbvN0abbpQgQmcv`, `...Qmcw`), `RefreshFlowTest` (`AaCppPeON0abbpQgQmcx`), `RotationReuseDetectionTest` (`AaCppPfxN0abbpQgQmcy`, `...Qmcz`), `WiredNegativePathAttackTest` (`AZ93HOBmCc1P49FnsLdk`), `TokenSheriffClientProducerTest` (`AZ9WhCj2bXNPmtFBBJLC`); `java:S2093` (CRITICAL, try-with-resources) ×1 — `AccessTokenCacheTest` (`AaEk5dTZqGtALy3xiLJk`); `java:S5976` (MAJOR, parameterize) ×1 and `java:S4144` (MAJOR, identical method bodies) ×1 — `ClientIpExtractorTest` (`AZ1S-qdbf2jVVBs5r-n3`, `...n2`); `java:S3457` (MAJOR, `%n` for `\n`) ×1 — `VertxServletObjectsResolverQuarkusTest` (`AZ1S-qb4f2jVVBs5r-n1`; `AGENTS.md` says a format string carrying `%n` must never be "fixed" by accepting a rewrite, so this one is read before it is touched); `java:S9439` (MINOR) ×1 — `DecryptionKeyLoaderTest` (`AaFosJ6XZMTG-656TYLR`); `java:S9427` (CRITICAL BUG, an `assert` statement) ×1 — `NativeTokenValidatorProducerIT` (`AaFosJ8sZMTG-656TYLS`); `java:S8786` (MAJOR, a regular expression with super-linear backtracking) ×3 — `VertxServletObjectsResolverScopingTest` (`AZ7xhrobXGjoVdkwztNl`, `...Nm`) and the test utility `JwtTokenTamperingUtil` (`AZ7xhrgAXGjoVdkwztNZ`).
   Settle at outline, with the operator: for each issue, fix it or dismiss it with a reason the operator selects (a `S5778` lambda whose second call cannot throw, a `S4144` pair that is deliberately a pair); the `S5778` and `S8714` fixes keep the assertion each test makes (the same exception type, the same message check) and add none; `S5976` parameterization keeps every variant as an argument; the `S8786` expressions are rewritten to a linear form that matches the same strings (a test that feeds both a matching and a backtracking input); the `S9427` `assert` is replaced by the framework's assertion in a native IT that this machine cannot run (the compile and the JVM-mode form are shown; the native run is the CI job's).
   Verification: a per-file before-and-after assertion count of every changed test class, equal except where a decision names the change; every changed test class still passes (`module-tests` of the owning module); a Sonar read at the head for the files of this deliverable shows none of the 27 keys open; no changed class grows past 500 lines or is split; a rename, if any, is its own commit.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-quarkus, plan-marshall:ref-code-quality
   - Persona: module-tester
2. **Main code: conditions that Sonar reads as always true or false, and the missing nullability annotations (9 issues).** `java:S2583` (MAJOR BUG, a condition that always evaluates to `false`) ×2 — `ClientAuthMethod` (`AZ9ntMHO7aqYlvt7BBYV`), `IdTokenValidationBridge` (`AZ9ntMG37aqYlvt7BBYT`); `java:S2589` (MAJOR, an expression that always evaluates to `true` or `false`) ×3 — `DiscoveryResolver` (`AZ9ntMGk7aqYlvt7BBYS`), `RefreshFlow` (`AZ9ntMBs7aqYlvt7BBYO`), `PostLogoutRedirectValidator` (`AZ9ntMGS7aqYlvt7BBYR`); `java:S4449` (MAJOR, annotate the parameter `@Nullable` or make sure null cannot be passed) ×4 — `IdTokenValidationBridge` (`noncesMatch`, `AZ9ntMG37aqYlvt7BBYU`), `StepUpHandler` (`create`, `AaGEZ-cXiEO5z3zzAeEk`), `HttpJwksLoaderConfig` (`uri`, `AaCuzxJNqUQmyvBP25IR`), `WellKnownConfig` (`uri`, `AaCuzxMLqUQmyvBP25IS`). These are in the client and the validation code a consumer's authentication path runs through, so the question for each is whether the condition is dead (remove it) or the analysis is wrong because a `@NullMarked` contract is broader than the code (a false positive, dismissed only on the operator's selection) or because the check is a deliberate fail-closed guard (keep it, say why in a comment or restructure so the analysis sees the case).
   Settle at outline, with the operator: for each of the nine, dead condition, deliberate guard or false positive, with the code read at HEAD (the Sonar line numbers are `0` in the listing, so the outline reads the line from Sonar's own view of the issue); a removed guard needs a test that the guarded case still behaves as before (the case is reachable by a caller outside the `@NullMarked` contract, or it is not); a `@Nullable` annotation is added only where the method really accepts null, and the contract of the method says so; no public signature changes without the operator's approval.
   Verification: for every issue a recorded verdict (removed, kept with the reason, annotated, dismissed on the operator's selection); the behaviour of each touched method unchanged and shown by the existing tests plus a new one where a guard is removed or a contract changes; `JwtPropertyKeys`-style reflective lists and native-image registration are untouched; a Sonar read at the head shows none of the nine keys open.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-http, plan-marshall:ref-code-quality
   - Persona: implementer
3. **Cognitive complexity of three methods is reduced to the allowed 15 (3 CRITICAL issues).** `java:S3776` — `TokenBuilder` (`AZ8rP6dm8tc1zsIwJVMz`, complexity 17), `TokenValidator` (`AZ8qIm0IgYwhxMzr5-UX`, 16), `IssuerConfigCache` (`AZ8qIm0agYwhxMzr5-UY`, 17). The three sit on the validation path of every request, so the change is a pure extraction of private helpers with no change of order, short-circuit or exception type.
   Settle at outline, with the operator: which method in each class and which helper to extract; whether any extraction changes the order of two checks (it must not); whether `AGENTS.md`'s broad-catch or logging rules constrain the helper.
   Verification: a before-and-after read of each method shows the same branches in the same order (a recorded comparison); the existing tests of each class pass without edited assertions (`TokenBuilderTest`, `TokenValidatorTest` and the class-level suites, `IssuerConfigCache*Test`), and the attack-database and negative-control suites of the validation module still pass; no new public or protected method; no new class unless the extraction needs one; a Sonar read at the head shows none of the three keys open.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-maintenance, pm-dev-java-cui:cui-logging, plan-marshall:ref-code-quality
   - Persona: implementer
4. **The remaining main-code findings (7 issues): the time zone, a stream, a cast, a path delimiter, a TODO.** `java:S8688` (INFO, pass a `ZoneId` or a `Clock` to `.now()`) ×3 — `AccessTokenCache` (`AZ6s6h-Kun0fPz_6Z6qo`), `AccessTokenValidationPipeline` (`AZ6s6h3Cun0fPz_6Z6qm`), `ValidationContext` (`AZ6s6h7pun0fPz_6Z6qn`); `java:S9391` (MAJOR, use a stream instead of a loop) ×1 — `AccessTokenCache` (`AaDsiIkr-a33HSAY02Lz`); `java:S9395` (MAJOR, cast a `long` to `double` explicitly) ×1 — `JwtMetricsCollector` (`AaDsiI9M-a33HSAY02L0`); `java:S1075` (MINOR, a hard-coded path delimiter) ×1 — `CustomAccessLogFilter` (`AZ8sYn8nN3xCn8X3OkRX`); `java:S1135` (INFO, a TODO comment) ×1 — `TokenLifecycleManager` (`AaGEZ-bfiEO5z3zzAeEj`). `git grep TODO` over `TokenLifecycleManager.java` at `dc5c3725` finds no `TODO`, so the last may be a stale analysis result (the file was edited since): the outline reads Sonar's view of it first.
   Settle at outline, with the operator: for `S8688`, whether the code already has an injected `Clock` the call can use (the project's tests inject time; a `Clock` is preferred to a `ZoneId`), and whether the fix changes which instant a comparison uses (it must not: the instant is the same, only the zone is explicit); whether the `S9391` stream is clearer or the loop stays with a recorded dismissal; the `S9395` cast spelled as the analysis asks; the `S1075` path built from the platform separator only where the code reads a file path (a URL or HTTP path keeps its `/`, which is the case Sonar's rule misreads); a stale `S1135` is recorded as stale (no code change).
   Verification: each issue closed, kept with a reason, or recorded stale; no change of any instant a validator compares against (a test that fixes the clock and shows the same result); the existing tests of the changed classes pass; a Sonar read at the head shows none of the seven keys open.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-logging, pm-dev-java:java-quarkus, plan-marshall:ref-code-quality
   - Persona: implementer

Done when: all criteria pass, the existing tests named in the Standing Constraints pass without edited assertions except where a deliverable names the decision, and the full `-Ppre-commit` plus `clean install` sequence of `AGENTS.md` is green. The Sonar open-issue read is owed to the `sonar-roundtrip` step after the PR exists, not a criterion the functional review grades before it (lesson 2026-11-19-21-002): it is `0` for the 46 keys of this plan and for any finding the plan's own commits add, or every remaining finding is dismissed on the operator's selection with a recorded reason (`unknown` for a failed read, never `0`); the project-level count on `main` after the merge is read once more and reported with the number, whatever it is. A closed key is read from Sonar, not inferred from the diff.

**Operator decisions needed at outline**

- **Each of the nine main-code conditions and annotations** (deliverable 2): dead, deliberate, or a false positive.
- **Each Sonar finding the plan proposes to dismiss** (all deliverables): the operator's selection and reason, per key.
- **Which method and which helper in each of the three complexity refactors** (deliverable 3).
- **The clock source for the three `.now()` calls** (deliverable 4).

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no history-framed wording in anything this plan writes, **including test method names, `@DisplayName` values, test class names, Javadoc and the PR body's description of the code**.
- **Behaviour is unchanged and proven so.** The tokens a validator accepts and refuses, the order of the checks, the exception types and fixed refusal texts, the logged identifiers, the metrics and the configuration keys are exactly as before; a changed condition has a test that fails without the change it pins, or a recorded reason that none can exist.
- **A guard is not removed because an analysis calls it dead** unless the code is read to show no caller reaches it, including a caller outside a `@NullMarked` contract, a reflective or framework caller (CDI, Quarkus, ServiceLoader, native image) and a test; a deliberate fail-closed check stays.
- **A dismissal is never the lane's own.** An open Sonar finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION — the question tool names each finding key, the progress record records the selection as `operator-selected: {issue-key}: {reason}`, and the chosen key is transmitted through the plan-less transition verb, `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. The lane never records a dismissal of its own, and `sonar-gate --dismissed-keys` is refused. A dismissal the run cannot transmit is a blocker, never a silent pass. The merge gate reads the open-finding count at the head — `0` confirmed, or every remaining finding's key selected by the operator — and an `undecidable` read blocks.
- **Say only what was read.** Every key, rule and file in this spec is from one Sonar read of 2026-11-21 and the line numbers are `0` in it (the search verb prints no line), so the outline re-reads each issue's line and rule text at HEAD; an issue the analysis no longer reports is recorded as stale, not fixed.
- **Test renames and splits are pure moves** (`AGENTS.md`): a new test goes in a new or an existing class by its own commit, never inside a rename; a class already over 500 lines is never split here; no changed test class grows past 500 lines.
- **The local verify does not run every Sonar rule** (lesson 2026-11-19-07-005: `S1130` and `S5778` reach the PR from a green `-Ppre-commit`): the plan reads Sonar after the first push and fixes what it reports in the same plan, not in a follow-up.
- **Security.** Attacker-controlled text goes through `ErrorTextSanitizer` (validation) or `LogSanitizer` (client); no token or key material is logged; the CWE-117 rule of ADR-0022 holds for every new or changed log line; refusal texts stay fixed. A new LogRecord takes the next free identifier in its range at write time and is added to `doc/LogMessages.adoc`.
- **Native image.** The helpers stay plain classes; no reflect-config entry and no new dependency without operator approval.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more similar variants, injected time (never sleeps), a negative control per regression test.
- Never gate on `test-compile`/`test`; use `verify`. **Never install into the shared `~/.m2` without the operator's word.**
- **Review-fix discipline** (lessons 2026-11-17-16-001, 2026-11-17-16-002, 2026-11-18-13-003): before applying a review-bot suggestion that changes a bounded structure, read the requirement row that states the bound; when an accepted fix contradicts a frozen deliverable criterion, ask in the same operator question whether to authorize the criterion change, and word the authorization for the whole change.
- **The plan's own rules** (lessons 2026-11-19-07-001 to -009, 2026-11-19-21-001 to -003, 2026-11-20-15-001, 2026-11-21-13-001 and -002): a framed test name, a second top-level class and a criterion that conflicts with `AGENTS.md` are each a defect of this plan, not an advisory; a test that failed and passed on rerun in CI is raised at `ci-verify`; a widened broad catch carries the `cui-rewrite:disable` directive and its reason in the same edit.

## Claim Labels

- OBSERVED: `sonar_rest search --project cuioss_TokenSheriff` returned 47 open issues (CODE_SMELL 44, BUG 3; CRITICAL 5, MAJOR 24, MINOR 14, INFO 4) on the 2026-11-21 read, and the rule tally is `S8714` 12, `S5778` 7, `S4449` 4, `S2589` 3, `S3776` 3, `S8688` 3, `S8786` 3, `S2583` 2, and one each of `S1135`, `S2093`, `S1075`, `S9395`, `S9439`, `S9427`, `S9391`, `S5976`, `S3457`, `S4144` — read this turn
  - verdict: contradicted | checked_at: a433e99005e56f19e5de045c5f6b99887193825c | by: api-sheriff-seams/analyze | rescoped: no | evidence: sonar_rest search sends no resolved=false and lists resolved issues too; sonar read_open_issues --project cuioss_TokenSheriff reports count_status confirmed, open_issue_count 1 (java:S8714 in BackChannelHttpBodyPhaseTest). The merged tree carries the archived quality-report PLAN-23 fixes (ClientIpExtractorTest parameterised, NativeTokenValidatorProducerIT without assert true). PLAN-17 retired unstaged.
- OBSERVED: `sonar_rest gate-status --project cuioss_TokenSheriff --branch main` answers `OK` with all six conditions `OK` (new coverage 94.5, new duplication 0.0, new ratings 1), and `sonar_rest hotspots --branch main` lists 0 hotspots — read this turn
- OBSERVED: the main-branch analysis of 2026-11-21T12:34Z is the latest `SUCCESS` task with `branch: main` (`sonar_rest ce-status --branch main`); #940 merged before the PLAN-15 landing message of 12:48Z — read this turn
- OBSERVED: the keys, rules and files of deliverables 1 to 4 are exactly the 46 rows of that read other than `AaH5fQ48N3wbMb6uxphI` (PLAN-16's) — read this turn
  - verdict: contradicted | checked_at: a433e99005e56f19e5de045c5f6b99887193825c | by: api-sheriff-seams/analyze | rescoped: no | evidence: sonar_rest search sends no resolved=false and lists resolved issues too; sonar read_open_issues --project cuioss_TokenSheriff reports count_status confirmed, open_issue_count 1 (java:S8714 in BackChannelHttpBodyPhaseTest). The merged tree carries the archived quality-report PLAN-23 fixes (ClientIpExtractorTest parameterised, NativeTokenValidatorProducerIT without assert true). PLAN-17 retired unstaged.
- OBSERVED: `git grep -n TODO` over `token-sheriff-client/.../lifecycle/TokenLifecycleManager.java` at `dc5c3725` finds no match, while Sonar lists `java:S1135` there (`AaGEZ-bfiEO5z3zzAeEj`) — read this turn
- OBSERVED: `AGENTS.md` says a format string carrying `%n` must never be "fixed" by accepting the rewrite (the `S3457` rule), and lists the sanctioned broad-catch forms (`:296-345`) — read at `a433e990` (the `%n` sentence by search)
- HYPOTHESIS: the Sonar line number of every issue is `0` because the search verb does not print it, not because the issues are file-level — not shown (verify-at-outline: open each issue's location)
- HYPOTHESIS: some of the 47 results are stale (the analysis does not re-run a file until a build touches it; `S1135` above is the first suspect) — verify at outline by reading the issue's line and the file at HEAD
- HYPOTHESIS: the `S2583` and `S2589` conditions in the client are dead because the surrounding contract is `@NullMarked`, and not because a framework caller can pass null — not shown (verify-at-outline, per issue)
- HYPOTHESIS: the three `S3776` methods can each be brought to 15 by extracting one private helper without changing the order of any check — not shown (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) each issue against HEAD (the current Sonar read and the file; no other plan except PLAN-16 may be running; read what has merged since `a433e990`), (3) that the deliverables stay at about five tasks or fewer per the guard; if the outline scopes above six, split at outline and record the split.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java`:1 — `S2093`, `S8714` ×2
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheConcurrencyTest.java`:1 — `S8714`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCachePerformanceTest.java`:1 — `S8714` ×4
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheSynchronizationTest.java`:1 — `S8714` ×2
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorPerformanceIntegrationTest.java`:1 — `S8714` ×2
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/WiredNegativePathAttackTest.java`:1 — `S5778`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtil.java`:1 — `S8786`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFailureClassificationTest.java`:1 — `S5778` ×2
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowTest.java`:1 — `S5778`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RotationReuseDetectionTest.java`:1 — `S5778` ×2
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducerTest.java`:1 — `S5778`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/logging/ClientIpExtractorTest.java`:1 — `S5976`, `S4144`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/servlet/VertxServletObjectsResolverQuarkusTest.java`:1 — `S3457`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/servlet/VertxServletObjectsResolverScopingTest.java`:1 — `S8786` ×2
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/jwe/DecryptionKeyLoaderTest.java`:1 — `S9439`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/NativeTokenValidatorProducerIT.java`:1 — `S9427`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientAuthMethod.java`:1 — `S2583`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`:1 — `S2589`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`:1 — `S2589`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpHandler.java`:1 — `S4449`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/PostLogoutRedirectValidator.java`:1 — `S2589`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/IdTokenValidationBridge.java`:1 — `S2583`, `S4449`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:1 — `S1135` (possibly stale)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:1 — `S4449`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfig.java`:1 — `S4449`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java`:1 — `S3776`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`:1 — `S3776`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java`:1 — `S3776`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java`:1 — `S9391`, `S8688`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/AccessTokenValidationPipeline.java`:1 — `S8688`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/context/ValidationContext.java`:1 — `S8688`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/metrics/JwtMetricsCollector.java`:1 — `S9395`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/logging/CustomAccessLogFilter.java`:1 — `S1075`

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`). The plan exists so that the release ships with an open Sonar count the operator can state as `0`, or as a list of selected dismissals.
- Depends on: PLAN-15 (shipped, #940, `a433e990`); the plan edits code and tests older than the epic. It depends on no unshipped plan.
- Inside the plan, 1 to 4 are independent; 3 touches the three classes every request passes through, so it runs after 2 for the shared validation files if the outline finds they share a method.
- Overlaps with: PLAN-16 (statements and fixture residuals) shares `HttpJwksLoaderConfig.java` (a nullability annotation here, a Javadoc there); the disjointness gate decides at emit. `BackChannelHttpBodyPhaseTest` is PLAN-16's and is not touched here.
- Adjacent to: the marketplace-side findings; API Sheriff's deletions of its own copies (its PLAN-34).
- Requirement, ADR and log ids are next free at write time (ADR numbering continues from `0031`; the next free is `0032`).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-17-sonar-debt-to-zero.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
