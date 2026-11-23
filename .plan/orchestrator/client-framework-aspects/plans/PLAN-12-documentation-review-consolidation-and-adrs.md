# PLAN-12: Documentation baseline — full review, consolidation, ADR reduction

epic: client-framework-aspects
workstream: WS-05

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.
> ⛔ This is the epic's FIRST plan, by operator decision (2026-11-23). Every other plan depends on
> PLAN-14, which depends on this one.

## Objective

Bring Token Sheriff's whole documentation to a reviewed, compressed baseline before any new requirement,
ADR or module is added. The documentation is about 22,800 lines:

- `doc/`: 79 files, 14,285 lines of AsciiDoc;
- the module `doc/` folders: 32 files, 6,098 lines;
- the READMEs: about 2,400 lines;
- 31 ADRs among them, 4,982 lines, about 160 lines per ADR.

The main Java is about 53,000 lines. The plan does four things:

1. **Reviews everything** with the documentation skills: format, links, content and tone. It checks
   every diagram (SVG, PlantUML, ASCII) against the diagram standards AND against the current code.
2. **Proposes and applies a drastic consolidation.** The operator wants the number of documents
   compressed as far as possible.
3. **Reviews every ADR** for whether it records a decision or merely restates an implementation
   definition, and reduces the set accordingly.
4. **Applies the review's fixes.**

Requirement renumbering is NOT this plan's job (PLAN-13 and PLAN-14). This plan keeps every requirement
id unchanged, so that the renumbering runs once over the final, consolidated text.

## Deliverables

1. **Review report** (read-only phase; nothing modified yet). It is persisted as a plan artifact and
   summarised in the PR, with findings by file and priority. Steps:
   - Load `pm-documents:ref-documentation` and run its comprehensive-review workflow on `doc/` and on
     every module documentation set (`token-sheriff-validation/doc/`, `token-sheriff-client/doc/`,
     `token-sheriff-quarkus-parent/doc/`, `benchmarking/doc/`, all `README*` files): format, then links,
     then content and tone.
   - Load `pm-documents:ref-svg-diagrams` and `pm-documents:ref-ascii-diagrams`. Check every diagram
     against those standards and against the current code (class, package and module names; flows).
   - Where a recipe is needed, it resolves through `resolve-recipe`. A key that doesn't resolve is
     reported with the available recipes, never guessed. This includes `pm-documents:recipe-doc-verify`,
     `recipe-verify-architecture-diagrams` and `recipe-verify-ascii-diagrams` where they apply.
   - Skills: pm-documents:ref-documentation, pm-documents:ref-asciidoc, pm-documents:ref-svg-diagrams, pm-documents:ref-ascii-diagrams
   - Persona: documenter
2. **Consolidation proposal, then application.** A target documentation structure that compresses the
   number of documents as far as the content allows, stating for each move its source, its destination
   and why. It states the file count and the line count before and after, so the reduction is measured,
   not asserted. Candidates to weigh, as leads and not decisions:
   - topics with two homes (module `doc/` folders versus `doc/{module}/`);
   - dated snapshot analyses (`benchmarking/doc/Analysis-03.2026-*`);
   - per-module `references.adoc` and README stubs;
   - performance and GraalVM guides that may be stale;
   - specification documents that restate requirements.

   ⛔ **The operator approves the target structure before anything is moved or deleted** (the plan's
   refine/outline escalation). Then apply it: merge, move and delete as approved, and fix every
   cross-reference (xrefs, README links, Javadoc `@see`/links into `doc/`, `AGENTS.md`).
   - Skills: pm-documents:ref-documentation, pm-documents:ref-asciidoc
   - Persona: documenter
3. **ADR review and reduction.** For each of the 31 ADRs, classify and justify one of:
   - **Decision**: keep, condensed to context, decision, consequences and rejected alternatives.
   - **Implementation definition**: the normative detail moves into the matching architecture or
     specification document, and the ADR is condensed to the decision it records, or superseded.
   - **Obsolete or merged**: superseded by the surviving ADR.

   Long ADRs that mostly specify behaviour (the 300–400-line ones, such as 0031) are the first
   candidates. Defaults: ADR numbers are never reused, and a superseded ADR keeps its file with status
   `Superseded by ADR-NNNN`. Whether superseded ADRs may be deleted instead (pre-1.0) is put to the
   operator together with the consolidation proposal. ADR-0001's split-out triggers and everything
   ADR-0032 (PLAN-01) will cite stay intact.
   - Skills: pm-documents:ref-documentation, pm-documents:ref-asciidoc
   - Persona: documenter
4. **Apply the review findings**: format, broken links, content and tone, stale statements against the
   code.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
5. **Diagram fixes**: correct or redraw every diagram the review found wrong against the code or the
   standards. Remove diagrams whose documents were consolidated away.
   - Skills: pm-documents:ref-svg-diagrams, pm-documents:ref-ascii-diagrams
   - Persona: documenter

**Done when:**

- The review report exists with every finding dispositioned (fixed, or declined with a reason).
- The operator-approved target structure is applied, and the PR states the before and after file and
  line counts.
- Each of the 31 ADRs carries a recorded classification.
- The AsciiDoc build of the documentation is clean, with no broken xref or link.
- No requirement id changed, which holds at `9c242518` and after: the set of `CLIENT-`, `VALIDATION-`
  and `COMMONS-` ids across the repository is identical before and after.
- The PR changes 100 files or fewer. If the approved consolidation needs more, stop after the proposal
  and report; the orchestrator splits the plan by documentation area.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Documentation only: no production or test source changes, except Javadoc links that point into moved
  documents.
- Requirement ids are frozen in this plan (PLAN-13 and PLAN-14 renumber them).
- Never lose a normative statement. A sentence that is removed is either redundant with its surviving
  home (named in the proposal), or obsolete against the code (named with its evidence).
- Do not touch `.plan/`.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: `doc/` holds 79 files (62 AsciiDoc, 16 SVG, 1 PNG) with 14,285 AsciiDoc lines; the module documentation folders hold 32 AsciiDoc files with 6,098 lines; there are 18 README files — read at `doc/` and `*/doc/` § `find`/`wc -l` (orchestrator 2026-11-23).
- OBSERVED: there are 31 ADRs totalling 4,982 lines — read at `doc/adr/` § `wc -l`.
- OBSERVED: dated benchmark analyses exist as separate documents — read at `benchmarking/doc/` § `Analysis-03.2026-Integration.adoc`, `Analysis-03.2026-JFR-Profiling.adoc`, `Analysis-03.2026-Latency-Decomposition.adoc`, `Analysis-03.2026-Micro.adoc`.
- OBSERVED: configuration topics are documented both in module folders and in `doc/` — read at `token-sheriff-validation/doc/configuration/` and `doc/validation/` § directory listings.
- HYPOTHESIS: a drastic reduction of the document count is achievable without losing normative content — confirm/refute in Deliverable 2's proposal, against `doc/README.adoc` § the document index (verify-at-outline). The before and after counts settle it.
- HYPOTHESIS: most of the ADR line count is implementation definition rather than decision — confirm/refute per ADR at `doc/adr/0031-Bearer_extraction_scope_challenge_sanitiser_escape_and_bounded_body_reads_are_public_commons_helpers.adoc` § Decision and the other long ADRs (verify-at-outline).

## Expected Surface

- OBSERVED: `doc/`
- OBSERVED: `token-sheriff-validation/doc/`
- OBSERVED: `token-sheriff-client/doc/`
- OBSERVED: `token-sheriff-quarkus-parent/doc/`
- OBSERVED: `benchmarking/doc/`
- OBSERVED: `README.md`
- OBSERVED: `token-sheriff-validation/README.adoc`
- OBSERVED: `token-sheriff-client/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/README.adoc`
- OBSERVED: `benchmarking/README.adoc`
- OBSERVED: `benchmarking/benchmark-core/README.adoc`
- OBSERVED: `benchmarking/benchmark-integration-wrk/README.adoc`
- OBSERVED: `benchmarking/benchmarking-common/README.adoc`
- OBSERVED: `AGENTS.md`

## Dependencies and Sequencing

- Depends on: none. It runs FIRST.
- Overlaps with: every other spec in this epic touches some file under `doc/`, which is why all of them
  wait for this plan and the renumbering (PLAN-13, PLAN-14).
- Adjacent to: Javadoc in `src/main` that links into `doc/`; only link targets change.
- After it lands, the orchestrator re-grounds every other staged spec's `## Expected Surface` against the
  consolidated layout (`/plan-orchestrator cleanup`), because documentation paths will have moved.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-12-documentation-review-consolidation-and-adrs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
