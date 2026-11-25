# Landing Analysis: PLAN-13 — Renumber the COMMONS and VALIDATION requirements to be monotonic

epic: client-framework-aspects
workstream: WS-05
pr: #956 (merged through the merge queue as `1022071f`, 2026-11-24T22:23Z)

> Landing record for one shipped plan. Written by `analyze` (inbox scan) from landing message
> `plan-13-renumber-commons-validation-007`, corroborated against git (`1022071f`, parent `c2f18a45`) and the CI
> abstraction. The lane's claims are leads; the facts below were read.

## Deliverable Fidelity vs Spec

Plan id `plan-13-renumber-commons-validation`. 6 of 6 deliverables reported done; functional review 4 met and 2 met with
residuals. `git show --stat 1022071f`: 33 files, +912 -159 (spec cap 100). The realized footprint (33) lies inside the
declared Expected Surface (`doc/`, `token-sheriff-validation/`, the validation-quarkus module, `token-sheriff-quarkus-parent/doc/`,
`benchmarking/doc/`, `benchmarking/README.adoc`) plus the client citations the spec's Dependencies section names; the lane
reports its own declared footprint as 66 (an over-estimate). No `.plan/` path, no other repository and no `CLIENT-` anchor is
touched. `inbox landing-check`: `complete: true`; `surface_delta` is `unmeasured` (neither side was supplied to the check), so the
containment above was read by hand from the diff, not measured by the verb.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. "Former identifiers" tables | shipped-as-specified | `doc/commons/requirements.adoc:330-353` (13 rows, old 5-17, targets distinct and covering 5..17); `doc/validation/requirements.adoc:792-808` (5 moved parents 9→12, 10→13, 12→9, 13→10, 14→11; sub-ids follow their parent). |
| 2. Apply the map everywhere in one pass | shipped-modified | All 11 Java files are comment, Javadoc or `@DisplayName` changes only (`HttpJwksLoader:1963`, `InboundErrorNormalizer:25`, `CallbackHandler:84`, `ClientExceptionMapper:26`, `ProblemDetailExceptionMapper:34`, the four test files). `doc/LogMessages.adoc` and `doc/developer/test-impl-mapping.adoc` carry no COMMONS or VALIDATION id at `c2f18a45`, so they needed no edit (L1-3). Prose changed beyond digits in four places (see Layer 2). |
| 3. Requirement-id integrity guard | shipped-as-specified, with gaps (L3-1 to L3-3) | `RequirementIdIntegrityTest.java`: three checks over `EnumSource` (order, dangling, duplicate), CLIENT `Enforcement.PENDING` (line 102). |

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, the `orchestrator.analyze` surface),
and the orchestrator re-read every point recorded below as `checked` at its line in the merged tree (`git show 1022071f:{path}`),
except where a row says it rests on the leaf's reading alone. The reading is by reading: no build, no test run, no write.

- **Dispatch:** one read-only leaf per landing — reason: the rule dispatches every full-ship landing, and this one carries a
  713-line guard whose predicates had to be traced against edge input, plus 32 citation files.
- **Verdict:** layer 1 findings (1 info); layer 2 findings (3 minor); layer 3 findings (3 minor, none blocking).
  Not shown by reading: the green `verify`, the guard's execution in CI, Sonar.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's two OBSERVED order claims hold at `c2f18a45`'s parent state: VALIDATION read 1-8, 12, 13, 14, 9, 10 with 11 absent, COMMONS 1-4, 17, 5-12, 16, 13-15 | checked | confirms | the Former identifiers tables (above) invert exactly those orders |
| L1-2 | Done-when "the guard fails at `9c242518`" is consistent with the premises, but cannot be shown: the guard did not exist there. The lane covers it with a predicate test of both literal orders | not_shown_by_reading | none | `RequirementIdIntegrityTest.java:276-295` (leaf reading) |
| L1-3 | Spec premise that `doc/LogMessages.adoc` and `doc/developer/test-impl-mapping.adoc` cite COMMONS/VALIDATION ids does not hold: neither file carries one (only `CLIENT-26`) | checked | refutes | `test-impl-mapping.adoc:458,568` (leaf reading; the orchestrator re-read the empty sweep below) |
| L1-4 | Standing constraint "only ids change" cannot be met literally: the prose "not sequential" had to be reworded and the traceability rows merged | checked | refutes | `doc/commons/requirements.adoc:324-327`; `:17-19` |
| L1-5 | HYPOTHESIS "no test asserts a requirement id at runtime": no Java assertion or constant changed in the diff; the rest of the test tree is not swept | not_shown_by_reading | none | `TokenValidatorTest.java:59-79` cites only unchanged ids ≤ 8 |

**Layer 1 verdict:** findings — 1 (L1-4, info); L1-2 and L1-5 not shown by reading.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `requirements.adoc` of both prefixes | the guard's order check | none | checked |
| 2 | 20 citation docs, 11 Java files | the guard's dangling check | L2-1, L2-2, L2-3 | checked |
| 3 | `RequirementIdIntegrityTest.java` | predicate tests at `:231-257`, `:259-274`, `:297-353`, `:355-370` (leaf reading) | none | checked |

Every one of the 33 touched files maps to a deliverable. **Orchestrator sweep for stale ids** (the check the leaf could not make):
at `1022071f` no file outside `.plan/` and outside the 33 touched files cites an id that moved (`COMMONS-5..17`,
`VALIDATION-9..14`), so no citation of a moved id was left unrewritten. A stale citation inside a touched file is not excluded by that
sweep; the guard cannot see one either, because every old id still resolves.

- **L2-1 (minor).** Five restated ranges widened from `COMMONS-1`–`COMMONS-8` to `1`–`9`: `doc/commons/requirements.adoc:324`,
  `doc/client/best-practices.adoc:21` and `:156`, `doc/client/requirements.adoc:52`, `doc/client/specification/test-strategy.adoc:285`
  (old and new text re-read). The new `COMMONS-5` (old 17, the bounded response-body handler) is now inside the range. The traceability
  row at `:324` maps the range to `transport.adoc`, where `COMMONS-5` lives, so the widening is accurate; it is a coverage change
  beyond digits, which the commit body `b2a538c3` ("still cover the same requirements") does not say.
- **L2-2 (minor).** The merged squash body line 77 says "Clarified when logout revocation occurs after refresh refusal". The
  word-diff of `doc/client/specification/token-handling.adoc` shows only `COMMONS-7`→`COMMONS-8` style id changes on the cited
  lines; the sentence about refusals revoking nothing when the refresh token did not rotate is unchanged context. The commit body
  over-claims. History is not rewritten.
- **L2-3 (info).** `doc/commons/requirements.adoc:334` says "a sub-ID follows its parent", but COMMONS has no sub-ids (no `COMMONS-n.m`
  anchor exists). Harmless vestige of the VALIDATION wording.

**Layer 2 verdict:** findings — 3 (L2-1, L2-2 minor; L2-3 info).

### Layer 3 — Correct

None blocking. Three defects in the merged guard, each re-read at its line, plus the fixes the lane names:

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `RequirementIdIntegrityTest.java:485-493` | `xref:../validation/requirements.adoc#COMMONS-11[...]` in `doc/client/` | A reference is checked against the prefix's own id set only, never against the xref target document's anchors, so a link to a file with no such anchor passes the build (deferred by the lane; CodeRabbit `6ccba2`, answered "follow-up") | minor |
| L3-2 | `RequirementIdIntegrityTest.java:69-72` | A citation in `.svg`, `.puml`, `.properties`, `.yml`, `.json`, `.xml` or a script; any citation under `.github` or `.claude` | Only `.adoc`, `.md` and `.java` outside the excluded directories are swept, so those citations are never guarded although the spec said "every reference" | minor |
| L3-3 | `RequirementIdIntegrityTest.java:650-656` (`return base`) and the file as a whole (713 lines) | No ancestor carries the repository markers; the class is 713 lines | The fall-through is covered by no test (the setup assertions refuse the run, so it is not a defect); the class exceeds `AGENTS.md` § Test Class Size (≤ 500 for a created class, else the PR body states the reason), and the PR body does not | minor |

Fixes the lane names, re-read present at the merged tree: parentless sentinel is `-1` (`:445`, F1); explicit zero sub-id is
rejected in the order check and in the dangling check (`:455-460`, `:489-492`, ed6c1d); the range regex checks both endpoints
(`:595-609`, 61bb85); the root resolver starts at the base directory and walks its ancestors (`:650-655`, 6ccba2). CLIENT stays
`PENDING` (`:102`), parsed and then skipped through `assumeTrue`, so it is reported as skipped rather than passed.

**Layer 3 not shown by reading:** that `./mvnw verify` is green at the merge head and that the guard actually runs in CI (it is a
plain `*Test` in the validation module, picked up by Surefire); that the renumbering was done by a mechanical tool and not by hand
(collision-free results are visible: 9↔12 and 10↔13 are correct in the diff); the Sonar count.

**Layer 3 verdict:** findings — 3 (all minor); the items above not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `AGENTS.md` § Test Class Size | A test class a PR creates stays ≤ 500 lines, else the PR body gives the reason | deviates | `RequirementIdIntegrityTest.java` is 713 lines (re-read); no reason in the PR body |
| spec Standing Constraints | Only ids change; no wording or normative-level change | deviates | L1-4, L2-1 |
| spec | CLIENT untouched and pending; no `.plan/` edit; no other repository | conforms | `:102`; the 33-file diff |
| spec | PR ≤ 100 files | conforms | 33 files |
| spec | Guard enforces order, dangling and duplicate for all three prefixes | conforms | three checks over `EnumSource` (CI execution not shown) |
| `AGENTS.md` § Documentation | xref syntax, no new document | conforms | new sections use `xref:#former-identifiers[]`; no new document |

## Metrics and Anomalies

- Tokens: 21,031,778 total (landing fact). Per-phase outliers were not supplied.
- Duration: 37,310 s wall (about 10.4 h), 15 finalize steps all `done`.
- Anomalies: the plan looped back from `5-execute` to `4-plan` once (2026-11-24T14:29Z); a kernel OOM killed the first whole-tree
  quality-gate build (a Maven build of another session ran at the same moment; rerun with `-T 4`); one blocking code-review
  finding (F1, `0` as the no-parent sentinel) was fixed in `3ff7df48`; the post-merge `Run JMH Benchmarks` workflow was still running
  when the post-merge CI wait timed out (non-gating); the plugin installed from the cache rejected two documented flags
  (`manage-findings qgate query`, `manage-change-ledger append --changed-paths-file`) — routed as lessons, see Follow-Ups.

## Routing and Merge Behavior

- Review: CodeRabbit left two inline threads (both resolved, answered in-thread, fix `3ff7df4`) and one review body with two
  outside-diff findings (range endpoints: fixed; xref target: declined to a follow-up with a stated reason). Sourcery's body reads
  "Approved." Landing numbers: `unresolved_review_threads=0`, `review_body_findings_total=2`, `review_body_findings_handled=2`.
  Re-read at analysis (2026-11-25T06:19Z) through the CI abstraction's `ci pr comments`: 2 threads, 0 unresolved (`7 unresolved`
  in that payload counts non-thread comments and bodies, which carry no resolution). `review-gate` produced no output when run
  through its notation, so the body count rests on the two bodies read above; `review_body_findings_handled` is **not re-verified**
  (the run's progress file was not read), and is recorded neither as a difference nor as a false claim.
- CI/merge: merged through the merge queue at 2026-11-24T22:23Z; `merge_commit_sha` `1022071f` is on `origin/main`.
  `cleanup_owed=false`.
- Sonar: landing claims `sonar_open_new_issues=0`. The re-read (`sonar read_open_issues --project cuioss_TokenSheriff --pr 956`)
  returned `count_status: undecidable`, `count_status_reason: head_revision_unavailable` (analysis revision `3ff7df48`, no head
  revision), so the figure is **unverified**, not contradicted. The Done-when "zero Sonar issues" is not evidenced by this analysis,
  the same state as PLAN-12.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-13 --status shipped`
- [x] row `pr` stamped — `--field pr --value 956`
- [x] row `landing` stamped — `--field landing --value landings/PLAN-13.md`
- [x] row `plan_marshall_plan_id` stamped — `--field plan_marshall_plan_id --value plan-13-renumber-commons-validation`
- [x] epic.md narrative reconciled (queue annotations, decisions, open defects)
- [x] PLAN-16 gained deliverables 6 and 7 and its surface widened (standing residual rule)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **L3-1, L3-2, L3-3 and the guard advisories** (parameterisation, the `return base` branch test, the 713-line size) → PLAN-16
  deliverable 6 (the guard follow-ups; PLAN-14 is a renumbering plan and is never a host).
- **L2-3** (the `doc/commons/requirements.adoc:334` sub-id sentence) → PLAN-16 deliverable 7.
- **L2-1 and L2-2** are recorded only: the widened ranges are accurate, and the commit body is history.
- **Candidate lessons `-001` to `-006`:** `-002` and `-003` (guard design: absent versus explicit zero; range endpoints, repository
  root, xref target) promoted to this repository's lessons store; `-001`, `-004`, `-005` and `-006` name plan-marshall components
  (task verification module list, installed-script flag drift, the pre-push freshness verdict, the OOM under a disabled build
  server) and are routed to the plan-marshall repository's `lessons-routing` inbox under the standing rule in `epic.md` Decisions.
- **PLAN-14** still owes the CLIENT ids and removes the guard's CLIENT `PENDING` entry; it keeps the two predicate tests that pin
  the absent-versus-zero cases.
