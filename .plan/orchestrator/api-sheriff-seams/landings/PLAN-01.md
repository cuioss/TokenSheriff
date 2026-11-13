# Landing Analysis: PLAN-01 — Client native-image registration

epic: api-sheriff-seams
workstream: WS-01
pr: #898 (https://github.com/cuioss/TokenSheriff/pull/898), merged as c23e2a857d8f153652a99200f73f92317760d2d4

> Landing record for one shipped plan. Lives at `landings/PLAN-01.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `client-native-image-registration-008.md` (lane `plan-marshall`, plan id
`client-native-image-registration`). PR state read through `ci pr view` (`state: merged`, merge commit as above, base `main`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. DSL-JSON converter registration | shipped-as-specified | `reflect-config.json` holds 16 entries (8 `_*_DslJsonConverter` + 8 served types, `allDeclaredConstructors` only, no `condition`); `ClientNativeReflectionMetadataTest:82-110` pins the exact set and the non-vacuity converters (`TokenErrorResponse`, `JsonObjectProbe`). Re-read at the merge commit. |
| 2. Runtime init of static-`SecureRandom` classes | shipped-as-specified | `native-image.properties:1` names `FlowContext` and `PkceChallenge`; `ClientNativeRuntimeInitializationTest:64-104` pins the set, non-vacuity and resource presence. |
| 3. Delivery mechanism chosen once and documented | shipped-modified | `META-INF/native-image` resource set in the client jar (the spec's second option); reason recorded at `doc/client/requirements.adoc:589-594`. The recorded reason carries a wrong premise — see L1-1. |
| 4. Native integration test closing both fail-open cases | shipped-as-specified (run not shown by reading) | `ClientEngineNativeEndpoint` + `ClientNativeImageSpecIT` (credential_rejected, jkt_mismatch, matching-key control). The native run and the negative controls are the lane's and the PR body's claim; not re-run. |
| 5. Documentation | shipped-as-specified | `CLIENT-26` is the next free id (`requirements.adoc:581`), traceability row, `test-strategy.adoc` names the guard tests. |

Added unplanned (4 docs, all low-risk sync): `doc/client/README.adoc`, `doc/developer/test-impl-mapping.adoc`,
`token-sheriff-quarkus-parent/doc/configuration/native-image-configuration.adoc`, integration-tests `README.adoc`.
Declared but untouched (9): `doc/LogMessages.adoc` (no LogRecord added), `FlowContext`, `PkceChallenge`, `AccessTokenConfirmation` (read-only by spec), the three client-deployment files (`pom.xml`, `TokenSheriffClientProcessor`, its test — the BuildStep alternative was not chosen), `JwtValidationEndpoint`, integration-tests `application.properties` (the plan's point: no entry needed).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze`) — reason: reading merged code is the depth the Dispatch Decision Rule reserves for a leaf, and this landing's read shares no context with any other.
- The orchestrator re-read these `checked` points at their file:line on the merge commit before recording them: `requirements.adoc:589-595` (L1-1, L2-5), `ClientNativeImageSpecIT:23` (Hamcrest import), `NativeImageClassScanner:63-65,81-84,93,122,133` (Javadoc `@throws`), `ClientEngineNativeEndpoint:78-80,184-190,210-214` (thread-safety Javadoc, the two untested 400 branches), `reflect-config.json` (16 `name` entries, 0 `condition`). The remaining `checked` points are carried as the leaf's reading and were not individually re-read.
- **Verdict:** layer 1 findings (1, minor) and not shown by reading (1); layer 2 findings (6, all minor); layer 3 clean, with not shown by reading (3). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The recorded delivery rationale says the API Sheriff dependency shape "uses the engine without the client Quarkus extension"; the spec says it includes it. **Settled by this analysis:** `API-Sheriff/api-sheriff/pom.xml` lists `token-sheriff-client`, `token-sheriff-client-quarkus` and `token-sheriff-validation-quarkus`, so the spec was right and the doc rationale is wrong. The delivery choice stays valid (it also reaches bare GraalVM consumers). | checked | refutes | `doc/client/requirements.adoc:591` |
| L1-2 | Spec HYPOTHESIS 2 and 3 (does the DSL-JSON service-loader path already cover the client converters; does a client-only consumer get no service registration) are answered in no landed doc or file; the doc asserts reach for bare GraalVM builds without a test showing the `META-INF/services` half. | not_shown_by_reading | none | `doc/client/requirements.adoc:591` |
| L1-3 | Static-`Random` re-check holds: only `FlowContext:55` and `PkceChallenge:43`. Other static state of types native refuses to bake in was not searched. | checked | confirms | `requirements.adoc:586` |

No verdict is persisted onto the spec: the spec is spent (shipped) and no finding names a claim of its own `## Claim Labels` section that a `corpus set-verdict` call could address.

**Layer 1 verdict:** findings — 1 (L1-1); not shown by reading — 1 (L1-2)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `reflect-config.json` | `ClientNativeReflectionMetadataTest:82-148` (exact set, non-vacuity, no duplicates, flags, `condition` counts as widening) | none | checked |
| 2 | `native-image.properties:1` | `ClientNativeRuntimeInitializationTest:64-115` | none | checked |
| 3 | resource set, `requirements.adoc:589-594` | resource-presence test `:94-104` | none | checked |
| 4 | `ClientEngineNativeEndpoint:152-171,207-236` | `ClientNativeImageSpecIT` (3 tests, asserted from the refusing side) | L2-1: spec asks each negative control "documented in the test"; the IT Javadoc `:25-40` gives the failure shape, the removal procedure lives in `test-strategy.adoc:348-352` and the PR body — partially unmet, doc location a reasonable substitute | checked |
| 5 | `requirements.adoc:581`, `test-strategy.adoc:327-352` | n/a | L2-2: the 4 added docs above are explained by repo convention, not by a deliverable | checked |

Further layer-2 findings (all minor, none changes a deliverable verdict):

- **L2-3** `ClientEngineNativeEndpoint:184` (stub-token 400) and `:210` (unknown-mode 400) are reached by no test — the lane's own advisory note, confirmed.
- **L2-4** `NativeImageClassScanner:65` and `:83` declare `@throws IllegalStateException`; read failures throw `UncheckedIOException` (`:93,:122,:133`) — confirmed.
- **L2-5** `requirements.adoc:595` and `test-strategy.adoc:503` say "SecureRandom holders" while the guard covers every static `Random` — confirmed.
- **L2-6** `ClientNativeRuntimeInitializationTest:136` finds only a directly declared static `Random` field; an indirectly held one is not found. This matches the spec's own definition; the docs' wording over-claims slightly.
- **L2-7** `ClientNativeImageSpecIT:23` imports `org.hamcrest.Matchers.equalTo`. The spec's Standing Constraints forbid Hamcrest; `AGENTS.md` does not name it (grep: no hit) and eight sibling ITs in the module use the same import. Deviates from the spec text, conforms to the module precedent.

The lane's claim "no change to `FlowContext`, `PkceChallenge`, the client Quarkus processor or any engine source; no `application.properties` entry" is borne out by the diff stat.

**Layer 2 verdict:** findings — 6 (L2-1, L2-3..L2-7), all minor

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| none | — | — | — | — |

Reviewed with no defect: `ClientEngineNativeEndpoint` stub-token thumbprint is matched against `^[A-Za-z0-9_-]+$` before it is spliced into JSON (`:184-190`), the only caller-controlled value reaching a log (`mode`) is allow-listed first (`:210`), no caller-supplied URL (no SSRF surface), per-request state only (thread-safety Javadoc `:78-80` accurate). Two notes, neither a defect: `jkt_mismatch` is classified by substring of the engine message (`:132,:229`) — brittle but fails loudly as `other_refusal`; each `dpopBinding` hit generates one or two RSA-2048 keypairs and builds a client (`:215-222`) — acceptable in a test-only module, and `ClientEngineNativeEndpoint` should never be built into a deployable artifact (whether the module is published was not checked).

**Layer 3 not shown by reading:**
- L3-NS1 constructor-only reflection suffices for DSL-JSON converters — supported by reading and by the validation jar's identical precedent; only a native run proves it.
- L3-NS2 the runtime-initialization effect of `native-image.properties` — no native-image control exists for deliverable 2 (the spec asked a removal control only for deliverable 4); coverage is the drift guard over file content.
- L3-NS3 everything only a run shows: the native 3/3 IT result, the removal controls, `-Ppre-commit` / `clean install`, the Sonar read, a bare non-Quarkus consumer actually benefiting, the Keycloak realm client existing, the native distroless trust-store path.

**Layer 3 verdict:** clean — not shown by reading — 3

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md / spec | Test classes at most 500 lines | conforms | 220 / 168 / 148 / 83 lines |
| AGENTS.md:166 | AAA test structure | conforms | `ClientNativeRuntimeInitializationTest:106-115`; ITs given/when/then |
| spec Standing Constraints | No Mockito / PowerMock / Hamcrest | deviates | `ClientNativeImageSpecIT:23` (module precedent, see L2-7) |
| spec Standing Constraints | Parameterized tests for three or more similar variants | conforms | `assertAll` lists |
| AGENTS.md / cui-logging | `CuiLogger`, `%s` only, `LogRecord` for INFO/WARN/ERROR | conforms | endpoint `:91,165,167,227,233,262` (DEBUG only) |
| AGENTS.md:495 | Document thread-safety considerations | conforms | endpoint Javadoc `:78-80` (bot-found gap fixed) |
| AGENTS.md pre-1.0 rules | No `@Deprecated`, transitional comments or shims | conforms | none in any added file |
| spec Standing Constraints | Names classes by symbol where possible; string-name resource needs guard tests | conforms | exact-set and resolvable-name guards |
| pm-dev-java:javadoc | `@throws` matches behaviour | deviates | `NativeImageClassScanner:65,83` (L2-4) |
| spec / AGENTS.md | No new dependency; deterministic tests | conforms | no pom touched; scanner sorted via `TreeSet` |

## Metrics and Anomalies

- Tokens: 13,310,080 total (lane report; per-phase split not in the payload).
- Duration: 21,615 s wall (about 6.0 h). Model `claude-sonnet-5-5`, effort `medium`, harness `claude`, plugin and executor `0.1.2106`.
- Anomalies: the lane's finalize steps all read `done` (16 steps, including `lessons-capture`, `sonar-roundtrip`, `branch-cleanup`). The `lessons-capture` step was recorded `--force` (lane's own statement; its terminal record was verified by the lane afterwards, not by this analysis). The finalize-re-entry handshake re-capture was refused because the plan worktree was already removed after the merge; the lane reports nothing was lost.

## Routing and Merge Behavior

- Review: bots sourcery-ai and coderabbit. Two actionable sourcery findings (5840a4 medium, 2eeb51 low) were fixed in commit `4e49a28c`; both are recorded as lessons (2026-11-12-18-001, -002).
- **Review numbers re-read against the provider.** The landing carries `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` all as `unknown` (landing-check: `complete: false`, `missing_keys` those three). `runbook_lane review-gate --pr-number 898` reads `unresolved_review_threads: 0`, `review_body_findings_total: 2` (both coderabbit, inline) and `review_body_findings_handled: 0` (`undisposed`: coderabbit `inline-1`, `inline-2`; `merge_allowed: false`, `reason: body_findings_undisposed`). The landing asserted no number for these, so there is no false claim — but the lane's narrative says every inline finding was answered while the provider shows two coderabbit body findings without a recorded disposition. The handled count is **not re-verified**: the run's progress file was not available to this analysis.
- CI/merge: merged through the queue as `c23e2a85` (squash form, single commit on `main`); no rebase conflict reported.
- Sonar: the lane's `sonar_open_new_issues=0` could not be re-verified. `read_open_issues` for PR 898 returned `count_status: undecidable` (`head_revision_unavailable`, analysis revision `4e49a28c`), so this analysis states `unknown`, not `0`.
- Surface collision: the landing touched 4 files its spec never declared. One of them, `doc/developer/test-impl-mapping.adoc`, is declared by the running PLAN-05 and the staged PLAN-06 and PLAN-07, so those rebase onto it. The disjointness gate did not predict this because the under-declaring spec is the shipped PLAN-01; that spec is spent and its declaration is not corrected. The same README gap is already folded into PLAN-02, 03 and 04 (see Follow-Ups).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-01 --status shipped`
- [x] row `pr` stamped — `#898`
- [x] row `landing` stamped — `landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped — `client-native-image-registration`
- [x] epic.md narrative reconciled (Open Defects, Watches, Decisions)
- [x] defects and watches opened (see below)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- **Fix-forward of the minor residuals (L1-1, L2-1, L2-3..L2-5, L2-7):** recorded as one Open Defect in `epic.md`; none is staged. The doc-rationale correction (L1-1) and the "SecureRandom holders" wording (L2-5) are the two with consumer-facing text. They become a staged spec, or fold into PLAN-07 (`public-helper-hygiene`), only on the operator's word.
- **Incomplete landing:** the three review facts the lane did not record, plus the two undisposed coderabbit body findings, are recorded as an Open Defect for the operator to settle on the PR.
- **API Sheriff must re-measure on bump:** nothing in this landing changes JVM-mode behaviour (build-time native metadata only). A native consumer that carries its own `TokenClientDslJsonReflection` and `--initialize-at-run-time` argument keeps both registrations in coexistence, which is harmless; the matching deletion is API Sheriff's PLAN-34, after the released 0.9.10.
- **Candidate lessons:** seven messages dispositioned in the decision log (3 promoted, 4 recurrences folded; README range folded into PLAN-02/03/04).
