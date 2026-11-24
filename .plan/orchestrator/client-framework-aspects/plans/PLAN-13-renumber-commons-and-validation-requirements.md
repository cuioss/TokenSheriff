# PLAN-13: Renumber the COMMONS and VALIDATION requirements to be monotonic

epic: client-framework-aspects
workstream: WS-05

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

The module split of earlier releases left the requirement numbers out of order and with gaps. Token
Sheriff is pre-1.0, so the ids are renumbered outright. This plan renumbers the `COMMONS-*` and
`VALIDATION-*` sets completely and atomically: the requirement documents, every reference in
documentation, Javadoc, tests, log-message tables and the test-implementation mapping. When it lands,
both prefixes are fully consistent. `CLIENT-*` follows in PLAN-14. The split keeps each PR at 100 files
or fewer (operator decision, 2026-11-23: three PRs, cut by prefix).

**Monotonic** means:

- in each requirements document, top-level ids run 1, 2, 3, … in document order, with no gap and no
  reordering;
- sub-ids (`VALIDATION-n.m`) run 1, 2, … within their parent, in document order.

## Deliverables

1. **The renumbering map.** For each prefix it records old id → new id, derived mechanically from
   document order in the consolidated text (after PLAN-12). It is published as a "Former identifiers"
   table at the end of `doc/commons/requirements.adoc` and `doc/validation/requirements.adoc`, or at
   their consolidated locations, so that consumers and older ADRs can translate.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
2. **Apply the map everywhere**, in one pass, with a mechanical tool or script driven by the map, never
   by hand. It covers:
   - the requirement headings and anchors;
   - every xref and textual reference in the documentation (ADRs included);
   - Javadoc and comments in `src/main`;
   - test names, `@DisplayName`s and comments in `src/test`;
   - `doc/developer/test-impl-mapping.adoc`;
   - `doc/LogMessages.adoc`;
   - integration tests;
   - module docs and READMEs.

   No collision may arise mid-rename. The tool maps through temporary tokens (or rewrites in an order
   that avoids collisions), so that `VALIDATION-9` → `VALIDATION-12` and `VALIDATION-12` →
   `VALIDATION-9` cannot corrupt each other.
   - Skills: pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: documenter
3. **Requirement-id integrity guard.** It fails the build when:
   - a requirements document's ids are not monotonic as defined above;
   - any reference in docs, main or test points to an id that does not exist;
   - an id is defined twice.

   It covers all three prefixes. `CLIENT` is listed as pending in the guard's own configuration until
   PLAN-14 removes that entry. Where the guard lives (a JUnit test in the reactor, or a verify-phase
   check) is decided at outline; it runs in CI.
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-core
   - Persona: module-tester

**Done when:**

- Both prefixes are monotonic, and every `COMMONS-*` and `VALIDATION-*` reference in the repository
  resolves.
- The guard enforces this and fails at `9c242518` (the numbering is broken there: for example
  `VALIDATION-11` is missing and `COMMONS-17` sits after `COMMONS-4`).
- `./mvnw verify` is green across the reactor.
- The PR changes 100 files or fewer.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Only ids change. No requirement's wording, normative level or test behaviour changes.
- `CLIENT-*` is not touched (PLAN-14). It stays pending in the guard.
- Consumers that cite Token Sheriff ids (`Audit-Notary/doc/tokensheriff.md`, API Sheriff ADRs and
  comments) are other repositories. They translate through the "Former identifiers" tables, and this
  plan edits nothing there.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: VALIDATION headings appear in the order 1–8, 12, 13, 14, 9, 10, and `VALIDATION-11` is absent — read at `doc/validation/requirements.adoc` § requirement headings (orchestrator grep 2026-11-23).
- OBSERVED: COMMONS headings appear in the order 1–4, 17, 5–12, 16, 13–15 — read at `doc/commons/requirements.adoc` § requirement headings.
- OBSERVED: `VALIDATION-*` ids are referenced in 47 files (393 occurrences) and `COMMONS-*` ids in 25 files (202 occurrences) outside `.plan/`, `target/` and `node_modules/` — read at the repository § `grep -rl` (orchestrator 2026-11-23). The union is at most 72 files; recount at outline, after PLAN-12's consolidation.
- HYPOTHESIS: no test asserts a requirement id at runtime, so renaming ids changes no test behaviour — confirm/refute at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/TokenValidatorTest.java` § uses of `VALIDATION-` (verify-at-outline).

## Expected Surface

- OBSERVED: `doc/`
- OBSERVED: `token-sheriff-validation/`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/`
- OBSERVED: `token-sheriff-quarkus-parent/doc/`
- OBSERVED: `benchmarking/doc/`
- OBSERVED: `benchmarking/README.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-12 (renumber once, over the consolidated text), PLAN-15 (PLAN-12's residual fixes land
  first, so the renumbering runs over corrected text; 15 shared files, strictly sequential).
- Overlaps with: PLAN-14 (shared documents such as `doc/developer/test-impl-mapping.adoc` and the guard);
  strictly sequential.
- Adjacent to: `token-sheriff-client/`, whose `CLIENT-*` ids PLAN-14 renumbers. Client files citing
  VALIDATION or COMMONS ids ARE edited here, so that each prefix lands complete.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-13-renumber-commons-and-validation-requirements.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
