envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:58Z

# Candidate lesson: bean-archive index scope check missed @BearerAuth methods inherited from non-bean dependency classes

- Signal: automatic review (CodeRabbit), pr-comment finding 1d18b5 on PR #925, `TokenSheriffProcessor.java:319`; fixed in run via TASK-16 (commit a0c2b4ab, follow-up 0e84e8a5)

## What happened

Operator decision Q7 (decision 450d10) narrowed `validateAnnotationScopes` from `CombinedIndexBuildItem` to
`BeanArchiveIndexBuildItem` so ArC-generated beans are covered. CodeRabbit pointed out that the bean-archive index does not
enumerate classes from non-bean dependencies: an abstract base class there can declare a `@BearerAuth` method whose scopes
are then never merged with a subclass's class-level `@BearerAuth`, so a 33 + 32 scope union passes deployment and fails at
runtime. Triage first deferred the comment to the operator because the claim could not be verified in triage; the
operator chose a fix task. TASK-16's red proof showed the gap was real: ArC's computing index resolves the base class on
demand via `getClassByName` but does not list its annotations via `getAnnotations`. The fix scans the combined index as
well and removes duplicate violations.

## Evidence

- Decision log 2a51a2 (deferred to operator), 3e512e (red proof, expected 1 validation error, was 0), 440caf.
- Work log 934bd7 (TASK-016 added).

## Observation for the epic

Narrowing a Quarkus build step to the bean-archive index to gain generated beans loses non-bean dependency classes; a scope
check that merges inherited members needs both indexes.
