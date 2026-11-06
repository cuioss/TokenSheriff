# Landing Analysis: PLAN-24 — Strict typing for the `client_id` fallback and the other identifier claims

epic: quality-report
workstream: WS-01
pr: #876 (merge `90f8474a2ebb77652141d341edcb3783e07478da`, merged 2026-11-06T16:42:18Z)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after verifying the landing message
> against git, the PR and Sonar. The inbox landing was `plan-24-client-id-strict-typing-005.md`; its `landing-check`
> reported `complete: true`.

## Deliverable Fidelity vs Spec

Corroborated against the real merge diff (`90f8474a~1..90f8474a`, 6 files, +188/-35), the PR state read through `gh`
(state `MERGED`, 24 checks, none failing) and the merged source.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `client_id` strict mapping | shipped-modified | `ClaimName.java:179` maps `CLIENT_ID` through `new StrictStringMapper()`, the mapper `AUTHORIZED_PARTY` uses at line 166; no new mapper, log record or event type. Tests: `AuthorizedPartyValidatorTest` (`shouldRejectNonStringClientIdAtBuild`, line ~272, four JSON types x two fallback settings; `shouldRejectNonStringClientIdBesideMatchingStringAzp`, line ~290) and `ClaimNameTest` (mapper pin and rejection rows). Modified: the spec said "an `azp`-present token is unchanged", but mapping `CLIENT_ID` strictly makes `TokenBuilder.extractClaims` reject a non-string `client_id` even beside a valid `azp`, in both fallback settings and for ID tokens. The lane saw it, tested it, and states it in the PR's "Narrowed acceptance" paragraph. |
| 2. Identifier-claim sweep | shipped-as-specified (docs only) | The spec allows "record that in one sentence and add no code". `security-reference.adoc` lines 35 (`sub`), 46 (`client_id`) and 47 (`jti`) were written. `iss` was already rejected by issuer resolution. The `sub` sentence is overstated, see Layer 2. |
| 3. Tighten the PLAN-11 `azp` assertions | shipped-as-specified | `AuthorizedPartyValidatorTest` azp test (line ~233) now asserts the build-time rejection, the `MISSING_CLAIM` event type, count 1 and `AZP_MISMATCH` count 0. The sibling azp test in `AudienceValidatorTest` (line ~297) still asserts only the supertype; that is outside the spec's wording. |

Added-unplanned: `AudienceValidatorTest.java`, a mechanical call-site migration (renamed helper, new `buildToken(..., counter)`
signature, `buildAndValidate` made an instance method). Needed, not stray; the spec's Expected Surface omitted it.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, resolved on the `orchestrator.analyze` surface) — reason: reading merged code and the spec is the depth the dispatch rule exists for, and this is the only landing in the drain. The leaf wrote nothing. The orchestrator re-read every `checked` point at its `file:line` against `90f8474a` before recording it: `ClaimName.java:164-181`, `MapRepresentation.java:104-116`, `TokenBuilder.java:138-153` and `172-182`, `TokenLifecycleManager.java:1268-1276`, `ClaimName.java` lines 44, 52 and 95 (the three `IdentityMapper` claims), `security-reference.adoc:33-48`, and the three test methods in `AuthorizedPartyValidatorTest`. Not re-read, so recorded as leads: the leaf's by-reading claim that the new rows fail under `IdentityMapper`, and ID-token impact beyond build-time rejection.
- **Verdict:** layer 1 findings (3 minor); layer 2 findings (3 minor, 1 not shown by reading); layer 3 clean of blocker and major defects (1 minor wording point).

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 | The spec has no Done-when section, although its Sonar standing constraint says "The Done-when lists the PR's open-issue count and the rules left open" | checked | `plans/PLAN-24-client-id-strict-typing.md` section list: Objective, Deliverables, Standing Constraints, Claim Labels, Expected Surface, Dependencies, Hand-Off, Write-Boundary |
| L1-2 | Deliverable 1's "an `azp`-present token is unchanged" contradicts the mechanism the spec mandates | checked | `TokenBuilder.java:140-152` maps every key present in the body, so a non-string `client_id` throws whatever `azp` holds |
| L1-3 | Expected Surface omitted `AudienceValidatorTest.java` | checked | the spec's own observation says the PLAN-11 azp rows live there; the diff touches it. Declaration corrected in the spec at this analysis. |

Every OBSERVED and HYPOTHESIS premise matches `main` (`ClaimName.java:179`, `AuthorizedPartyValidator` reads `CLIENT_ID` only at line 100, `TokenBuilder.mapClaim` counts a mapper-thrown event and rethrows). No labelled claim is addressed by the three findings, so `corpus set-verdict` persists nothing.

**Layer 1 verdict:** findings — 3 (all minor)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `ClaimName.java:179` | `AuthorizedPartyValidatorTest` client_id matrix and beside-azp test, `ClaimNameTest` pin and rows. By reading each fails on `IdentityMapper` (with it the build succeeds and the stringified id matches). The matrix is partly vacuous: the build throws before the validator is reached, so `fallbackEnabled` changes nothing; eight rows test one behaviour. | none | checked |
| 2 | `security-reference.adoc:35,46,47` | none (docs) | `security-reference.adoc:35`: "so a non-string `sub` cannot satisfy a comparison" is not true as worded. `SUBJECT` uses `IdentityMapper` (`ClaimName.java:52`) and `TokenLifecycleManager.java:1273` compares `accessSubject` with the ID-token subject as strings, so a numeric access-token `sub` equals the same text in the ID token. Token to token between two signature-verified tokens, so no acceptance defect, but the sentence overstates. The `jti` sentence matches the sweep. | checked |
| 3 | `AuthorizedPartyValidatorTest` azp test | same | `AudienceValidatorTest` azp test not tightened (outside the spec's wording) | checked |
| lane report | n/a | n/a | "Non-string azp/client_id tests added": the client_id tests are added, the azp tests were tightened. The lane's "2186/3870 tests pass" and "new rows fail under IdentityMapper" cannot be shown by reading. | not_shown_by_reading |

Also minor: `JsonTypedAzpFixture` and its provider `nonStringAzpValues` now serve `client_id` too, so the name has drifted; `ClaimNameTest` builds its own number and boolean generators because the fixture is package-private elsewhere.

**Layer 2 verdict:** findings — 3 minor, 1 not shown by reading

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `ClaimName.java:173`, `security-reference.adoc:46` | explicit `"client_id": null` | The text "must be a JSON string" is slightly broader than the behaviour. `MapRepresentation.fromJson` (`MapRepresentation.java:108-115`, from #511, untouched by this PR) drops null entries, so a null claim reads as absent: `client_id: null` with `azp` absent and the fallback on is `MISSING_CLAIM` at `AuthorizedPartyValidator`, fail-closed; beside a valid `azp` it is accepted, as if absent. No test pins it and none claims it is rejected. | minor |

No blocker or major defect. The production change is one line and fail-closed. The lane's accepted CodeRabbit point (`7a33e9`, null dropped before any mapper runs) is verified true and pre-existing. The strictness is global at token build: an identity provider emitting a non-string `client_id` is now rejected for access and ID tokens even where nothing reads the claim, with the fallback off (the default) too. The PR states this.

**Layer 3 not shown by reading:** ID-token impact beyond build-time rejection (whether ID tokens reach `AuthorizedPartyValidator` was not read); that the tests pass (no run).

**Layer 3 verdict:** findings — 1 minor. No layer-3 defect needs a fix plan on its own; the wording point is folded into PLAN-25 item 7(i).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | Parameterized tests for 3+ similar variants | conforms | `@ParameterizedTest` with `@MethodSource` in `ClaimNameTest` and `AuthorizedPartyValidatorTest` |
| AGENTS.md | CUI test generators for test data | conforms | numbers and booleans from `Generators` |
| AGENTS.md | New INFO/WARN/ERROR needs a LogRecord and `LogMessages.adoc` | conforms | the diff adds no logging |
| AGENTS.md | Pre-1.0: no deprecation or transitional comments | conforms | none in the diff |
| AGENTS.md | Javadoc and documentation accuracy | deviates | the `sub` sentence at `security-reference.adoc:35`; the fixture name still says `Azp` |
| Spec constraints | No new class; no edit to `source-report/` or `findings-disposition.md`; fail-closed only | conforms | 6 files, none new, none in the ledger |
| Spec constraints | PR body names the origin and the narrowed acceptance | conforms | PR body: "Origin: PLAN-11 #853", "Narrowed acceptance" |
| Spec constraints | Every behavioural fix ships a test that fails before and passes after | conforms | by reading; execution not shown |
| CLAUDE.md | Merge only after CI green and every AI-reviewer comment resolved | conforms | read directly: 24 checks, 0 failing (CodeRabbit `NEUTRAL`, Sourcery `SKIPPED`), Sonar below; comment resolution rests on the lane's own record |

## Metrics and Anomalies

- Tokens: 10,007,629 total (landing fact; no per-phase breakdown carried).
- Duration: 10,459 s wall (about 2 h 54 m).
- Anomalies: one loop-back (iteration 1) for a post-PR Sonar `java:S5778` finding; the e2e job was cancelled by its 30-minute job timeout after all 30 tests passed (a teardown hang the lane says recurs on `main`) and was cleared by a rerun, the rerun now reads `SUCCESS`; the pre-push gate recorded "test-compile not run" (the known reactor limitation, whole-tree verify green); a post-merge `checks wait --adaptive` hung 10 minutes with no output and was stopped by hand; `model` and `effort` are self-reported (`refresh-runtime-stamp` returned `model_unknown`), `effort=medium`, so the landing is complete and no incomplete-landing defect applies. Two of the four candidate-lessons are plan-marshall-owned process notes.

## Routing and Merge Behavior

- Review: CodeRabbit left an inline comment (`7a33e9`, explicit JSON null dropped before any mapper) accepted as pre-existing and out of scope, which this analysis verified. Sonar `de785b` (`java:S5778`, `AuthorizedPartyValidatorTest`) fixed in `826f6f46`. Code review: 0 blocking, 3 advisory.
- CI/merge: all PR checks green at merge (the e2e `test` job after a rerun), merged through the queue 2026-11-06T16:42:18Z. No rebase conflict reported. PLAN-17 and PLAN-18 were launched alongside and had not merged at this analysis (#878, #877 open).
- Sonar: lane claim `step.sonar-roundtrip:done`, no `open_issue_count` fact. Measured directly: PR #876 quality gate `OK`; the PR's one issue, `java:S5778`, is `CLOSED` / `FIXED`; `main` has 0 open issues (`resolved=false`, total 0). Consistent with the lane.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-24 --status shipped`
- [x] row `pr` stamped (`876`)
- [x] row `landing` stamped (`landings/PLAN-24.md`)
- [x] row `plan_marshall_plan_id` stamped (`plan-24-client-id-strict-typing`)
- [x] epic.md narrative reconciled (decision entry, Watch for the e2e teardown hang)
- [x] PLAN-24 spec: Expected Surface now names `AudienceValidatorTest.java` (L1-3)
- [x] PLAN-25: the residuals folded in as item 7(i), Expected Surface +7 entries, claim label added; `corpus surfaces` reads it declarative, 38 paths
- [x] upstream report: 2026-11-06 (fourth) addendum written, not sent
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- `security-reference.adoc` line 35 `sub` sentence, the null-is-absent wording, and the test hygiene points: folded into PLAN-25 item 7(i), with one operator question (should an explicit JSON `null` identifier claim be rejected; default "pin as observed").
- e2e teardown hang that recurs on `main`: recorded as a Watch; no plan staged.
- The four candidate-lessons: two plan-marshall-owned (`architecture search --module`, `manage-findings qgate query`) and the `java:S5778` one (suggested for `pm-dev-java:junit-core`) went into the upstream addendum as discarded locally; the null-claim fact is folded into PLAN-25.
