# Landing Analysis: PLAN-17 — Javadoc usage examples that compile and run

epic: quality-report
workstream: WS-05
pr: #878 (merge `d0fa0c7f180094708f76f6401949b0ce4b2b08e8`, merged 2026-11-06T20:27:02Z)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after verifying the landing message
> against git, the PR and the merged source. The inbox landing was `plan-17-javadoc-examples-007.md`; its `landing-check`
> reported `complete: false` (missing key `effort`, written as `unknown` by the producer), so the landing is reconciled as
> far as it goes and recorded as an Open Defect (thirteenth producer of an unstamped effort).

## Deliverable Fidelity vs Spec

Corroborated against the real merge diff (`d0fa0c7f`, 19 files, +198/-162, no `src/test` path), the PR state read through
the CI abstraction (state `merged`, 24 checks, none failing; CodeRabbit `NEUTRAL`, Sourcery `SKIPPED`) and the merged source.
The lane decomposed the spec's five deliverables into three of its own (`deliverables_total=3`); the diff covers all five.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. J-1 / J-4 / J-6 / J-9 `IssuerConfig` and `TokenValidator` examples | shipped-as-specified | `IssuerConfig` and `TokenValidator` hunks in the diff; `initJWKSLoader` on `IssuerConfig`, `SignatureAlgorithmPreferences.builder()` and "blacklisting" are gone from main code (`git grep` finds "blacklist" only at `doc/validation/threat-model.adoc:66`, outside the diff). |
| 2. J-2 / J-7 `JwksLoaderFactory` example, `jwks/http/package-info.java` | shipped-modified | Example uses `createHttpLoader(HttpJwksLoaderConfig)` and `initJWKSLoader`; `package-info` rewritten around `HttpJwksLoader` (131 lines changed). The class-level feature bullets of `JwksLoaderFactory` (lines 30-31) were left stale and now sit beside the new bullet at line 32. |
| 3. J-3 `NonValidatingJwtParser` examples | shipped-modified | `DecodedJwt decode(String)` and `rawToken()` used. Prose about the size pre-check and `IllegalArgumentException` was added beyond the spec; it is where two of the findings below sit. |
| 4. J-5 / J-10 `DiscoverableTokenValidationRule` claim access; cookie extraction docs | shipped-modified | Custom claim read through `getClaims()`; `BearerTokenProducer`, `BearerToken`, `BearerAuth` document the Authorization-first, cookie-fallback order and the property keys. Two AsciiDoc guides carried the same stale example and were fixed in the same PR (added-unplanned, see layer 2). `BearerTokenProducer.produceBearerTokenResult` (line 376) and `BearerTokenStatus` were left saying Authorization only. |
| 5. J-11 / J-12 `@since`, `StringSplitterMapper` parameter name, "Portal environment" | shipped-as-specified | `@since 1.0` added to the five classes (`TokenSheriffClientProcessor`, `TokenSheriffProcessor`, `TestApplication`, `JwtValidationEndpoint`, `MockJwtValidationEndpoint`); the `StringSplitterMapper` change is the Javadoc example variable only. |

Added-unplanned (accurate, explained by the finding family): `JwtPropertyKeys.java` (J-9 "blacklisting", J-10 cookie keys), `token-sheriff-validation/doc/developing.adoc` and `token-sheriff-quarkus-parent/doc/integration/quarkus-integration.adoc` (the same stale `ClaimName.of` example as J-5).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, resolved on the `orchestrator.analyze` surface, source `plan.effort`) — reason: reading the merged Javadoc claims against the implementing branches and signatures is the depth the dispatch rule exists for, and this is the only landing in the drain. The leaf wrote nothing. The orchestrator re-read at `file:line` against `main` (`d0fa0c7f`): `NonValidatingJwtParser.java:52-55,105-110,206-212,228-234,290-297`, `TokenStringValidator.java:52-67,96-101`, `ParserConfig.java:85`, `JweDecryptionConfig.java:51`, `RefreshTokenValidationPipeline.java:90-104`, `BearerTokenProducer.java:196-222,248-256,374-378`, `BearerTokenStatus.java:39-42,49-52,90-92`, `DiscoverableTokenValidationRule.java:52-56`, `JwksLoaderFactory.java:28-34`, `JwtPropertyKeys.java:175-179,938`, `IssuerConfig.java:747-757`, `SignatureTemplateManager.java:81-104`, `doc/validation/threat-model.adoc:66`, a `git grep` for "blacklist", and a grep showing `TokenValidationRuleTest` never reads `getClaims()` or `tenant_id` and no test mentions `maxEncryptedTokenSize`. Not re-read, so recorded as leads: the example-versus-signature check across the nine example sites (the leaf's "checked (c)" row), `BearerToken.java:37-40,105-110`, `BearerTokenProducerTest.java:279-307,378-396`, `StringSplitterMapper.java:74-86` with `MapRepresentation.java:108-111`, `HttpJwksLoader.java` line numbers, and the `NonValidatingJwtParserTest` coverage statement. One leaf statement was corrected on re-read: the leaf said `getPreferredAlgorithms` has no production caller; it has one (`SignatureTemplateManager.java:86`) but that loop fills `HashMap`s, so the order still has no effect (L3-8 stands on that ground).
- **Verdict:** layer 1 findings (1 minor, 2 info); layer 2 findings (2 minor, 1 not shown by reading); layer 3 findings (1 major, 8 minor, 1 info, 2 not shown by reading).

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 | Expected Surface listed 16 paths; the diff touches 19 (all 16 touched; 3 not declared: `JwtPropertyKeys.java`, `developing.adoc`, `quarkus-integration.adoc`). The gate under-declared the same finding family (J-9, J-10, J-5) for the third time in this epic | checked | `git show --stat d0fa0c7f`; `plans/PLAN-17-javadoc-examples.md` Expected Surface |
| L1-2 | The spec has no Done-when section and frames the work as examples, yet the lane also rewrote behaviour prose (cookie precedence, JSON null, retired key sets, 304 handling). The behaviour prose is where review and the layer-3 reading found errors | checked | spec section list; landing message residue |
| L1-3 | The spec's eight claim labels (nonexistent `initJWKSLoader`, `createHttpLoader` arity, `Optional<DecodedJwt>`, no `SignatureAlgorithmPreferences.builder()`, no `ClaimName.of`, mandatory `issuerIdentifier`, retired `package-info` classes, `HttpJwksLoader` surface) all hold at the pre-merge tree | checked | leaf, `HttpJwksLoader.java:253` and the signatures cited at layer 3 (leaf; not re-read) |

PLAN-17 is shipped, so no staged spec carries these claims and `corpus set-verdict` persists nothing.

**Layer 1 verdict:** findings — 1 minor (L1-1), 2 info

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 (J-1/J-4/J-6/J-9) | `IssuerConfig.java`, `TokenValidator.java` | none (Javadoc only; `{@snippet}` bodies are not compiled) | none | checked |
| 2 (J-2/J-7) | `JwksLoaderFactory.java`, `jwks/http/package-info.java` | none | none | checked |
| 3 (J-3) | `NonValidatingJwtParser.java:54-118` | `NonValidatingJwtParserTest` is named by the Javadoc as exercising the examples; per the leaf it pins invalid-format tokens but no null/empty/oversized `IllegalArgumentException` and no JSON-null payload (lead, not re-read) | `NonValidatingJwtParser.java:54` "exercised by `NonValidatingJwtParserTest`" overclaims (L2-1) | not_shown_by_reading |
| 4 (J-5/J-10) | `DiscoverableTokenValidationRule.java:52-56`, `BearerTokenProducer`, `BearerToken`, `BearerAuth` | `BearerTokenProducerTest` pins the precedence (leaf, not re-read); `TokenValidationRuleTest` does not read `getClaims()` or `tenant_id` (re-read) | `DiscoverableTokenValidationRule.java:54` "exercised by `TokenValidationRuleTest`" overclaims (L2-2) | checked |
| 5 (J-11/J-12) | the five classes' `@since 1.0` | none | none | checked |
| lane report | "no behaviour change, no new tests": no `src/test` path in the diff (`git show --stat`); the main-code hunks are comments, `package-info` and AsciiDoc (leaf: no executable line) | n/a | the lane's `deliverables_total=3` understates the five spec deliverables | checked |

**Layer 2 verdict:** findings — 2 minor (L2-1, L2-2), 1 not shown by reading (deliverable 3's test coverage)

### Layer 3 — Correct

The Javadoc and AsciiDoc were read as claims against the implementing code. The rewritten examples match the real signatures (leaf, not re-read), the Authorization-first / cookie-fallback statements match `BearerTokenProducer.java:196-222`, and the `StringSplitterMapper` JSON-null statement matches the mapper (leaf, not re-read). One real defect surfaced while documenting `NonValidatingJwtParser`; it pre-dates the PR and is reported to the operator at once.

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `NonValidatingJwtParser.java:294-295`, with `TokenStringValidator.java:65-67,96` and `ParserConfig.java:85` | JWE configured (`maxEncryptedTokenSize` default 32768, `JweDecryptionConfig.java:51`) and a token string of 8193..32768 bytes (any shape: 5-part JWE, opaque refresh token, garbage) | `TokenStringValidator` admits it (limit 32 KiB), then `decodeInternal` fails `Preconditions.checkArgument(... <= config.getMaxTokenSize())` (parser limit 8 KiB) with `IllegalArgumentException`, not `TokenValidationException`; no WARN and no security event. `RefreshTokenValidationPipeline.java:100` and `BearerTokenProducer.java:252` catch only `TokenValidationException`, so the exception escapes to the caller (the REST-edge status is not shown by reading). The token is still rejected (fail-closed), but every legitimate JWE above 8 KiB is unusable although `JwtPropertyKeys.java:938` documents 32 KB, and no test pairs `maxEncryptedTokenSize` with an oversized token (re-read). Pre-existing, not introduced by #878 | major |
| L3-2 | `NonValidatingJwtParser.java:107` | n/a (prose) | Follow-up confirmed: the text scopes the size pre-check to "ID-token parsing" though all three pipelines run `TokenStringValidator` first, and the new "its limit can differ" presents the L3-1 mismatch as benign | minor |
| L3-3 | `NonValidatingJwtParser.java:208-212,230-234` | n/a | `decode()` and `decodeOpaqueToken()` declare only `@throws TokenValidationException`, while the class text now promises `IllegalArgumentException` for null, empty or oversized input (code `:292-295` agrees with the class text) | minor |
| L3-4 | `BearerTokenProducer.java:376` | n/a | Follow-up confirmed: `produceBearerTokenResult` still says the token comes from the Authorization header only; the code (`:198-203`) falls back to the cookie when `token.header=Cookie` | minor |
| L3-5 | `BearerTokenStatus.java:41,50,91` | n/a | Not on the lane's follow-up list: `FULLY_VERIFIED`, `NO_TOKEN_GIVEN` and `INVALID_REQUEST` speak only of the Authorization header; a blank cookie value also gives `INVALID_REQUEST` (`BearerTokenProducer.java:217-221`) | minor |
| L3-6 | `JwksLoaderFactory.java:30-31` | n/a | Follow-up confirmed: "Creates appropriate loader based on the JWKS URL" and "Supports HTTP and file-based JWKS sources" do not match the three explicit methods (`createHttpLoader`, `createFileLoader`, `createInMemoryLoader`); the new bullet at `:32` contradicts the first | minor |
| L3-7 | `JwtPropertyKeys.java:176` | n/a | Follow-up confirmed: "preferred and allowed" though `SignatureAlgorithmPreferences` has one list plus the rejected set; the sentence right below (`:178`) now says "explicit set" | minor |
| L3-8 | `IssuerConfig.java:749-750,756` | n/a | New text says algorithms are accepted "in order of preference" and "in its declared preference order"; `isSupported` tests membership only and the sole production reader of the list (`SignatureTemplateManager.java:86`) fills `HashMap`s, so the order changes nothing | minor |
| L3-9 | `doc/validation/threat-model.adoc:66` | n/a | Follow-up confirmed: "Token blacklisting for revoked tokens" keeps the word; terminology only, and the last "blacklist" outside `.plan/` (re-read by `git grep`). The file is in PLAN-19's declared surface | info |

**Layer 3 not shown by reading:**

- L3-N1: the statement (`BearerTokenProducer.java:59`, `BearerToken.java:37-40,105-110`) that `Authorization: Bearer ` with an empty token gives `INVALID_REQUEST` with no cookie fallback holds in the code and the mock-resolver tests, but HTTP servers normally strip trailing whitespace, so a real request may arrive as `Bearer` and fall back to the cookie or `NO_TOKEN_GIVEN`; only a run against the real Vert.x resolver settles it.
- L3-N2: the `{@snippet}` bodies are not compiled by javac or javadoc, so "compiles" rests on reading alone and nothing guards later drift; the whole-tree `verify -Ppre-commit` (3852 tests) and the doclint/render result cannot be checked here.

**Layer 3 verdict:** findings — 1 major (L3-1), 8 minor, 1 info; 2 not shown by reading

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | Javadoc: document public methods with parameters, returns and exceptions | deviates | `NonValidatingJwtParser.decode` and `decodeOpaqueToken` omit the `IllegalArgumentException` the class text documents (L3-3) |
| AGENTS.md | Javadoc: include `@since` | conforms | five classes gain `@since 1.0` |
| AGENTS.md | Javadoc: usage examples for complex classes | conforms | examples match the real signatures (leaf; not re-read) |
| CLAUDE.md / AGENTS.md | Javadoc accuracy: prose states only what the code does | deviates | L3-4 to L3-8, plus the two test-coverage overclaims (L2-1, L2-2) |
| AGENTS.md | Logging: CuiLogger, `%s` placeholders | conforms | the diff changes no production logging; snippet calls use `%s` (leaf) |
| AGENTS.md / JSpecify | Null-safety contracts unchanged | conforms | no code change; the `StringSplitterMapper` null-return text matches `ClaimMapper.map` (leaf) |
| CLAUDE.md | Fail-closed posture / no behaviour change | conforms | no executable line changed; the L3-1 defect exists before the change and still rejects the token |
| CLAUDE.md | Whole-tree verify and doclint | not_shown_by_reading | only a run confirms the build and the snippet rendering |

## Metrics and Anomalies

- Tokens: 22,258,542 (mixed population; includes a known double-count of roughly 186k execute-phase tokens, noted in the lane's decision log). Per-phase outliers not supplied.
- Duration: 30,387 s wall clock (about 8.4 h), the longest landing of the epic so far; most of it is review-bot waiting and loop-backs.
- Effort: `unknown` (no effort stamp on the plan) — drain-completeness `complete: false`, missing key `effort`; Open Defect recorded.
- Anomalies: `boundary_monotonicity` flagged `6-finalize` in the metrics report; several phase subagents moved the plan status by themselves and skipped the orchestrator's Q-gates and `phase-boundary` stamp each time (candidate-lesson 002); an inline review-fix commit (`b252595f`) wrote no change-ledger row so the `5-execute` to `6-finalize` transition refused with `execute_commits_unrecorded` (candidate-lesson 003); an empty-index `git-workflow commit` was classified FETCH_FAILURE (003); a whole-tree `test-compile` is not performable while the validation test-jar is made at `package` (004); deviations from the written finalize procedure (one whole-tree `verify -Ppre-commit` instead of the per-module sweep, full review rounds instead of deltas, lessons-capture inline, PR body carries the attribution footer although the template says none).

## Routing and Merge Behavior

- Review: the first code review found two blocking precedence errors; CodeRabbit then raised eleven comments of which ten needed a fix commit (`b252595f` precedence, `17f564db` empty `Bearer`, `3b1e11e5` null, 304, loader join). CodeRabbit refused the PR on quota once; with `review_rate_window_await=false` the step had no recovery path and the operator-side orchestrator waited about 540 s and re-triggered by hand (candidate-lesson 005). The final CodeRabbit check state is `NEUTRAL`.
- CI/merge: all 24 checks green or skipped; squash merge through the merge queue (`step.branch-cleanup.merge_mechanism=merge_queue`), commit `d0fa0c7f`. No rebase conflict or re-verify signal reported.
- Sonar: `sonar-roundtrip:done`; the landing carries no `step.sonar-roundtrip.open_issue_count`, so the figure is `unknown` for this analysis; the `sonar-build` job passed on the PR. Not an anomaly (no measured non-zero count against a clean claim).
- Parallelization: PLAN-17 ran beside PLAN-19 (running). No collision reported; PLAN-17's three undeclared files (L1-1) do not intersect PLAN-19's declared surface except through `doc/validation/threat-model.adoc:66`, which PLAN-17 did not touch.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-17 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-17 --field pr --value 878`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-17 --field landing --value landings/PLAN-17.md`
- [x] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-17 --field plan_marshall_plan_id --value plan-17-javadoc-examples`
- [x] epic.md narrative reconciled (Decision entry, Open Defect for the missing `effort`, Watch for the 2026-11-06 PLAN-17 residue)
- [x] `findings-disposition.md`: J-1..J-7, J-9..J-12 marked FIXED (#878 `d0fa0c7f`) with the residuals pointing to PLAN-25 item 7(k)
- [x] PLAN-25 item 7(k) added (L3-1 JWE size mismatch with its fail-before test; L3-2..L3-9 doc residuals; L2-1, L2-2); Expected Surface extended in the same act
- [x] seven inbox messages archived under `inbox/archive/plan-17-javadoc-examples/`
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- L3-1 (JWE 8..32 KiB size mismatch, major, pre-existing, fail-closed but an unhandled `IllegalArgumentException`): reported to the operator at once and folded into PLAN-25 item 7(k) with a required test that fails before and passes after (a 5-part token of about 10 KiB under a JWE-enabled issuer, through `TokenValidator`, `RefreshTokenValidationPipeline` and `BearerTokenProducer`). It becomes its own fix-plan only on the operator's word; the recommendation is a separate plan because it is the only behavioural change in the epic's remaining work and PLAN-25 is already above six deliverables.
- L3-2..L3-9, L2-1, L2-2 (Javadoc residuals): folded into PLAN-25 item 7(k); L3-9 (`threat-model.adoc:66` "blacklisting") belongs to PLAN-19's file and is folded into 7(k) because PLAN-19 is already running.
- Candidate-lessons 001..005 and the 2026-11-06 PLAN-17 process residue: discarded as plan-marshall-owned and appended to the upstream report as the sixth 2026-11-06 addendum (not sent); 001's project-owned half (read the branch before writing behaviour prose) is carried by 7(k).
