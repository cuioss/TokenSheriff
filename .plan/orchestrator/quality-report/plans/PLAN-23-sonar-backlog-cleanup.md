# PLAN-23: Close the pre-existing SonarCloud backlog on main

epic: quality-report
workstream: WS-05

## Objective

SonarCloud reports 44 open issues on branch `main` (read 2026-11-04 through the issues API, `branch=main`, `resolved=false`). None comes from this epic: the newest was created on 2026-10-10, before the first epic plan landed. No plan owns them, because every plan's merge gate reads only the issues of its own pull request. The operator's standing rule is that tool findings are fixed immediately, so this plan closes the backlog: fix each issue (preferred), or dismiss it with a stated reason through the Sonar transition when the finding is wrong or the flagged code is a deliberate defence. After the merge, the `branch=main` query returns 0 open issues, or only issues that carry a recorded dismissal. No report finding ID: this is tool hygiene, not a report finding.

## Deliverables

1. **Test-code issues (27 issues in 16 test files)** — mechanical and behaviour-neutral:
   `S8714` (11: replace `try`/`catch`+`fail()` with `assertThrows` or `assertDoesNotThrow`), `S5778` (7: one throwing call per `assertThrows` lambda), `S8786` (3: simplify super-linear regular expressions; check the pattern still matches the same inputs), `S5976` (parameterise three tests into one), `S4144` (two identical test bodies: merge or make the second one test something different), `S3457` (`%n` for `\n`), `S9439` (`new byte[]` prefix), `S2093` (try-with-resources), `S9427` (`assert` statement in `NativeTokenValidatorProducerIT` becomes a framework assertion, which matters because plain `assert` is off unless `-ea` is set). A converted test must still fail for the defect it names: do not turn a negative check into a vacuous one.
2. **Cognitive complexity (3 CRITICAL, main code)** — `S3776` in `IssuerConfigCache`, `TokenValidator` and `TokenBuilder` (16 or 17 against the allowed 15). Extract helper methods without changing behaviour; the existing tests are the pin, add one where a branch is not covered. `TokenBuilder` carries the one sanctioned `InvalidExceptionUsageRecipe` suppression named in `AGENTS.md`: keep it valid and keep its documented line reference accurate.
3. **Always-true or always-false conditions (5 issues, two typed BUG)** — `S2583` in `ClientAuthMethod` and `IdTokenValidationBridge`, `S2589` in `DiscoveryResolver`, `RefreshFlow` and `PostLogoutRedirectValidator`. Decide per site at outline, from the code: dead branch (remove it), a null check that the `@NullMarked` contract makes redundant (remove it), or a deliberate defensive check at a trust boundary. The third case is a dismissal with a stated reason, not a removal: fail-closed posture outranks the tool. State the choice per site in the PR.
4. **Nullness (4 `S4449`)** — `StepUpHandler.create`, `IdTokenValidationBridge.noncesMatch`, `HttpJwksLoaderConfig.uri`, `WellKnownConfig.uri`. Decide per site: the parameter really accepts null (annotate it `@Nullable` with the JSpecify annotation the project uses, and handle the null), or the caller must not pass null (fix the caller or the contract). Do not add the `javax.annotation` annotation the rule message suggests.
5. **Minor main-code rules** — `S8688` (3, INFO: give `now()` a `Clock` or `ZoneId`; prefer the injected clock where the class already has one so the value stays testable), `S9395` (cast `long` to `double` explicitly in `JwtMetricsCollector`), `S1075` (hard-coded path delimiter in `CustomAccessLogFilter`).
6. **Close-out** — re-query by issue key at outline (the keys and line numbers below come from the last analysis of `main`; the tree has moved, and some lines may have shifted or the issue closed). Every dismissal is recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition`. The PR's own Sonar gate must read 0 open issues.

## Standing Constraints

- Every claim below came from the Sonar issues API and file names, not from a code read; re-verify each against the implementing source at outline before scoping on it (refutation loops back to re-scope). The original report text is in `.plan/orchestrator/quality-report/source-report/`.
- Do NOT edit `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Behaviour-preserving by default. Anything that changes behaviour (a removed defensive check, a changed nullness contract) needs a test that fails before and passes after (JUnit 5, CUI test conventions); build and verify via Maven only.
- Fail-closed posture is non-negotiable: do not delete a check only because the analyser cannot see why it exists.
- Operator policy: tool findings (OpenRewrite markers, Sonar issues) are fixed immediately; suppression only in the rule's sanctioned syntax with a stated reason.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every remaining issue dismissed; an `undecidable` read blocks.
- Scope guard: 44 small issues in 32 files across four modules. Do the test-code deliverable first, it is mechanical. If the outline finds deliverables 2 to 4 growing past the rest, split them into a follow-up plan and record why. Do not widen into refactors beyond what a flagged method needs.

## Claim Labels

- OBSERVED: 44 open issues on `main`: 11 `S8714`, 7 `S5778`, 4 `S4449`, 3 each of `S2589`, `S3776`, `S8688`, `S8786`, 2 `S2583`, and one each of `S2093`, `S1075`, `S9395`, `S9439`, `S9427`, `S5976`, `S3457`, `S4144` — read from `https://sonarcloud.io/api/issues/search?componentKeys=cuioss_TokenSheriff&branch=main&resolved=false` on 2026-11-04
- OBSERVED: the newest creation date among them is 2026-10-10
- OBSERVED: 17 issues are in main sources and 27 in test sources; by module: validation 22, client 12, validation-quarkus 9, client-quarkus 1
- HYPOTHESIS: the line numbers below still point at the flagged code at HEAD `ed172e43` — they come from the last analysis of `main`, and PLAN-21 and PLAN-08 changed files in this list (`AccessTokenCache.java`, `TokenValidator.java`, `AccessTokenCacheTest.java`) (verify-at-outline, re-query by key)
- HYPOTHESIS: the `S2583`/`S2589` hits are a mix of dead code and deliberate defensive checks — decide per site, no site has been read

### Main-code issues

| Rule | Sev | File:line | Issue key |
|------|-----|-----------|-----------|
| S1075 (code smell) | minor | `CustomAccessLogFilter.java:149` | `AZ8sYn8nN3xCn8X3OkRX` |
| S2583 (bug) | major | `ClientAuthMethod.java:103` | `AZ9ntMHO7aqYlvt7BBYV` |
| S2583 (bug) | major | `IdTokenValidationBridge.java:188` | `AZ9ntMG37aqYlvt7BBYT` |
| S2589 (code smell) | major | `DiscoveryResolver.java:175` | `AZ9ntMGk7aqYlvt7BBYS` |
| S2589 (code smell) | major | `RefreshFlow.java:399` | `AZ9ntMBs7aqYlvt7BBYO` |
| S2589 (code smell) | major | `PostLogoutRedirectValidator.java:69` | `AZ9ntMGS7aqYlvt7BBYR` |
| S3776 (code smell) | critical | `IssuerConfigCache.java:174` | `AZ8qIm0agYwhxMzr5-UY` |
| S3776 (code smell) | critical | `TokenValidator.java:206` | `AZ8qIm0IgYwhxMzr5-UX` |
| S3776 (code smell) | critical | `TokenBuilder.java:125` | `AZ8rP6dm8tc1zsIwJVMz` |
| S4449 (code smell) | major | `StepUpHandler.java:94` | `AaGEZ-cXiEO5z3zzAeEk` |
| S4449 (code smell) | major | `IdTokenValidationBridge.java:81` | `AZ9ntMG37aqYlvt7BBYU` |
| S4449 (code smell) | major | `HttpJwksLoaderConfig.java:605` | `AaCuzxJNqUQmyvBP25IR` |
| S4449 (code smell) | major | `WellKnownConfig.java:252` | `AaCuzxMLqUQmyvBP25IS` |
| S8688 (code smell) | info | `AccessTokenCache.java:352` | `AZ6s6h-Kun0fPz_6Z6qo` |
| S8688 (code smell) | info | `ValidationContext.java:85` | `AZ6s6h7pun0fPz_6Z6qn` |
| S8688 (code smell) | info | `AccessTokenValidationPipeline.java:165` | `AZ6s6h3Cun0fPz_6Z6qm` |
| S9395 (code smell) | major | `JwtMetricsCollector.java:261` | `AaDsiI9M-a33HSAY02L0` |

### Test-code issues

| Rule | Sev | File:line | Issue key |
|------|-----|-----------|-----------|
| S2093 (code smell) | critical | `AccessTokenCacheTest.java:622` | `AaEk5dTZqGtALy3xiLJk` |
| S3457 (code smell) | major | `VertxServletObjectsResolverQuarkusTest.java:142` | `AZ1S-qb4f2jVVBs5r-n1` |
| S4144 (code smell) | major | `ClientIpExtractorTest.java:172` | `AZ1S-qdbf2jVVBs5r-n2` |
| S5778 (code smell) | major | `RefreshFailureClassificationTest.java:240` | `AaCppPbvN0abbpQgQmcv` |
| S5778 (code smell) | major | `RefreshFailureClassificationTest.java:256` | `AaCppPbvN0abbpQgQmcw` |
| S5778 (code smell) | major | `RefreshFlowTest.java:335` | `AaCppPeON0abbpQgQmcx` |
| S5778 (code smell) | major | `RotationReuseDetectionTest.java:127` | `AaCppPfxN0abbpQgQmcy` |
| S5778 (code smell) | major | `RotationReuseDetectionTest.java:147` | `AaCppPfxN0abbpQgQmcz` |
| S5778 (code smell) | major | `TokenSheriffClientProducerTest.java:454` | `AZ9WhCj2bXNPmtFBBJLC` |
| S5778 (code smell) | major | `WiredNegativePathAttackTest.java:491` | `AZ93HOBmCc1P49FnsLdk` |
| S5976 (code smell) | major | `ClientIpExtractorTest.java:77` | `AZ1S-qdbf2jVVBs5r-n3` |
| S8714 (code smell) | minor | `IssuerConfigCacheConcurrencyTest.java:213` | `AZ7xhrnHXGjoVdkwztNi` |
| S8714 (code smell) | minor | `IssuerConfigCachePerformanceTest.java:95` | `AZ7xhrmTXGjoVdkwztNc` |
| S8714 (code smell) | minor | `IssuerConfigCachePerformanceTest.java:167` | `AZ7xhrmTXGjoVdkwztNd` |
| S8714 (code smell) | minor | `IssuerConfigCachePerformanceTest.java:215` | `AZ7xhrmTXGjoVdkwztNe` |
| S8714 (code smell) | minor | `IssuerConfigCachePerformanceTest.java:217` | `AZ7xhrmTXGjoVdkwztNf` |
| S8714 (code smell) | minor | `IssuerConfigCacheSynchronizationTest.java:99` | `AZ7xhroEXGjoVdkwztNj` |
| S8714 (code smell) | minor | `IssuerConfigCacheSynchronizationTest.java:152` | `AZ7xhroEXGjoVdkwztNk` |
| S8714 (code smell) | minor | `TokenValidatorPerformanceIntegrationTest.java:69` | `AZ7xhrluXGjoVdkwztNa` |
| S8714 (code smell) | minor | `TokenValidatorPerformanceIntegrationTest.java:85` | `AZ7xhrluXGjoVdkwztNb` |
| S8714 (code smell) | minor | `AccessTokenCacheTest.java:275` | `AZ7xhrmpXGjoVdkwztNg` |
| S8714 (code smell) | minor | `AccessTokenCacheTest.java:340` | `AZ7xhrmpXGjoVdkwztNh` |
| S8786 (code smell) | major | `VertxServletObjectsResolverScopingTest.java:176` | `AZ7xhrobXGjoVdkwztNl` |
| S8786 (code smell) | major | `VertxServletObjectsResolverScopingTest.java:177` | `AZ7xhrobXGjoVdkwztNm` |
| S8786 (code smell) | major | `JwtTokenTamperingUtil.java:450` | `AZ7xhrgAXGjoVdkwztNZ` |
| S9427 (bug) | critical | `NativeTokenValidatorProducerIT.java:55` | `AaFosJ8sZMTG-656TYLS` |
| S9439 (code smell) | minor | `DecryptionKeyLoaderTest.java:168` | `AaFosJ6XZMTG-656TYLR` |

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientAuthMethod.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshFlow.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpHandler.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/PostLogoutRedirectValidator.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/IdTokenValidationBridge.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFailureClassificationTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/RefreshFlowTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RotationReuseDetectionTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducerTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/logging/CustomAccessLogFilter.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/metrics/JwtMetricsCollector.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/jwe/DecryptionKeyLoaderTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/logging/ClientIpExtractorTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/NativeTokenValidatorProducerIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/servlet/VertxServletObjectsResolverQuarkusTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/servlet/VertxServletObjectsResolverScopingTest.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfig.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/IssuerConfigCache.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/context/ValidationContext.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/AccessTokenValidationPipeline.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheConcurrencyTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCachePerformanceTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/IssuerConfigCacheSynchronizationTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorPerformanceIntegrationTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/WiredNegativePathAttackTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/test/JwtTokenTamperingUtil.java`

## Dependencies and Sequencing

- Depends on: PLAN-09 (shared `ValidationContext.java`, and `AccessTokenCache.java` if PLAN-09 touches it at outline). Start only after PLAN-09 has merged
- Overlaps with: PLAN-22 (`AccessTokenCacheTest.java`, `TokenValidator.java`), PLAN-17 (`TokenValidator.java`). Run PLAN-23 before PLAN-22 and PLAN-17; each re-reads the file at its own outline
- Adjacent to: PLAN-20 (marker cleanup, shipped #847), the same fix-immediately rule

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-23-sonar-backlog-cleanup.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
