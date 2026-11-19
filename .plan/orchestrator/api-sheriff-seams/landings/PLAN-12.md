# Landing Analysis: PLAN-12 — Open items sweep

epic: api-sheriff-seams
workstream: WS-02
pr: #925 (https://github.com/cuioss/TokenSheriff/pull/925), merged through the merge queue as ae493e00370ae6da2e715836aad19e80df3a9da1

> Landing record for one shipped plan. Lives at `landings/PLAN-12.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-12-open-items-sweep-020.md` (lane `plan-marshall`, plan id `plan-12-open-items-sweep`), plus nineteen candidate-lesson messages (`-001` to `-019`).
PR state read through `ci pr view` (`state: merged`, merge commit as above). The merge commit's first parent is `e53f3e4f` (a workflow-update merge, #928), so the plan's diff is `git diff e53f3e4f ae493e00` (52 files, +2817/-315, re-counted).
`inbox landing-check`: `complete: true`; `surface_delta` is `unmeasured` (`declared_not_supplied_and_realized_not_supplied`), so the declared-versus-realized comparison below is the orchestrator's own, from the diff.
The `steps` fact was split on its last colon; every element is a bare step id with outcome `done`. The lane's account of its own run was not re-run. The archived plan directory (`2026-11-19-plan-12-open-items-sweep`) was read for `logs/decision.log`, `logs/work.log`, `metrics.md`, `solution_outline.md` and `work/functional-review-verdict.json`, `work/code-review-findings.json`.

## Deliverable Fidelity vs Spec

The spec lists six deliverables; the landing reports `deliverables_total=6` and `deliverables_done=6` (the outline kept six, the operator chose one plan, decision Q9; the plan ran 18 tasks). The nine operator decisions Q1 to Q9 were settled at outline (`solution_outline.md:480-498`, read). The table follows the spec's numbering.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Orphaned-DPoP guard fails closed | **Delivered, shown by reading** | `declaredKeysUnder` takes a minimum and refuses a short result with a fixed message naming class and base (`JwtPropertyKeys.java:116-119`); a `null` field read is a named `IllegalStateException` (`:127-134`); `DPOP_SUB_PROPERTIES` passes `4` (`:461`); all re-read. `IssuerConfigResolverOrphanedDpopTest:39-62` pins the property named (natural sort: `dpop.proof-max-age-seconds` before `dpop.required`) with a valid-config control; re-read. The recorded red proofs are not in the tree and were not re-run. Q1 (native image): the guard alone, as the reason. |
| 2. PLAN-10 leftovers | **Delivered with residuals** | `WireValues.java:67` fixed text, no echo (Q3); `AuthorizationCodeFlow.java:224-258` documents and raises the `IllegalStateException`, `AuthorizationCodeFlowPushedGuardTest:107-135` has the test with a negative control; dedicated `PushedAuthorizationEndpointMissingException` (Q4) raised before any request (`:244`); the recursion hazard is Javadoc-only (Q2); the interrupt-edge text states that the bound does not hold for an interrupted caller (`CachingDiscoveryResolver.java:88-92`) and matches `refresh()` (`:424-431`); `DiscoverySpecIT:94-112` counts delegate calls (one PLAN-04 nitpick fixed, an integration test: not run by reading). The `CachingDiscoveryResolverAdversarialTest` nitpick has no diff; its disposition lives in the PR. Residuals: layer-2 rows below (three new framed test names, ADR/doc compatibility framing). |
| 3. PLAN-11 leftovers | **Delivered with residuals** | Malformed-`Content-Length` tests at the three call sites exist (`ParClientMalformedResponseTest`, `UserInfoClientMalformedResponseTest`, `DiscoveryResolverTest:167`); the catch is narrowed through `sendAsync` (`BackChannelHttp.java:462-500`); the five #921 items are fixed (the lane's functional review graded all five; the 204 case is a pre-redemption `TransportException`, the operator held the line). The first-allocation value, ADR-0031 itemisation and the doc statements: the code-review's advisory says the docs still name only the JDK 26 form. |
| 4. `test-impl-mapping.adoc` rows | **Delivered, shown by reading** | Seven rows match the five named class groups at the merge commit; the second top-level classes `BearerScopeValidationGeneratedBeanControlBootTest` and `DiscoveryResolverSizeCapTest` and every test class the diff adds have a row (a count of each name, re-read: one hit each). The "does `module_mapping_validator` read this document" question is a PR-body item, not shown by the tree. |
| 5. Playwright flake and E2E environment | **Partly delivered; run-only criteria not met** | `start-dev-environment.sh` builds into a run-private repository `$TARGET_DIR/m2-repository` and reads the shared `~/.m2` only as `maven.repo.local.tail` (`:13`, `:21-28`, re-read); at the base `:76` was the bare `mvnw quarkus:dev` (re-read). `goToTokenDebugger` waits for `jwt-debugger-container` (`devui-navigation.js:118-135`, re-read). **The flake was neither reproduced nor shown fixed, the ten runs were not done (operator decision, decision-log entry `505871`), and `start-dev-environment.sh` was never executed**; the plan's functional review grades the criterion met by its own "or the record states why" clause. `self-devui-accessible.spec.js:48` still navigates to the Token Debugger without the new wait. |
| 6. Review-body dispositions | **Delivered by the PR record; not shown by the tree** | Three triage-disposition comments by the lane exist on #925 (read through `ci pr comments`), and the three inline review threads are resolved, each with a lane reply naming its fix commit. Per-PR dispositions for #898 to #912 and #920 were not re-read (the lane's `d6` record was not opened). PR #907's "Files not reviewed due to moderation or processing errors: 8" section is unreadable by the gate (`undecidable_read`, advisory D6.R1). |

Surface delta: the plan declared 41 paths; the diff holds 52 files. The files the spec's declaration did not name and no deliverable states are listed under layer 2.

## Three-Layer Analysis

**Dispatch.** One read-only leaf (`execution-context-level-3`, resolved role `orchestrator.analyze`, source `plan.effort`; the `[DISPATCH]` record written by the resolve) read merged `ae493e00` against the spec's body, which the orchestrator passed as data. Reason: reading merged code is the depth the Dispatch Decision Rule exists for. The leaf was told to write nothing and to create no scratch files (PLAN-10's leaf had); it reported none. It misprinted one path (a doubled `token-sheriff-quarkus-parent/` prefix on the `TokenSheriffProcessor` finding at `:188`); that point was re-read under the corrected path. Return: 26 findings and 12 conformance rows. **The orchestrator re-read every `checked` point it records below at its `file:line` at the merge commit (or the base commit where marked)**, and says where it did not.

### Layer 1 — Specified

Verdict: premises mostly sound; the native-image premise was partly wrong; four Done-when conditions cannot be settled by reading.

| Claim | Reading | Persisted verdict |
|-------|---------|-------------------|
| OBSERVED mapping rows missing for five classes | Confirmed: the base had none, the merged document has a row each (re-read). | `corroborated` (claim 7) |
| OBSERVED `start-dev-environment.sh:76` runs `mvnw quarkus:dev` with no install | Confirmed at the base (re-read). | `corroborated` (claim 8) |
| HYPOTHESIS `DPOP_SUB_PROPERTIES` can be empty in a native image | **Partly refuted:** the explicit `ReflectiveClassBuildItem` for `ISSUERS` with fields already existed at the base (`TokenSheriffProcessor.java:186-189` re-read: "without this the list would be empty"), so the list is empty only if that registration is lost; whether it is non-empty in a native executable is not shown by reading, and the Javadoc's "in a native image alike" is unshown. The merged tree turns the empty case into an initialisation failure. | `unverifiable` (claim 9) |
| HYPOTHESIS no malformed-`Content-Length` tests at `ParClient`, `UserInfoClient`, `DiscoveryResolver` | Confirmed at the base (`ParClientTest:316` and `UserInfoClientTest:338` test `Content-Type` line breaks only; re-read). | `corroborated` (claim 10) |
| HYPOTHESIS the `send` catch width misattributes a request-side fault | Confirmed at the base (`catch (IOException \| IllegalArgumentException)` around `client.send`, base `:406`, re-read); that the JDK throws the request-side `IllegalArgumentException` synchronously from `sendAsync` rests on the `HttpClient` Javadoc quoted in the code and was not read. | `corroborated` (claim 11) |

Not shown by reading (the spec's own Done-when): the recorded red proofs, the ten Playwright passes, the Sonar read (`undecidable` for a merged PR), the `ClientConfigurationTest` 52 to 55 acceptance in the PR body, and the deliverable 6 dispositions on the older PRs. The spec also set a "no test class grows past 500 lines" limit while naming `TokenEndpointClientTest` (843 lines at the base) in its own Expected Surface, so the limit could not hold for a class the spec told the plan to edit (layer-2 row).

### Layer 2 — Implemented

Verdict: every deliverable but 5 and 6 traces to code and a test that can fail; five deviations.

- **Framed test names remain (minor, confirmed).** `BackChannelHttpRequestRefusalTest.java:163` `shouldPropagateRequestRefusalUnchanged` and `:181` `shouldStillMapMalformedResponseToFixedFailure`, and `BearerTokenProducerTest.java:264` `resultUnchangedAfterCallerClearsRequirementSets` with a DisplayName "Should keep the result unchanged..." (all re-read). The four names the spec listed were renamed.
- **Grown test classes over 500 lines (minor, confirmed).** `TokenEndpointClientTest` 843 to 903 and `BearerTokenProducerTest` 559 to 607 (counted at both commits); the PR-body reason `AGENTS.md` asks for was not read. New classes are within the limit (largest `BackChannelHttpRequestRefusalTest` 380, `BearerScopeValidationBuildStepTest` 496 after growth, counted).
- **Second top-level class in a test file (minor, confirmed).** `BearerScopeValidationGeneratedBeanControlBootTest` sits in `BearerScopeValidationGeneratedBeanBootTest.java:139` (re-read); it has a mapping row but no file of its own, and a file-based fork-group or tag guard may not see it (not shown).
- **Doc drift on the catch (minor, confirmed).** `doc/commons/requirements.adoc:196` ("each catches that `IllegalArgumentException`") and ADR-0031 `:335` ("map the JDK's `IllegalArgumentException`") describe the old mechanism; the code maps any exchange-future failure `isMalformedFraming` recognises, including the JDK 27 `ProtocolException` chain (`BackChannelHttp.java:161-176`). ADR-0031 `:196` "Before, the JDK's `IllegalArgumentException` ... escaped the call" is history framing (all re-read).
- **Compatibility framing (minor, confirmed).** The `PushedAuthorizationEndpointMissingException` class comment (`:30-37`), `doc/client/requirements.adoc:201` and `retrieval-flow.adoc:115` justify the subtype by unchanged existing catches, against the pre-1.0 "never enforce backward compatibility" rule (all re-read); behaviour is fine.
- **`AGENTS.md:159` drift (info, confirmed).** It still says `INFO (001-099)` while the plan changed the Javadoc and pinned unpadded identifiers; not touched by the diff.
- **Files no listed deliverable states, judged:** `doc/client/specification/test-strategy.adoc` (operator-sanctioned wording fix, on the plan's own scope-creep list); `doc/client/requirements.adoc` logout-endpoint wording (`allowInsecureHttp`; the diff re-read, and it matches `EndpointSchemePolicy.java:90-94`; **not on the plan's scope-creep list and in no deliverable**); `BearerTokenProducer` and its test, `EgressHostValidator` (`Refusal` enum) and four tests, `BearerTokenResponseFactoryUnrepresentableScopesTest`, `TokenSheriffProcessor` with two deployment test classes (all explained by the five #921 items and the two CodeRabbit fix tasks, operator-chosen); `ParClientMalformedResponseTest`, `UserInfoClientMalformedResponseTest` (deliverable 3).
- **Not shown by reading:** `CachingDiscoveryResolverAdversarialTest` nitpick (no diff), `DiscoverySpecIT` (needs a run), the Playwright root cause, and that the `ClientConfiguration*Test` split acceptance (outside this diff).

### Layer 3 — Correct

Verdict: no blocker and no major defect found by reading; three minor and two unshown points. **Reported to the operator in the same turn; nothing is fixed inline and nothing is staged.**

- **Minor — `isMalformedFraming` accepts any `IllegalArgumentException` of the exchange future** (`BackChannelHttp.java:162-164`, re-read): a response-side `IllegalArgumentException` from a body handler or the client's internals would read as "malformed response" with its cause dropped. The Javadoc states the request-side limit (`:403-407`) but not this residual width; the spec asked to narrow or state the limit, and the narrowing is partial.
- **Minor — `CancellationException` is not mapped** (`BackChannelHttp.java:473-499`, re-read: only `InterruptedException` and `ExecutionException` are caught): a future cancelled by client shutdown escapes `pending.get()` as an unchecked exception. Whether a caller handles it was not read.
- **Minor — package-level `@Vetoed` is not read** (`TokenSheriffProcessor.java:447-450`, re-read: `declaredAnnotation(VETOED)` on the class only), so a concrete class in a `@Vetoed` package is a bean candidate and its class-level scopes are merged; known as advisory D3.R4. Whether the Javadoc states the limit was not re-read.
- **Not shown — a request-side `IllegalArgumentException` now escapes `TokenEndpointClient` raw** (`BackChannelHttp.java:462` declares it); nearly unreachable (requests come from the builder), and whether the refresh path's quarantine classification handles an untyped `RuntimeException` was not read.
- **Not shown — timeouts:** `pending.get()` is unbounded and relies on the request's own timeout from the handler's request builder, as `client.send` did; that `cui-http` sets it was not read.
- **Confirmed sound (re-read):** interrupt handling in `exchange()` (`:469-478`): the pre-check clears the flag and throws, a wait interrupt cancels the future and rethrows, and the callers restore the flag (the leaf named five call sites; not each re-read); the composite scan removes duplicates through a `TreeSet` (`TokenSheriffProcessor.java:366-367`) and the `@Vetoed`/abstract/interface exclusion is in one predicate (`:447-450`); the DPoP guard messages carry no value and the guard cannot leave `ISSUERS` uninitialisable for a valid configuration on the JVM by reading (every `String` constant is a compile-time constant; the leaf's claim, `:116-134` re-read, the constant-expression point not exhaustively checked).

### Conformance

| Rule source | Rule | Verdict |
|-------------|------|---------|
| `AGENTS.md` Pre-1.0 | no `@Deprecated`, no transitional comments | conforms |
| `AGENTS.md` Pre-1.0 and spec | no history-framed wording incl. test method names and ADR text | **deviates** (layer 2: three test names, ADR-0031 `:196`, the compatibility framing) |
| `AGENTS.md` Test Class Size | created or grown class at or under 500 lines, else the PR body gives the reason | **deviates** (`TokenEndpointClientTest`, `BearerTokenProducerTest`; the reason not read) |
| `AGENTS.md` Test Class Size | splits are pure moves | not shown by reading (no `ClientConfiguration*Test` in the diff) |
| junit-core / `AGENTS.md` | JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized for three or more | conforms |
| `AGENTS.md` Testing / spec | injected time, a negative control per regression test | conforms; the "fails without the change" red proofs are not shown |
| cui-logging | `CuiLogger`, `LogRecord` for INFO and above, identifiers documented | conforms (no new `LogRecord`; one fixed-text DEBUG line) |
| ADR-0022 / spec security | attacker text sanitised, refusal texts fixed and never echoing | conforms (`WireValues` fixed text; the malformed-response mapping neither chains nor echoes) |
| Spec / `AGENTS.md` native image | helpers plain classes, no reflect-config entry beyond deliverable 1, no new dependency | conforms |
| Spec Standing Constraints | default behaviour unchanged (`BoundedBodyHandler`, `BearerChallenge`) | not shown by reading (untouched by the diff; the named tests were not run) |
| `InvalidExceptionUsageRecipe` | narrow catches, no new broad catch | conforms |
| Javadoc Standards | public types with `@since`, `@throws` for new exceptions | conforms |

## Metrics / Anomalies

| Fact | Value | Source |
|------|-------|--------|
| Tokens | 41,296,404 (5-execute 27,697,310, 6-finalize 8,354,466, 3-outline 2,637,841) | landing and `metrics.md`, read: **matches** |
| Wall clock | 58,208 s reported (16h10m; 5-execute 6h35m reported with 6h02m idle, 6-finalize 8h38m with 7h16m idle) | landing and `metrics.md`, read |
| Model / effort | `claude-sonnet-5-5`, `medium` | landing facts |
| Plugin / executor | 0.1.2133 both | landing facts |
| Finalize | three `loop_back` step records in `decision.log` (`functional-review` 2, `code-review` 1, counted); the lane's functional review ran four-plus rounds | `decision.log` |
| `script_failure` lines | four `[ERROR]` classes in `work.log` (`get-deliverable --number` ×2 at `:68`, `:149`; `manage-change-ledger append` at `:352`; the `no_sanction` commit at `:574`), plus seven `--changed-paths-file` `[ERROR]` lines (`:112, :297, :339, :486, :606, :636, :693`) | `work.log`, counted |
| Rebase | one, in branch cleanup, after the PR was review-clean (`decision.log:255`: `queue_wait=forbidden` overrides the skip), conflict with #926 resolved by an agent with the operator's authorization; every plan commit SHA was rewritten and the change ledger was not remapped (lesson 2026-11-19-07-007) | `decision.log` |

Anomalies:

- **The review gate and the plan's record disagree.** `review-gate --pr-number 925` returns `merge_allowed: false`, `body_findings_undisposed`, total 5, handled 0; the lane's own landing says the same. The lane replied to the five (two in one batched comment declining both; three inline threads replied to with fix commits and resolved; the thread replies read, the fixes themselves not all re-read), and the gate does not read those replies. The merge path's own gates were clean (0 pending findings, `participation_gate: complete`), per the lane. Recorded as measured, not as a false claim.
- **A seven-hour gap between review-clean (22:46Z on 2026-11-18) and branch cleanup (05:21Z) let `main` move** (#926, #927, #928); the conflict cost one rebase and a full second finalize pass (the lane's candidate `-017`). It is the PR-wait problem of lesson 2026-11-18-13-001, not a defect of the plan; the epic's own ledger lands (#922 to #924) and unrelated CI updates moved `main`, so the plan's declared-surface overlap prediction was not wrong.
- **Estimated figures were added to the add-only usage accumulator** before the measured reports (lesson 2026-11-19-07-009); the plan's totals above match the landing, and the tool-use and duration totals are known to be slightly high.
- The lane's own corrections to four candidate lessons (`-005`, `-012`, `-015`, `-016`) were applied (see the lesson bodies).

## Routing / Merge Behavior

| Fact | Landing says | Provider re-read |
|------|--------------|------------------|
| PR / merge | #925, `merged`, queue merge `ae493e00` | `ci pr view`: `state: merged`, merge commit `ae493e00`, head branch `feature/plan-12-open-items-sweep`; **confirmed** |
| `unresolved_review_threads` | `0` | `review-gate`: `0`; **no false claim** |
| `review_body_findings_total` / `_handled` | `5` / `0` | `review-gate`: total `5` (coderabbit `additional-1`, `additional-2`, `inline-1`, `inline-2`, `inline-3`), handled `0`, `merge_allowed: false`; **no false claim**; the replies exist on the PR (read) but the gate does not read them |
| `sonar_open_new_issues` | `0` (the last scanned head `33e68ac0`, security hotspots not read) | `sonar read_open_issues`: `undecidable` (`head_revision_unavailable`, analysis revision `33e68ac0`, **the revision matches the lane's**); the count is recorded **unknown**, the lane's `0` is its own |
| Merge path | merge queue | `decision.log:274-277`: merged by the platform merge queue after about 500 s (read) |
| Post-merge benchmark | still running at cleanup end, not awaited (lane) | not re-read |

## Reconciliation

- Queue: PLAN-12 `running` → `shipped`; `pr` `#925`; `landing` `landings/PLAN-12.md`; `plan_marshall_plan_id` `plan-12-open-items-sweep` (carried by the landing message).
- Parallelization: nothing ran beside it; no overlap with a running plan was predicted or observed. The only collision was with an unrelated merged PR (#926) on `BackChannelHttp` and `TokenEndpointClient`, which the gate could not see because it compares epic plans, not the whole repository.
- Queue state: PLAN-01 to PLAN-12 are all `shipped`; nothing is staged and nothing is running, so the queue is empty.
- Candidate lessons: nine promoted (2026-11-19-07-001 to -009), three recurrences added to existing lessons, six absorbed into watches, one discarded (see the Decisions entry in `epic.md`).
- `epic.md`: the PLAN-12 Queue annotation, the Decisions entries, the Open Defects entry "PLAN-12 landed" and the extended Watches were written with this landing.

## Follow-ups

None is staged: the operator's word is needed for any new plan, and the queue is empty. Items carried to `epic.md` Open Defects: the Playwright flake and the never-run start script (D5); the three minor layer-3 points and the two unshown ones; the framed test names, the two oversize test classes and the second top-level class; the doc drift on the catch and ADR-0031's history framing; the code-review's eleven advisories (all unfixed); the two declined review items (a generated mapping inventory; the hard-coded minimum of four); the JDK 27 documentation lag (D3.R5); the unreadable #907 review-body section (D6.R1).
