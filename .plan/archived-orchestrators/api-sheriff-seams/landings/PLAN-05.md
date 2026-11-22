# Landing Analysis: PLAN-05 — Validation public seams

epic: api-sheriff-seams
workstream: WS-02
pr: #899 (https://github.com/cuioss/TokenSheriff/pull/899), merged as fff5befee71def00818053f5b24eec46de9833c2

> Landing record for one shipped plan. Lives at `landings/PLAN-05.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-05-validation-public-seams-024.md` (lane `plan-marshall`, plan id
`plan-05-validation-public-seams`), plus 23 candidate-lesson messages (`-001` to `-023`). PR state read through
`ci pr view` (`state: merged`, merge commit as above, base `main`). `inbox landing-check`: `complete: true`;
`surface_delta` is `unmeasured` (the landing supplied neither the declared nor the realized surface), so the
declared-vs-realized comparison below was made by hand from the 48-file merge diff.

## Deliverable Fidelity vs Spec

The spec lists five deliverables; the landing reports `deliverables_total=8`, which the spec does not bear out
(the lane's outline ran twice and may have split them; the outline was not read — see L2-1). Compared against the spec's five:

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Public signature-only verification on `TokenValidator` | shipped-modified | `TokenValidator.verifySignature:544-557` runs `tokenStringValidator.validate`, then `idTokenPipeline.verifySignature`, touches no cache, counts one `SIGNATURE_ONLY_VERIFIED` event; result type `SignatureVerifiedToken` sits outside both sealed hierarchies (OD-1). Name and shape were the spec's operator decisions; `expectedTokenType` is skipped (OD-2). |
| 2. Native-JSON claim accessor | shipped-modified | `TokenValidator.getRawClaims(TokenContent):576-578` re-decodes `getRawToken()` with the validator's own parser (OD-4: lazy re-parse, validator-owned) and `SignatureVerifiedToken.getRawClaims()`; `MapRepresentation` gained a null-member-names component. |
| 3. Issuer visibility for a caller-built validator | shipped-modified | `IssuerStatusView` (narrow, non-fetching, no `IssuerConfig` or loader handed out; OD-5) plus opt-in `report-external-validator` and `disclose-issuer` keys (`JwtPropertyKeys`), `JwksEndpointHealthCheck.performHealthCheck:130-175`. |
| 4. Back-channel logout token | shipped-as-specified (Branch A, absent `typ` accepted) | `logout` package: `LogoutTokenValidator`, `LogoutTokenReplayGuard`, `LogoutTokenCheck`, `LogoutTokenValidationResult`, `package-info.java`. |
| 5. Documentation, traceability, ADR | shipped-modified | ADR-0029 (Status Accepted), `VALIDATION-13.x`, threat rows, `api-reference.adoc`, `test-impl-mapping.adoc` rows for all seven new test classes, `doc/README.adoc` ADR index. |

Added unplanned or outside Expected Surface (realized in the 48-file diff, not declared): `AGENTS.md` (the "never gate on `test-compile`" paragraph rewritten), four poms (`benchmark-core`, `token-sheriff-client`, `quarkus-integration-tests`, `token-sheriff-validation-quarkus`: `<type>test-jar</type>`, disclosed in the PR as commit `62b7f847`), `doc/client/requirements.adoc` and `doc/client/specification/step-up-and-logout.adoc` (cross-references to `VALIDATION-13.4`, declared only for Branch B), `JwtPropertyKeys` and its test, `quarkus-config-doc.adoc`, the integration docs `health-checks.adoc` and `metrics-integration.adoc`, `ExternalValidatorJwksReportingTest`, `UncheckedThrowBoundaryTest`, `MapRepresentationTest`, `ClaimMappingException`, `IssuerStatusView`. Declared but untouched: `IssuerConfigCache`, `TokenStringValidator`, `MeasurementType`, `JWTValidationLogMessages`, `TokenContent`, `BaseTokenContent`, `LogMessages.adoc`, `NativeImageMetadataTest`, and the HYPOTHESIS file `SignatureOnlyValidationPipeline` (no new pipeline class; `IdTokenValidationPipeline` gained a sibling method).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze`) — reason: reading merged code is the depth the Dispatch Decision Rule reserves for a leaf, and this landing's read shares no context with any other.
- The orchestrator re-read these `checked` points at their file:line on the merge commit before recording them: `LogoutTokenValidator:130-321` (L3-1..L3-4, the clean read of the checks), `LogoutTokenReplayGuard:95-126`, `TokenValidator:536-585`, `SignatureVerifiedToken:44-108`, `NonValidatingJwtParser:410-427`, `JwksEndpointHealthCheck:125-224`, the `AGENTS.md` diff hunk, the line counts of the three oversized test classes plus `ObservedValidatorResolverTest` (500), a content search of `logout/` tests for a huge-exponent input (none), a search of ADR-0029, `security-reference.adoc` and the logout package for a backward-clock statement (none), and `ADR-0029` Status. The remaining `checked` points (the `IdTokenValidationPipeline` refactor equivalence, `IssuerStatusView:38` and its counting-server test, `MapRepresentation:63`, the `getRawClaims(TokenContent)` WARN side effect, the Javadoc-example conformance rows) are carried as the leaf's reading and were not individually re-read.
- **Verdict:** layer 1 findings (2, minor) and not shown by reading (1); layer 2 findings (5, all minor or info); **layer 3 findings (4 minor, 4 info), one of them (L3-1) a resource-exhaustion defect in the new logout validator**, and not shown by reading (1). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | Spec HYPOTHESIS "`DecodedJwt.rawToken()` is the decrypted inner JWS" is refuted: for a 5-part token the parser returns the original JWE as `rawToken`. The spec's verify-first clause requires a refutation to loop back for re-scoping; it was absorbed into ADR-0029 Context instead. The downstream design (read the decoded body, never decode `getRawToken()` blindly) holds. | checked | refutes | `NonValidatingJwtParser.java:418-423` |
| L1-2 | The Standing Constraint "never gate on `test-compile` or `test`" copied into all seven specs rests on a premise main no longer holds: `AGENTS.md` now says the fixtures resolve in the reactor before `package` through `<type>test-jar</type>`. The constraint stays safe (`verify` still works) but its rationale is stale in PLAN-02, 03, 04, 06 and 07. Whether `test-compile` now builds green is not shown by reading. | checked | refutes | `AGENTS.md` diff hunk (fff5befe); `plans/PLAN-0{2,3,4,6,7}-*.md` (one `Never gate on` line each) |
| L1-3 | ADR-0029 records OD-1..OD-8, Status Accepted, with no operator attribution; that the operator took them is not shown by reading. The lane's own message (candidate 019) says the outline ran a second time "to revise with operator answers OD-1..OD-8", which corroborates it as the lane's claim only. | not_shown_by_reading | none | `doc/adr/0029-*.adoc:20` |

No verdict is persisted onto a spec: PLAN-05's spec is spent, and none of the findings names a claim of a staged sibling's own `## Claim Labels` section (L1-2 is a Standing Constraint, not a labelled claim).

**Layer 1 verdict:** findings — 2 (L1-1, L1-2); not shown by reading — 1 (L1-3)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `TokenValidator.verifySignature:544` | `TokenValidatorVerifySignatureTest`, `TokenValidatorVerifySignatureAttackTest` | none | checked |
| 2 | `TokenValidator.getRawClaims:576`, `MapRepresentation` | `RawClaimsTest`, `MapRepresentationTest` | none | checked |
| 3 | `IssuerStatusView`, `JwksEndpointHealthCheck:130-175` | `TokenValidatorIssuerStatusTest`, `ExternalValidatorJwksReportingTest`, extended `JwksEndpointHealthCheckTest` | none | checked |
| 4 | `LogoutTokenValidator`, `LogoutTokenReplayGuard` | `LogoutTokenValidatorTest` (per-check identity, positive controls), `LogoutTokenReplayGuardTest` | out-of-range `numericDate` branch has no test (L2-4) | checked |
| 5 | `doc/validation/*`, `api-reference.adoc`, ADR-0029 | n/a | `AGENTS.md` and four poms, see L2-2 | checked |

- **L2-1** The landing says `deliverables_total=8`; the spec has 5. Not contradicted by the diff (all five trace); the outline that set eight was not read.
- **L2-2** `AGENTS.md:115-123` (rewritten) and the four `<type>test-jar</type>` pom edits are outside the Expected Surface and were a disclosed, operator-requested fix to a pre-existing reactor failure, not a deliverable. In a pre-`package` reactor the type resolves to `target/test-classes` (all test classes) while the published jar is filtered, so reactor and published classpaths can diverge — not shown by reading.
- **L2-3** Test classes over the 500-line guideline: `LogoutTokenValidatorTest` 1012, `JwksEndpointHealthCheckTest` 793 (grew by 236), `TokenValidatorVerifySignatureTest` 553; `ObservedValidatorResolverTest` is at exactly 500. The lane's advisory, confirmed. All seven new test classes are listed in `test-impl-mapping.adoc`.
- **L2-4** No logout or verify-signature test feeds an out-of-range or huge-exponent `iat`/`exp` (content search finds none); the `fromIssuerStatus` catch branch (`JwksEndpointHealthCheck:222`-) has no test that makes a view throw (the only simulated `IllegalStateException` is on the loader path, `JwksEndpointHealthCheckTest:661`). The lane's "untested branches" advisory, confirmed.
- **L2-5** `doc/validation/security-reference.adoc:121` states that `IdTokenValidationPipeline` checks neither `events` nor `nonce`, so a logout token handed to `createIdToken` is refused only by ID-token claim checks or an optional `expectedTokenType`. Honestly documented and pre-existing; the reverse confusion is not closed by code.

**Layer 2 verdict:** findings — 5 (L2-1..L2-5), all minor or info

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `LogoutTokenValidator.java:311-312` (called inside `synchronized (decisionLock)` at `:170-186`, via `isFresh:245`, `isUnexpired:252`, and `:178`) | a signed logout token whose `iat` or `exp` is a number literal with an attacker-sized exponent, e.g. `1e99999999` (ten characters) | `new BigDecimal(...)` is cheap, but `exact.setScale(0, FLOOR)` scales by 10^99999999; the work and the transient allocation run while holding `decisionLock`, so every other logout validation on that validator blocks for the duration. The lane's own advisory names tens of seconds; not run here. The `catch` at `:317` only fires after the scaling is done. | minor (the signature must have verified first, `:157`, so the token must come from a key the trusted issuer holds; rises with a shared issuer or a compromised signer) |
| L3-2 | `LogoutTokenValidator.java:312` | `iat`/`exp` with a large negative exponent (`1e-99999999`) | takes the same `setScale(0)` path. The lane's proposed fix ("bound `precision()-scale()` before `setScale`") covers only the positive exponent; the bound must cover both signs. | minor |
| L3-3 | `LogoutTokenValidator.java:138-140`, `:247-248`, `:178` | `freshnessWindow` is only checked positive; `Duration.ofSeconds(Long.MAX_VALUE)` | `now.minus`/`now.plus` or `iat.plus` overflow and throw `DateTimeException`/`ArithmeticException` out of `validate()`, inside the lock, contradicting the documented "returns a `LogoutTokenCheck`, never throws" contract. Caller-supplied configuration, not attacker input. | minor |
| L3-4 | `LogoutTokenValidator.java:171` with `LogoutTokenReplayGuard.java:111` | a backward wall-clock step after a purge | a purged `jti` whose `iat` is fresh again is admitted a second time. The lane calls it "accepted"; neither the class Javadoc, ADR-0029 Risks nor `security-reference.adoc` states it (content search of all three). | minor |
| L3-5 | `SignatureVerifiedToken.java:51,77` | a caller logs the result | `@ToString` prints `typHeader` verbatim with no `ErrorTextSanitizer`; the value is signature-covered, so only the trusted issuer can inject. Whether `LogRecordSanitizationGuardTest` scopes `toString` is not shown by reading. | minor |
| L3-6 | `LogoutTokenReplayGuard.java:111`, under `decisionLock` `:170` | every admission | `removeIf` over all retained entries while the caller holds `decisionLock`: O(capacity) per validation, serialised across threads. A throughput ceiling, not a correctness defect. | info |
| L3-7 | `SignatureVerifiedToken.java:102-108` | any caller | public constructor retains `rawClaims` by reference; immutability rests on a documented precondition. The type is outside both sealed hierarchies and cannot reach a `TokenContent` consumer. | info |
| L3-8 | `TokenValidator.java:576-578` | a later decode or JWE-decrypt failure on an already validated token | `getRawClaims(TokenContent)` decodes again in normal mode, so a failure there logs a WARN and counts a failure event, and the method does not check the token came from this validator. A consequence of the OD-4 lazy re-parse, documented as per-call cost. | info |
| L3-9 | `MapRepresentation.java:63` | an object serialised by the old record shape | the record gained a component, so `equals`/`hashCode` differ for explicit null versus absent and an old stream fails `requireNonNull` on deserialisation (`serialVersionUID` unchanged); member order is now insertion order. No cached content class retains the record, so the cache footprint is unaffected. Carried from the leaf, not re-read. | info |

**Layer 3 clean points (re-read):** the logout checks fail closed — `typ` absent accepted per OD-8, any other value refused (`:200,282-288`); `iss`/`aud` by equality or list membership (`:203-206,290-295`); `iat` window edges inclusive and `exp` strictly after `now` (`:245-254`); `events` read from the native claims as a `Map` on both levels, no string scan (`:256-258`); `nonce` presence includes a JSON `null` (`:218,264-266`); `sub`/`sid` non-blank strings (`:272-280`); `jti` non-blank (`:224`); the guard is called last (`:180`). The guard is sound by reading: one monitor over purge, lookup, capacity test and insert (`:110-120`), `FULL` is returned instead of evicting a live entry (`:115-117`), only the SHA-256 digest is stored (`:109,123-125`). `verifySignature` leaks no claim verdict, touches no cache and reads the inner JWS payload for a JWE (`TokenValidator:544-557`, `NonValidatingJwtParser:418-423`). The `PROPERTY_CONFIGURED` probe path keeps its output (`JwksEndpointHealthCheck:130-175`).

**Layer 3 not shown by reading:** L3-1/L3-2 — how long either extreme exponent runs, whether `dsl-json` hands the literal over as `BigDecimal`, and whether `OffsetDateTimeMapper` throws first on the same literal (the leaf noted a JDK `longValue` shortcut it did not read). Only a run settles the effect size.

**Layer 3 verdict:** findings — 9 (L3-1..L3-9: 5 minor, 4 info); not shown by reading — 1

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | Javadoc on public types and methods with `@since` and a thread-safety note | conforms | class Javadoc with `@since 1.0` and a thread-safety sentence on all new public types (`SignatureVerifiedToken:44-50`, `LogoutTokenValidator`, `IssuerStatusView`, `LogoutTokenValidationResult`, replay guard). Carried from the leaf, not re-read. |
| spec Standing Constraints | usage example for complex types | deviates | none in `LogoutTokenValidator`, `SignatureVerifiedToken` or the new `TokenValidator` methods (leaf's reading). |
| AGENTS.md | `CuiLogger`/`LogRecord`; no new record unless needed | conforms | no `LogRecord` added; only `LOGGER.debug` in `TokenValidator:545,553`. |
| spec Standing Constraints | remote text only through `ErrorTextSanitizer` | not_shown_by_reading | `SignatureVerifiedToken:51,77` prints `typHeader` raw (L3-5); the guard test's scope was not read. |
| AGENTS.md | narrow catches; no OpenRewrite markers | conforms | `LogoutTokenValidator:158-162,317`; `ClaimMappingException` carries a `java:S110` suppression with a rationale. |
| AGENTS.md | pre-1.0, additive, default behaviour unchanged | conforms | `createAccessToken`/`createIdToken`/`createRefreshToken` flow unchanged by reading; `MapRepresentation` record shape changed (L3-9). |
| AGENTS.md | test classes at or under 500 lines | deviates | see L2-3. |
| `test-impl-mapping.adoc` | every new test class listed | conforms | seven new classes in the diff of that file; `ClientNativeReflectionMetadataTest` (PLAN-01) and `LogoutTokenValidatorTest` rows both present, so the PLAN-01/PLAN-05 collision merged without losing either plan's rows. |
| spec Standing Constraints | native image: pinned reflect-config, no new static pool | conforms | `NativeImageMetadataTest` not in the diff; `LogoutTokenReplayGuard` holds no static state. The pinned run itself is not shown by reading. |
| AGENTS.md | no new dependencies | conforms | the poms add only a `<type>` on an existing dependency. |
| spec Standing Constraints | gate: pre-commit verify, install, integration-tests verify | not_shown_by_reading | whole-tree `verify -Ppre-commit` 4512 green is the lane's claim; the integration-tests verify hit the 302 s timeout (4334 passed, 0 failed) and was treated as covered; a completed integration run and the install step are not shown. |
| spec Standing Constraints | Sonar new-code issues fixed or suppressed | not_shown_by_reading | see Routing. |

## Metrics and Anomalies

- Tokens: 39,315,801 total (the lane reports no per-phase split in the landing; PLAN-01 was 13,310,080).
- Duration: 84,055 s wall (about 23.3 h), against 21,615 s for PLAN-01.
- Anomalies: two full review rounds re-fired by a rebase that changed no plan file (the verdict-currency candidate, now lesson 2026-11-13-06-008); four review rounds on one replay-window family (lesson 2026-11-13-06-002); the self-response-loop guard fired on CodeRabbit's in-place-edited summary comment and the operator accepted it as benign (lesson 2026-11-13-06-007); the integration-tests module verify timed out at 302 s; five `script_failure` clusters (`manage-execution-manifest`, `manage-findings`, `manage-solution-outline`, `manage-status`, `verdict_currency`) that the capture did not classify.

## Routing and Merge Behavior

- Review: `review-gate --pr-number 899` on the merged head returns `unresolved_review_threads: 0` and `review_body_findings_total: 10`, `review_body_findings_handled: 0` with `merge_allowed: false`, `reason: body_findings_undisposed` (nine CodeRabbit `inline-1..9` and `additional-1`). The landing claims `review_body_findings_total=1`, `handled=1`. **`unresolved_review_threads` agrees (0 = 0). `review_body_findings_total` is a false claim: 1 against the provider's 10.** `review_body_findings_handled` is **not re-verified**: the dispositions live in the run's own record, which this analysis did not have, and the provider's `0` reflects that the parser finds no disposition marker on the PR, not that none was given. The lane's own narrative (candidates 005, 009-016) shows at least six CodeRabbit findings answered by fix tasks (`nonce:null`, `exp == now`, malformed `iat`/`exp`, fractional `numericDate`, the clock pairing `d9b039`, the security-reference mechanism `e9b6e2`), which is consistent with most of the ten being handled, but the count was not reproduced. The same shape as PLAN-01 (two undisposed body findings against a narrative that said all answered).
- CI/merge: merged through the merge queue as `fff5befe` (`ci pr view`: `state: merged`). The branch was rebased onto `c23e2a85` (#898) and force-pushed with lease at the operator's overlap-gate answer; the recorded branch-cleanup detail reads "rebase deferred to queue" although the rebase happened (lesson 2026-11-13-06-009). The landing does not say whether the rebase had a conflict; the merged `test-impl-mapping.adoc` carries both plans' rows, so none was lost.
- Sonar: `sonar_open_new_issues=0` claimed; the re-read returns `count_status: undecidable`, `count_status_reason: head_revision_unavailable` (`analysis_revision` is `2c167f4e`, the head the lane saw, not the merge head). Recorded as **unknown, not 0** — the same state as PLAN-01.
- Cost of the collision with PLAN-01: two full review rounds, gate, CI and triage re-ran on the rebased head.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-05 --status shipped`
- [x] row `pr` stamped — `#899`
- [x] row `landing` stamped — `landings/PLAN-05.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-05-validation-public-seams`
- [x] epic.md narrative reconciled (Open Defects: L3-1/L3-2, the incomplete-review-numbers entry for #899, L1-2; Watches: the PLAN-05 `test-impl-mapping.adoc` watch retired, script_failure clusters and the API Sheriff re-measure list added; Decisions: PLAN-05 shipped / inbox drain)
- [x] 23 candidate-lesson messages dispositioned and archived with the landing (see the decision log)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- **L3-1/L3-2 (resource exhaustion in `LogoutTokenValidator.numericDate`), reported to the operator in this turn.** Becomes a staged fix-plan spec, or folds into PLAN-07, only on the operator's word. Both exponent signs, the lock hold and the `freshnessWindow` overflow (L3-3) belong in one fix.
- **The ten undisposed CodeRabbit body findings on PR #899 and the two on PR #898** are the operator's to settle on the PRs.
- **API Sheriff must re-measure on the 0.9.10 bump** (the landing's behaviour changes for a consumer): the `MapRepresentation` record shape and member order (L3-9), the new `SIGNATURE_ONLY_VERIFIED` event type, the opt-in `report-external-validator` and `disclose-issuer` keys (default behaviour unchanged), and the `logout` package contract (`exp == now` refused, fractional `NumericDate` kept, `nonce:null` refused).
- **PLAN-06** inherits a changed neighbourhood: `JwtPropertyKeys` (+27 lines), `quarkus-config-doc.adoc` and `test-impl-mapping.adoc` were edited by this landing; the line anchors in PLAN-06's Expected Surface for them shifted. Its Standing Constraint about `test-compile` carries the stale premise L1-2.
