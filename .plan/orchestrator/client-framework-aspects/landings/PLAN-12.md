# Landing Analysis: PLAN-12 — Documentation baseline: full review, consolidation, ADR reduction

epic: client-framework-aspects
workstream: WS-05
pr: #952 (https://github.com/cuioss/TokenSheriff/pull/952), merge commit 66cecbf481c69355fe7e503b8f1be4f2e01b23db

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-12-documentation-review-consolidation-009.md`) after verifying claims against git, the
> CI abstraction and the merged tree. The message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against git (`66cecbf4` against its first parent) and `ci pr view 952` (`state: merged`).
Diff: 91 files, +1701/-6533 (18 deleted, 73 modified).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1 Review report | not shown by reading | A plan artifact, not in the repo diff; PR body summarises it. The lane reports 12/12 deliverables done. |
| 2 Consolidation, applied | shipped-as-specified | `doc/` adoc 62 -> 48 files, 14285 -> 10876 lines; four Quarkus guides deleted and merged into `devui-implementation` and `native-optimization-guide`; PR body states before/after counts. The operator's approval of the target structure is not readable from the repo. |
| 3 ADR review and reduction | shipped-modified | 31 -> 17 ADR files, 4982 -> 1317 lines: 14 deleted, 14 condensed, 3 untouched (0001, 0022, 0024); matches the PR body. The spec default was "a superseded ADR keeps its file with status Superseded" unless the operator allows deletion; 14 were deleted outright, ADR-0005 (which had that status) among them. No operator allowance is readable from the repo. |
| 4 Apply review findings | shipped-as-specified, with residuals | `functional-review`: 7 deliverables met, 5 met with residuals, 0 not met. Residuals below. |
| 5 Diagram fixes | shipped-as-specified | `token-content-types.svg`, `validation-components.svg`, `jwe-key-locations.svg` changed; new labels match the code (sealed `MinimalTokenContent`, `TokenType` members). Rendering not checked. |

Done-when check, by condition:

- Requirement ids unchanged: **conforms** — 116 ids before and after the merge; 109 outside `.plan` and identical to `9c242518`. The 7-id difference against `9c242518` is `.plan`-only (`b7b49bb9`).
- PR changes 100 files or fewer: **conforms** — 91 (82 without the nine `.plan` files).
- Sonar zero new-code issues and zero hotspots, with the rules named: **not evidenced.** The lane recorded the review and Sonar counts as `unknown` and states the hotspot read was not made. This analysis's own re-read (`read_open_issues`, PR 952, at 2026-11-24T06:32Z) is `undecidable` (`head_revision_unavailable`), so it too is `unknown`, not `0`.
- AsciiDoc build clean, no broken xref: **not shown by reading**; one anchor looks broken (L3-5).
- Each ADR carries a recorded classification, review report exists: **not shown by reading** (plan artifacts).

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reading `git` and the merged tree only: no build, no test run, no write. The orchestrator re-read the points marked "re-read" below at their `file:line` at the merge commit before recording them.

- **Dispatch:** one read-only leaf per landing — reason: reading the merged documentation and the two Java files against the spec is the depth the dispatch rule exists for, and it keeps this landing's read out of the orchestrator's context.
- **Verdict:** layer 1 findings — 1 (spec); layer 2 findings — 4 (+ items not shown by reading); layer 3 findings — 5 (3 minor defects, 1 pre-existing advisory carried forward, 1 `not_shown_by_reading`).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's Done-when and standing constraints are mutually consistent | checked | refutes | spec § Done-when vs § Standing Constraints: "do not touch `.plan/`" conflicts with a diff that edits nine `.plan/archived-orchestrators/quality-report` files, and "ids identical at `9c242518`" holds only outside `.plan` because `b7b49bb9` added CLIENT-32..36 and VALIDATION-11 there afterwards. The spec reconciles neither. |

No claim-label of the spec is named by this finding, so no corpus verdict is persisted.

**Layer 1 verdict:** findings — 1

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 2 / 3 counts | `git diff --name-status 66cecbf4^1 66cecbf4 -- doc/adr` (re-read: 31 -> 17 files) | zero-hit reference scan (below) | none | checked |
| 5 diagrams | three SVGs | rendering not checked | none | checked (labels), not_shown_by_reading (render) |
| 4 comment-only Java edits | `TokenLifecycleManager.java:1055` (re-read), `DefaultBeanReplacementTest.java:53` | none (comment-only) | **both are outside the spec's "Javadoc links only" carve-out**: line 1055 is a prose edit, line 53 is a doc pointer outside the declared surface (the lane flagged it) | checked |
| standing constraint "do not touch `.plan/`" | nine files under `.plan/archived-orchestrators/quality-report/`, +25/-15 (re-read with `git diff --stat`) | n/a | no deliverable explains them; the PR body calls it an operator-selected exception, not readable from the repo | checked (edited), not_shown_by_reading (authorisation) |

Not shown by reading: the review report, the per-ADR classification record and the operator's approval of the structure live in plan artifacts and the escalation, not in the repo diff. The repo carries no retired-number record for the 14 deleted ADR numbers (`doc/adr` has no tombstone list), so "numbers never reused" is not recorded in the tree.

**Layer 2 verdict:** findings — 3 (two Java edits outside the carve-out, nine `.plan` edits); not shown by reading — 3

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java:61-63` (re-read) | a token whose signing key left the JWKS key sets while its loader stays `OK`, hit in the cache | Javadoc says "the cache never returns a token the uncached validation pipeline would reject at the same instant"; `doc/validation/architecture.adoc:36` (re-read) was corrected by this landing to say the opposite. A security-relevant property is over-promised in production source and contradicts the corrected spec. Pre-flagged by the lane as a follow-up, still open | minor |
| L3-2 | `doc/client/specification/retrieval-flow.adoc:147-152` (re-read) | reading the surviving text for the rule deleted ADR-0013 carried | The general rule — refuse a compatibility shim or overload whose implicit value is the one a breaking change made unreachable; source compatibility is not the acceptance test; a `@Deprecated` window only dates the defect; the refusal is scoped to that case — is cut down to the `AuthenticationResult` fact only. This is a "never lose a normative statement" deviation. The leaf found no other normative loss in its sample | minor |
| L3-3 | `doc/adr/0010-…third_classification_state.adoc:8-12` (re-read) | ADR-0010 condensed | the `// supersedes: 5` metadata line is gone, so ADR-0010 no longer machine-records that it supersedes ADR-0005 (a deleted record); other kept ADRs still carry the key | minor |
| L3-4 | `benchmarking/benchmarking-common/README.adoc:180` (re-read) and `benchmarking/doc/local-testing.adoc:31`, `:66` (re-read) | following the shipped README and quick start | `BenchmarkConfiguration.defaults()` does not exist (only `builder()`, `BenchmarkConfiguration.java:67`); the stop command is given without its working directory. Both are the lane's declared advisory leftovers, so a carried-forward finding, not a new one | minor |
| L3-5 | `token-sheriff-validation/doc/configuration/multi-issuer-setup.adoc:132` (re-read, anchor only) | `link:security-settings.adoc#token-type-validation-rfc-9068[…]` | the section is `== Token Type Validation (RFC 9068)` (`security-settings.adoc:81`); Asciidoctor's default id would be `_token_type_validation_rfc_9068`, and no `idprefix` is set, so the anchor looks broken. Pre-existing, not changed by this landing; an Asciidoctor build was not run | not shown by reading |

Reference scan (leaf, regex over every tracked adoc/md/java/xml/properties/yml/yaml/sh/json file outside `.plan` at `66cecbf4`): zero references to the 14 deleted ADRs or the four deleted guides; every relative `xref:`/`link:` file target resolves. A regex scan cannot catch every spelling.

**Layer 3 not shown by reading:** L3-5 (build not run); the unsampled prose of the 14 condensed ADRs was not diffed line by line.

**Layer 3 verdict:** findings — 4 (L3-1 to L3-4), not shown by reading — 1 (L3-5)

Layer-3 findings are reported to the operator at once and are not fixed inline; none is staged as a plan without the operator's word.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| spec | requirement ids unchanged | conforms | 116 ids before and after |
| spec | PR changes 100 files or fewer | conforms | 91 files |
| spec | documentation only, Javadoc links excepted | deviates | the two Java edits are prose/pointer edits outside the carve-out (comment-only) |
| spec | do not touch `.plan/` | deviates | nine `.plan/archived-orchestrators/quality-report` files; the authorisation is not readable from the repo |
| spec | never lose a normative statement | deviates | L3-2 (ADR-0013's general rule) |
| spec | superseded ADR keeps its file unless the operator allows deletion | not_shown_by_reading | 14 deleted; no allowance readable |
| AGENTS.md | ADR Status section is a single status token | conforms | all 17 remaining ADRs read `Accepted` |
| AGENTS.md | cross-references use `xref:`, no dangling references | conforms | except the pre-existing L3-5 anchor |

## Metrics and Anomalies

- Tokens: 31,499,996 total (landing fact; no per-phase breakdown carried).
- Duration: 26,708 s wall (about 7.4 h).
- Model: `claude-sonnet-5-5`, effort `medium`, plugin and executor `0.1.2133`.
- Anomalies:
  - One finalize loop-back: code review caught that condensing ADR-0010 left its Status section as more than one token (`manage-adr` dropped it from the Accepted listing); closed at `ddad84a3`.
  - `manage-change-ledger append --changed-paths-file` was rejected after each per-deliverable commit (TASK-3, 4, 6, 8, 12) because the verb declares only `--changed-paths`; re-read in plugin 0.1.2184, `phase-5-execute/SKILL.md:830` still prescribes the undeclared flag. One dispatch ended in a no-progress reclassification.
  - `manage-solution-outline get-deliverable --number` rejected once (an already tracked recurrence).
  - A direct review-fix commit in 6-finalize was refused for lack of a sanction, then taken through the sanctioned path (second quality gate and push).

## Routing and Merge Behavior

- Review: CodeRabbit and Sourcery. One CodeRabbit inline thread, replied to and resolved. The CodeRabbit review body carried 23 minor comments and the ingest cap clamped it, so about four were triaged; the rest were neither triaged nor answered, yet the pre-merge barrier reported zero pending.
  - Re-read at 2026-11-24T06:32:36Z with `runbook_lane review-gate --pr-number 952`: `merge_allowed: false`, `reason: undecidable_read`, `detail: unreadable review-body section … Minor comments: 23`. The three review numbers the landing carried as `unknown` are therefore `unknown` here too, and the re-read independently shows the 23-comment body is still unread by the parser. `review_body_findings_handled` was not re-verified (no progress file consulted); recorded as **not re-verified**, not as a difference.
- CI/merge: merged through the queue as `66cecbf4`; post-merge the Performance Benchmark workflow (run 45257159120) was still running when the lane's wait expired, nothing had failed.
- Sonar: landing `sonar_open_new_issues=unknown` (the lane reports 0 new-code issues, hotspots not read). This analysis's read is `undecidable` (`head_revision_unavailable`; analysis revision `f719225…`), so the figure is `unknown`, not `0`.
- Surface collisions: none observed against other plans (the corpus was in a single-plan state). The plan's own surface under-declared two Java files (L2 above); both are outside `## Expected Surface` of PLAN-12, which is a finished spec, so no declaration is corrected.

## Reconciliation Actions

- [x] row `status` -> `shipped` — `orchestrator queue --transition PLAN-12 --status shipped`
- [x] row `pr` stamped — `--set-row PLAN-12 --field pr --value 952`
- [x] row `landing` stamped — `--set-row PLAN-12 --field landing --value landings/PLAN-12.md`
- [x] row `plan_marshall_plan_id` stamped — `--set-row PLAN-12 --field plan_marshall_plan_id --value plan-12-documentation-review-consolidation`
- [x] epic.md narrative reconciled: Open Defects and Watches recorded
- [x] incomplete landing recorded as an Open Defect (`inbox landing-check` `complete: false`, four `unknown` keys)
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Operator decision owed (layer 3):** whether to stage a fix plan for L3-1 (`AccessTokenCache` class Javadoc) and L3-2 (restore ADR-0013's general rule in `retrieval-flow.adoc`), and what to do with the untriaged CodeRabbit minor comments (their list is in lesson draft `lesson-review-body-cap.md`). L3-1 touches production source, which no staged plan declares; L3-2/L3-3/L3-4 are documentation and would fit PLAN-13/PLAN-14's documentation surface. Nothing was staged.
- **Staged specs PLAN-01..11, 13, 14:** the documentation paths moved (ADR set 31 -> 17; four Quarkus guides gone). `/plan-orchestrator cleanup` re-grounds them. Any spec that cites a deleted ADR number must be corrected there.
- **Lessons:** the seven candidate-lesson messages are held in the inbox pending the operator's decision on filing `plan-marshall:*` lessons into this repository's store (the store refused them with `wrong_store`).
