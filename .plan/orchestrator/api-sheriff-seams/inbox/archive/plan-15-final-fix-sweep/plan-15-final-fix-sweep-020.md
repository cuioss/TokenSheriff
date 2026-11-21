envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:46:29Z

# Candidate lesson: owed architecture hint (insight) for a recurring accepted ci_cancelled

**Signal:** preference-emitter (disposition recurrence within one plan)
**Records:** finding hashes d843fa and adfa32 (type triage, `[ci_cancelled]`, the JDK 25 and JDK 21 legs of the build matrix), both resolved `accepted` in plan plan-15-final-fix-sweep, finding component `plan-marshall:phase-6-finalize`; recurrence 2, threshold `preference_min_recurrence` 2.

## Owed hint

- **Verb:** `architecture enrich insight`
- **Target `--module`:** `plan-marshall:phase-6-finalize` (the attribution the findings carry; as for candidate 19 it is not an architecture module of this repository, so the pickup chooses a resolvable module or drops the hint)
- **Generalized hint text:** the project accepts a cancelled CI build leg as a consequence of a sibling matrix leg failing, because the matrix cancels the remaining legs when one fails; the cancellation is not a finding on its own and the failing leg is the one to act on.

## Confidence

Low to medium. The two cancelled legs only followed the failed JDK 27 leg in the same run; the pattern is a property of the matrix configuration rather than of the code, which is why it may belong in a CI note rather than in an architecture hint.

The step wrote no `architecture enrich` call itself: it runs after the merge, where such a write would land on `main` as an uncommitted tracked-file change.
