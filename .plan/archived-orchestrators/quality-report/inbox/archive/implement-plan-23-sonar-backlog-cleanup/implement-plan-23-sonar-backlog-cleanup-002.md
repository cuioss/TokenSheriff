envelope_version=1
sender_type=plan
sender_id=implement-plan-23-sonar-backlog-cleanup
epic=quality-report
kind=candidate-lesson
created=2026-11-05T07:53:29Z

component=plan-marshall:phase-6-finalize
category=improvement
source_plan=implement-plan-23-sonar-backlog-cleanup
signal=pre-push-quality-gate module-tests arm degradation on Maven

# pre-push-quality-gate reports the module-tests arm as un-gated on every Maven push even when the whole-tree verify ran the tests green

## Symptom

On a Maven project (TokenSheriff), the finalize `pre-push-quality-gate` step logs on every push:

> No module-tests canonical resolves in this project, so no separate module-tests gate could be run and the divergence question cannot be answered by the resolve-test-scope seam. The whole-tree verify -Ppre-commit did execute 3680 tests (green), but the module-tests arm itself is UN-GATED at finalize for this push. Proceeding on honest degradation.

The tests did run and pass inside the whole-tree `verify`, yet the gate reports the module-tests arm as un-gated. The warning fires on every Maven run, so it carries no information and trains operators to ignore it.

## Root cause

The pre-push-quality-gate doc and its resolve-test-scope seam assume a pyprojectx-style layout where `module-tests` is a separate canonical command. The Maven build resolves `verify` (which runs surefire tests) but no distinct `module-tests` canonical, so the arm has nothing to resolve and degrades.

## Corrective action

- Treat a resolved whole-tree `verify` that runs the test phase as satisfying the module-tests arm for build systems where tests are part of `verify` (Maven, likely Gradle `check`), and record it as gated-by-verify rather than un-gated.
- Or let `plan-marshall:build-maven` declare a `module-tests` canonical (e.g. `test` / `verify` scoped to modules) so the seam resolves.
- Either way, stop emitting a per-push WARNING for a configuration that is normal for the build system.

## Evidence

Plan `implement-plan-23-sonar-backlog-cleanup` (PR #855, merged as 0c56583e), 6-finalize work log, 2026-11-05T06:50:36Z.

## Routing note

Two candidate owners: `plan-marshall:phase-6-finalize` (the gate's arm-satisfaction rule) or `plan-marshall:build-maven` (declare the canonical). This candidate was first filed into the global lessons store by an earlier run that missed the orchestration verdict (2026-11-05-07-002, now tombstoned); it is re-routed here and is not a duplicate of any live lesson.
