envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:46:28Z

# Candidate lesson: owed architecture hint (insight) for a recurring accepted ci_build_failure

**Signal:** preference-emitter (disposition recurrence within one plan)
**Records:** finding hashes d89b59 and 7100eb (type triage, `[ci_build_failure]`), both resolved `accepted` in plan plan-15-final-fix-sweep, finding component `plan-marshall:phase-6-finalize`; recurrence 2, threshold `preference_min_recurrence` 2.

## Owed hint

- **Verb:** `architecture enrich insight`
- **Target `--module`:** `plan-marshall:phase-6-finalize` (the attribution the findings carry; it is a plan-marshall component, not an architecture module of this repository, so the orchestrator-side pickup has to choose the module the call can resolve against, or drop the hint)
- **Generalized hint text:** the project accepts a failed CI build leg whose only failing test is a known intermittent per-JDK probe that passes on rerun while the base branch is green, and treats it as a rerun-or-fix-the-probe decision rather than a defect of the change under review.

## Confidence

Low. Both acceptances in this plan were stop-gaps for one flaky probe (`BackChannelHttpBodyPhaseTest` on the JDK 27 leg); the operator later chose to fix the probe instead of continuing to accept the failure (see candidate 17 and 18). The hint states what the plan did, and the orchestrator may reasonably decide it is not a durable preference.

The step wrote no `architecture enrich` call itself: it runs after the merge, where such a write would land on `main` as an uncommitted tracked-file change.
