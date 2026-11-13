envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:12Z

component=plan-marshall:build-maven
category=bug
created=2026-11-13
source=qgate 5-execute 5512a9 (resolution rejected)

# module-tests canonical 'mvn test' cannot resolve a sibling test-jar in a multi-module reactor

## What happened

The phase-5 module-tests step resolved to plain `mvn test` over the whole reactor. The `test` phase never packages the
`token-sheriff-validation` test-jar, so `token-sheriff-client` (test) and `benchmark-core` (main) failed to compile
(package `de.cuioss.sheriff.token.validation.test` missing). 2577 tests passed before the reactor stopped; all 7 failing
paths were outside the plan footprint. The Q-Gate finding was rejected as "not a plan defect" because the whole-tree
`verify -Ppre-commit` (which packages test-jars) ran green on the same HEAD (4483 tests).

Later, the pre-push gate hit the same pre-existing reactor test-compile failure. The project-side repair was to declare
`<type>test-jar</type>` on the consuming dependencies in four poms (commit 62b7f847, disclosed in PR #899). The
underlying plan-marshall defect is NOT fixed: the build-maven reactor `module-tests` canonical command still runs a
lifecycle phase that cannot satisfy test-jar consumers.

## Proposed rule

A reactor-wide test canonical must either run a phase that packages test-jars (`verify`, or `test-compile` + `jar:test-jar`)
or detect test-jar consumers and scope accordingly. A green `verify` on the same HEAD should not be the only way to
discover the gap.

## Evidence

- build log: build-results/default/maven-2026-11-12-145041.log (failing), maven-2026-11-12-145330.log (green verify)
- pom fix commit: 62b7f847
