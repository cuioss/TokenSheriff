# WS-05: Documentation baseline — review, consolidation, ADR reduction, requirement renumbering

epic: client-framework-aspects

> Charter document for one workstream. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Bring Token Sheriff's documentation to a reviewed, compressed baseline before this epic adds any
requirement, ADR or module:

- a full review of every document and diagram against the documentation standards and the current code;
- a drastic consolidation of the document count, approved by the operator;
- a reduction of the ADRs to real decisions;
- a complete, monotonic renumbering of the requirement ids that the module split left out of order
  (Token Sheriff is pre-1.0), guarded so it cannot break again.

The workstream runs FIRST (operator decision, 2026-11-23), and it closes when all three PRs have landed.

## Scope

- In scope: `doc/`, every module `doc/` folder, all READMEs, `AGENTS.md`, and the requirement-id
  references in Javadoc and tests.
- Out of scope: behaviour changes of any kind; new requirements (WS-02 to WS-04).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-12-documentation-review-consolidation-and-adrs | staged | PR 1: review, consolidation (operator approves the target structure), ADR reduction, fixes; ids frozen |
| PLAN-13-renumber-commons-and-validation-requirements | staged | PR 2: COMMONS and VALIDATION monotonic, plus the id-integrity guard (CLIENT pending) |
| PLAN-14-renumber-client-requirements | staged | PR 3: CLIENT monotonic, guard complete |

## Sequencing and Surface Notes

- Strictly sequential: PLAN-12 → PLAN-13 → PLAN-14. Every plan of WS-01 to WS-04 depends on PLAN-14.
- The PR cut is by prefix, so each PR is self-consistent and stays at 100 files or fewer (operator
  decision: three PRs).
- After PLAN-12 and again after PLAN-14, the orchestrator re-grounds all other staged specs with
  `cleanup`: documentation paths move, and the requirement ids they cite change.
