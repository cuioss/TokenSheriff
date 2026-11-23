# PLAN-14: Renumber the CLIENT requirements to be monotonic

epic: client-framework-aspects
workstream: WS-05

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Renumber the `CLIENT-*` requirement set completely and atomically, as PLAN-13 did for COMMONS and
VALIDATION. **Monotonic** means the ids run 1, 2, 3, … in document order, with no gap and no
reordering. After this plan, every requirement prefix in the repository is monotonic and fully
resolvable. The integrity guard PLAN-13 introduced then enforces all three prefixes.

## Deliverables

1. **The renumbering map** for `CLIENT-*`, derived mechanically from document order in the consolidated
   text. It is published as a "Former identifiers" table in `doc/client/requirements.adoc`, or its
   consolidated location.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
2. **Apply the map everywhere** with the same map-driven, collision-safe tool as PLAN-13. It covers the
   requirement headings and anchors, the documentation (ADRs included), Javadoc and comments in
   `token-sheriff-client` and the client Quarkus modules, tests and integration tests,
   `doc/developer/test-impl-mapping.adoc`, `doc/LogMessages.adoc`, and the module docs and READMEs.
   - Skills: pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: documenter
3. **Guard completion.** Remove `CLIENT` from the integrity guard's pending list, so all three prefixes
   are enforced.
   - Skills: pm-dev-java:junit-core
   - Persona: module-tester

**Done when:**

- `CLIENT-*` is monotonic and every reference resolves.
- The guard enforces all three prefixes. Its CLIENT check fails at `9c242518`, where CLIENT headings run
  1–14, 23–25, 27, 29, 15–20, 28, 30, 21, 22, 26.
- `./mvnw verify` is green across the reactor.
- The PR changes 100 files or fewer. This is tight: 99 files referenced `CLIENT-*` at `9c242518`.
  Recount after PLAN-12. If the change set exceeds 100, stop and report, and the orchestrator splits it
  (main + docs, then tests).
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Only ids change. No wording, normative level or behaviour changes.
- Other repositories that cite `CLIENT-*` ids translate through the "Former identifiers" table: API
  Sheriff, and Audit Notary's `doc/tokensheriff.md` (which cites CLIENT-26, -27, -29 and the planned
  CLIENT-31..36).
- The staged specs of this epic cite CLIENT ids. The orchestrator re-grounds them after this plan lands;
  this plan does not edit `.plan/`.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: CLIENT headings appear in the order 1–14, 23, 24, 25, 27, 29, 15–20, 28, 30, 21, 22, 26 — read at `doc/client/requirements.adoc` § requirement headings (orchestrator grep 2026-11-23).
- OBSERVED: `CLIENT-*` ids are referenced in 99 files (641 occurrences) outside `.plan/`, `target/` and `node_modules/` — read at the repository § `grep -rl` (orchestrator 2026-11-23). Recount at outline.

## Expected Surface

- OBSERVED: `doc/`
- OBSERVED: `token-sheriff-client/`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/`
- OBSERVED: `token-sheriff-quarkus-parent/doc/`

## Dependencies and Sequencing

- Depends on: PLAN-13.
- Overlaps with: PLAN-13 (shared documents and the guard); strictly sequential.
- Adjacent to: every other spec in this epic. They all wait for this plan, because they add new CLIENT
  ids or cite existing ones.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-14-renumber-client-requirements.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
