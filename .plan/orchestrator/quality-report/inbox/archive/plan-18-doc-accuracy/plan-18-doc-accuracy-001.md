envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:44:57Z

# Candidate lesson: sweep the whole doc/source tree for the defect phrase at outline time, not only the named files

**Source record**: Q-Gate finding 796a31 (phase 4-plan, scope_criterion_validator, under_coverage for deliverable 2, resolved taken_into_account).
**Suggested component**: plan-marshall:phase-3-outline
**Suggested category**: improvement

## What happened

PLAN-18 (docs accuracy, D-2 "commons owns all outbound IdP HTTP") scoped its deliverables to the files the source spec named. The 4-plan scope-criterion validator ran a content search for the D-2 defect phrases ("issues no HTTP", "outbound IdP HTTP", "HTTP plumbing", "commons transport base layer") and found the same defect class in
`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/package-info.java` ("Transport surface of the commons base layer - all outbound IdP HTTP"), which no deliverable covered. The plan looped back 4-plan -> 3-outline -> 4-plan to add it (TASK-2 extended, TASK-7 added).

## Corrective rule

For a doc-accuracy / wording-defect plan, the outline author should run `architecture search --content` for each defect phrase (and its obvious paraphrases) across the whole tree - docs AND Javadoc/README/POM descriptions - before fixing the affected-files set, and either include every hit or record a deliberate exclusion. Scoping to the files the finding report names leaves sibling occurrences for the Q-Gate (or later review rounds) to find.

See also the companion candidate from finding bf9985 (same defect class surfacing in a neighbouring doc during functional review).
