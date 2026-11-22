envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:30Z

component=plan-marshall:phase-5-execute
category=improvement
created=2026-11-13
source=qgate 5-execute 21958e (resolution accepted)

# Scope-creep check flags files from earlier committed deliverables and operator-requested fixes

## What happened

`scope_creep_check` reported 7 files outside declared scope (threshold 5). Four were the operator-requested test-jar pom
fix (62b7f847, disclosed in PR #899). The other three (`quarkus-config-doc.adoc`, `domain/token/package-info.java`,
`UncheckedThrowBoundaryTest.java`) belonged to earlier committed deliverables that functional and code review had already
covered. The finding was accepted with a manual explanation.

## Proposed rule

Files touched by an earlier deliverable's own commit, or by an operator-sanctioned out-of-footprint fix, should enter the
declared scope (or an explicit sanctioned-residual list) when they are committed, so the scope-creep check does not ask
for the same disposition again.
