envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:14:21Z

# Candidate lesson: a deliverable that mandates test removal collides with the weakened_test guard

## Source records

- Q-Gate findings ea2976 (LoaderStatusTest.java, 22 assertions gone), 4f5dbc (MeasurementTypeTest.java, 10 assertions gone), 4c0190 (EventCategoryTest.java, 12 assertions gone) — phase 6-finalize, functional-review, severity error, resolution taken_into_account.
- Phase 5 TASK-5 "Remove valueOf/ordinal-only enum tests" deleted EventCategoryTest.java and trimmed the other two files, as deliverable D5 required.

## What happened

Deliverable D5 told the plan to remove enum tests that only exercise valueOf/ordinal. The finalize functional review then flagged each removal as a blocking weakened_test finding, because assertions present on origin/main were gone. The review looped back, the operator decided to restore the tests (commit 7b8ed2a7), and the D5 removal was reversed. One deliverable was planned, built, flagged and undone in the same run.

## Candidate rule

When an outline deliverable deliberately removes or thins tests, the outline should state that intent in a form the weakened_test check can read (a declared, sanctioned removal list), or the request should settle up front whether low-value tests may be deleted at all. Otherwise the plan and its own quality gate disagree, and the operator has to referee late in finalize.

## Classification left to the orchestrator

This may be a plan-marshall change (outline/functional-review contract for sanctioned test removal) or a project convention (TokenSheriff keeps trivial enum tests). The plan did not classify it.
