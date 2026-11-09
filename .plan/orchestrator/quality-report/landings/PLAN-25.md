# Landing Analysis: PLAN-25 — Epic leftover sweep (the final cleanup)

epic: quality-report
workstream: WS-05
pr: #883 (validation half, 47045031) and #884 (client and documentation half, aeb3fa49), both merged 2026-11-09

> Landing record for one shipped plan, shipped as TWO PRs. Source: inbox message
> `plan-25-epic-leftover-sweep-060` (landing-check complete: true; its facts cover #883 only), corroborated against git,
> the CI abstraction and the merged code at main `aeb3fa49`. A pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

The plan-marshall lifecycle ran 26 deliverables (the spec has 10 items with sub-items). It grew to 163 files, over the
required review bot's 100-file cap, so the operator split it: #883 carries the validation half (77 paths by name-status;
the lane says 74), #884 the client, client-Quarkus, client ITs, all of `doc/` and the quarkus-parent README (93 files).
The landing's `deliverables_done=16` counts only #883.

| Spec item | Verdict | Evidence |
|-----------|---------|----------|
| 1 native class-level `@BearerAuth` fixture | shipped-modified (1(a), 1(b) fail-open verdict, CI-runner question: no evidence) | `ClassLevelUnregisteredAuthEndpoint.java:48-92`, `BearerAuthSpecIT.java:410-460`; `BearerTokenInterceptor` untouched; native run not shown |
| 2 DSL-JSON guard | shipped-as-specified | `DslJsonTruncatedEscapeGuardTest.java:81-94`, controls at 154-163, stale-exemption leg 183-202 |
| 3 dead code | shipped-as-specified | `ClaimValue` -15, `ClaimValueTest` -20 |
| 4(a), 4(d) | shipped | `SignatureVerificationUtilTest` +51, `NonValidatingJwtParserTest` +216; 4(b) edited but its scratch-run result is not recorded; 4(c) dropped |
| 5(a) test-impl mapping | shipped (#884) | `doc/developer/test-impl-mapping.adoc` |
| 5(b) max-staleness | shipped | `IssuerConfigResolverMaxStalenessTest`, `JwtPropertyKeysTest` |
| 5(c) test-size budget | **shipped-modified, opposite of the operator decision** | `AGENTS.md:125` "should stay within 400 lines", `:126` "more than 1000 lines must be split" |
| 6 CWE-117 sanitisation sweep | shipped, wider than the three named classes (about eight more) | `LogRecordSanitizationGuardTest` |
| 7(a)-(h) docs, ADRs, response/header strictness | shipped (#883 + #884) | ADR-0027, ADR-0028, ADR-0017 amendment, `JsonResponseMediaType`, `JsonNumberLongConverter`, `HeaderValues`, `StepUpChallengeParser` |
| 7(i) identifier claims | **shipped-modified, opposite of the operator decision** (null refused, not absent) | `StrictStringMapper.java:52`, `ClaimName.java:62,227`, `MapRepresentation.explicitNullKeys` |
| 7(j) client egress | **shipped-modified, opposite of the operator decision** (a host-address policy was added) | `BackChannelHttp.java:148,178,239`, `ClientConfiguration.getEgressPolicy`, Quarkus keys, COMMONS-3 |
| 7(k)(1) JWE size mismatch | **shipped-modified, opposite of the operator decision** (limit widened to 32 KiB) | `NonValidatingJwtParser.java:365-370`, `JweDecryptionConfig.java:51`, `JweTokenSizeEntryPointTest.java:137-198` |
| 7(k)(2),(3), 7(l)(1)-(4),(7) Javadoc and doc corrections | shipped (#883 + #884) | client Javadocs, ADR-0010, token-handling.adoc, quarkus-parent README |
| 7(l)(5) ADR-0026, 7(l)(6) AsciiDoc link check | no evidence of verification | ADR-0026 +12 only |
| 8 #763 `invalid_scope` | shipped (#884) | `InvalidScopeException.java:67-91`, `RefreshFlow.java:313-315`, `TokenLifecycleManager` `SCOPE_REFUSED` arm |
| 9 e2e teardown | shipped as a hardened stop script; root cause not named | `stop-dev-environment.sh`; the criterion was waived to CI |
| 10(a),(e)-(g) | shipped (#884) | `RefreshAdversarialTest`, `doc/validation/architecture.adoc`, `TokenStoreTest` +26, `threat-model.adoc` +81 |
| 10(b) | shipped (#883) | `AccessTokenValidationPipelineTest` +58 |
| 10(c) health-check catch, 10(d) JweDecryptorTest gap rows, 10(h) SVG render | no evidence | files untouched, rows only moved |

Files no deliverable explains: `UserInfoResponse.StrictSubjectConverter` (non-string userinfo `sub` refused), the
`SUBJECT` mapper change and `SubjectClaimTypeRejectionTest`, `MapRepresentation.explicitNullKeys`, the 32 KiB cap in
`JweDecryptionConfig`, the client egress policy and its two Quarkus keys, `JsonNumberLongConverter` and the Content-Type
enforcement at token, PAR and userinfo. `.github/workflows/benchmark.yml` is Dependabot (#881/#882), outside the plan.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf at `execution-context-level-3` (resolved
`orchestrator.analyze` -> `plan.effort`), by reading, no build, no test run, no write. Reason for its own leaf: a
full-ship landing is always read by one leaf, and this one spans two PRs and about 170 files. The orchestrator re-read
these `checked` points at their `file:line` on main `aeb3fa49`: `AGENTS.md:122-133`; `DpopReplayProtection.java:80-144`;
`NonValidatingJwtParser.java:365-370`; `JweDecryptionConfig.java:51`; `StrictStringMapper.java:52`; `ClaimName.java:62,227`;
`BackChannelHttp.java:55,148,178,239`; the `NonValidatingJwtParserTest` size (1023 lines); `DpopProofValidator.java:286,351,441,457`;
`LogRecordSanitizationGuardTest.java:55-68`. Points not re-read (`TokenBuilder` refusal paths, `UserInfoResponse`,
`JsonResponseMediaType`, `TokenEndpointClient` Content-Type handling, `HttpJwksLoader` log changes, the `stop-dev-environment.sh`
rewrite) are recorded as leaf-reported, unverified leads.

- **Dispatch:** one read-only leaf per landing — reason: the depth rule; two PRs, 170 files, and four decisions to compare against main.
- **Verdict:** layer 1 findings (2); layer 2 findings (the four inverted decisions, one self-violated rule, unexplained files); layer 3 findings (DPoP replay cache, DPoP log sanitisation).

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | The spec's Done-when is written for ONE PR ("the PR head reads 0 Sonar issues", "report both numbers in the PR body"); the split leaves part two without an owner | checked | spec Done-when; #883 and #884 |
| L1-2 | Spec 5(c) (default: split nothing) and the mailbox (500-line guideline) disagree; the plan followed neither. The spec's "400" figure was stale at the source | checked | spec 5(c), `AGENTS.md:125-131` |

**Layer 1 verdict:** findings — 2

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained / unborne | Verdict |
|-------------|-----------|--------------------|-----------------------|---------|
| 7(k)(1) JWE | `NonValidatingJwtParser.java:365-370` | `JweTokenSizeEntryPointTest.java:137-198` | **inverted**: the 8 KiB limit was kept only for a JWS and the inner JWS; a 5-part token now follows the 32 KiB limit | checked |
| 7(i) null claims | `StrictStringMapper.java:52` | `IdentifierClaimNullRejectionTest`, `TokenBuilderExplicitNullTest` | **inverted**: explicit null refused (`MISSING_CLAIM`), `MapRepresentation` changed; `SUBJECT` made strict (a numeric `sub` is now refused), userinfo `sub` refused | checked |
| 7(j) egress | `BackChannelHttp.java:148,178,239` | `BackChannelEgressPolicyTest`, `ClientConfigurationTest` | **inverted**: default-on host-address policy (loopback, link-local, site-local, ULA, CGNAT refused); docs rewritten to match, none left saying the opposite | checked |
| 5(c) size | `AGENTS.md:125-131` | none | **inverted**: "should" at 400 and "must split" above 1000; 500 appears nowhere; four existing classes were split (pure-move-sized, 4 to 38 non-moved lines) | checked |
| 5(c) self-violation | `NonValidatingJwtParserTest.java` | n/a | the plan's own +216 took it from about 810 to 1023 lines, over the rule it wrote at `AGENTS.md:126`; not split. About 20 created or edited classes are over 500 lines (created: `DslJsonTruncatedEscapeGuardTest` 660, `HttpJwksLoaderFailureAdvertisedJwksUriTest` 533, `DpopProofValidatorAlgorithmAndClaimTest` 508) | checked |
| 1(a),(b), 4(b) result, 7(l)(5),(6), 10(c),(d),(h), 9 root cause | not shown | not shown | no evidence in the diff | not_shown_by_reading |
| Done-when: Sonar, e2e, `verify -Ppre-commit`, native IT | n/a | n/a | cannot be shown by reading (see Routing for the measured Sonar and e2e figures) | not_shown_by_reading |

**Layer 2 verdict:** findings — 5; not shown by reading — 3

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `DpopReplayProtection.java:84-104,122-144` | a flood of fresh jtis inside the 360 s window | `evictOldest` only removes entries older than the TTL, so the map exceeds `maxSize` (10000) without bound; the only signal is a DEBUG line. The code comment says this is intended. The landing's residue is confirmed; it is pre-existing (#565), not introduced by PLAN-25. The flood needs a valid DPoP-bound access token, so it is authenticated | major |
| L3-2 | `DpopReplayProtection.java:99-101,133-139` | the same flood, once over `maxSize` | every later insert runs a full stream, sort and limit over all entries (O(n log n)) while nothing is evictable: a CPU amplification under the same flood. Not named by the landing | major |
| L3-3 | `DpopProofValidator.java:351,441,457` | a valid proof whose `jti`, `htm` or `htu` contains CR/LF | the values reach `LOGGER.warn` unsanitised (CWE-117); outside the three classes deliverable 6 named; `LogRecordSanitizationGuardTest.java:58-59` is a name vocabulary (`kid`, `kty`, `alg`, ...) that does not list `jti`, `htm`, `htu`, `typ`, so the guard cannot see it | minor |
| L3-4 | `NonValidatingJwtParser.java:365-370`, `JweDecryptionConfig.java:51` | a 5-part token of 8 to 32 KiB under a JWE-enabled issuer | newly accepted: widening, against the fail-closed constraint. Narrowings in the same landing (null refusal, numeric `sub`, egress default, non-JSON Content-Type at token endpoint, quoted `expires_in`) are leaf-reported and not all re-read | major (policy, not a code defect) |
| L3-5 | `BackChannelHttp.java:239-246` | an IdP behind a container service name or private address | the new default refuses it unless `allowed-egress-hosts` lists it; the check resolves DNS before the connect and the client resolves again (DNS-rebinding limit); redirects to an internal host: not shown | minor |
| L3-6 | `JsonResponseMediaType.java:63-78` | n/a | duplicates `HeaderValues.containsLineBreakOrNul` (`HeaderValues.java:39-41`) | minor |

**Layer 3 not shown by reading:** L3-5 redirect behaviour of the upstream `cui-http` client; `TokenBuilder` refusing an explicit null for a non-identifier claim by calling a configured mapper (a throwing mapper could now reject a token that carried an explicit null).

**Layer 3 verdict:** findings — 6

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md:126 | a test class of more than 1000 lines must be split | deviates | `NonValidatingJwtParserTest` 1023 lines after the plan's own edit |
| AGENTS.md:125 | a test class should stay within 400 lines | deviates (soft) | about 20 created or edited classes over 500 lines |
| Spec Standing Constraints | fail-closed; nothing widens acceptance unless a deliverable says so | deviates | `NonValidatingJwtParser.java:365-370` |
| Spec item 6 | split at outline when sanitising touches more than the three named classes | deviates | about eight more classes touched, no split reason readable |
| Operator decisions (mailbox 001) | four decisions delivered to the running plan | deviates | all four inverted on main (see Layer 2) |
| AGENTS.md:109-113 | `%s` only; no new LogRecord; exception first | conforms | new calls use `%s`; no template changed |
| Spec Standing Constraints | native-image metadata for a reflected class | conforms | no new reflected class |
| Spec Standing Constraints | do not edit `source-report/` or `findings-disposition.md` | conforms | neither in the diff |
| Spec Standing Constraints | state in the PR body anything that narrows | not_shown_by_reading | PR bodies not read |
| CLAUDE.md Tool Usage | no Bash for file operations | deviates (the leaf, reported openly) | two `git grep`, one `grep`; no effect on a verdict |

## Metrics and Anomalies

- Tokens: 44,985,861 for #883's lifecycle; wall time 197,430 s (about 55 h).
- The landing's counts: 26 plan-marshall deliverables, 16 done in #883, 10 graded `superseded` for the split.
- Anomalies: the required review bot (CodeRabbit) refused the 163-file head, which forced a split late in finalize; the same
  `weakened_test` rows (pure-move splits and attribution artifacts) were re-filed about 40 times across two review steps and two
  rounds; the merge queue ejected #883 once on a Playwright screenshot flake and a re-dispatched enqueue cancelled the queued
  run. The review gates were closed as operator overrides on four accepted splits, the unevidenced D16.C1 and D26.C1.
- The lane's total: 59 candidate lessons were drained into the corpus (15 promoted, 2 recurrences appended to existing lessons, 42 discarded as duplicates).

## Routing and Merge Behavior

- Review: CodeRabbit on #883: 22 inline comments plus two summaries, all answered (two fix tasks, five inline edits). The DPoP cache design question was accepted as-is (L3-1).
- CI/merge: #883 merged 13:08:46Z on the third enqueue; #884 merged 14:25:13Z. Workflows on main are green at `47045031` and `aeb3fa49` (Maven Build, Integration Tests, End-to-End Tests, Scorecard).
- Sonar: read by the orchestrator at main `aeb3fa49` (analysis 2026-11-09T14:29Z): 0 unresolved issues on `branch=main&resolved=false`, 0 bugs, 0 vulnerabilities, 0 code smells, 0 hotspots, coverage 93.2 percent. The lane's "owed after the merge" Sonar read is therefore discharged for #884's head. The lane's per-PR figure is not compared (not in the landing facts beyond `sonar-roundtrip:done`).
- Done-when: Sonar 0 on main — met. Two consecutive green e2e runs on main — the two main pushes `47045031` and `aeb3fa49` are both green; this is two consecutive green runs, but the teardown hang cause was never named. Whole-tree `verify -Ppre-commit` — not shown by reading.

## Reconciliation Actions

- [x] row `status` -> `shipped`
- [x] row `pr` stamped `883,884`
- [x] row `landing` stamped
- [x] row `plan_marshall_plan_id` already `plan-25-epic-leftover-sweep`
- [x] epic.md narrative reconciled: PLAN-25 shipped, the four decision inversions recorded as an Open Defect awaiting the operator's word, the DPoP findings recorded as Open Defects (reported to the operator at once, not fixed inline), the not-shown items recorded as a Watch, the retired Residual Register items, the JWE Open Defect and the PLAN-19 doc-drift Open Defect dispositioned
- [x] 59 candidate lessons drained: 15 promoted (corpus lessons 2026-11-09-16-001 to -015), recurrences appended to 2026-11-05-23-002 and -003, 42 discarded as duplicates; 7 script-failure clusters discarded (no cause extracted; the pickup says file only on recurrence)
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- Four operator decisions inverted: ask the operator whether the in-run answers stand (the landing says the operator answered differently in the run). If they do not, a corrective plan is staged on the operator's word. Recorded as an Open Defect, not fixed inline.
- DPoP replay cache (L3-1, L3-2, L3-3): a staged fix-plan spec only on the operator's word; the landing residue's "fail closed at capacity" alternative is the design question.
- Not shown as done: 1(a),(b), 4(b) result, 7(l)(5),(6), 10(c),(d),(h), the e2e teardown root cause. Held as a Watch; PLAN-26 does not carry them.
- PLAN-26 (key-id contract test plus `insufficient_scope` parser) now shares only files PLAN-25 already merged: the overlap prompt is moot, the gate is re-evaluated at `next`.
