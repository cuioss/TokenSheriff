envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:06Z

component=plan-marshall:phase-3-outline
category=anti-pattern
source_plan=plan-04-quarkus-startup-health
source_records=253267, 699f58 (6-finalize Q-Gate, operator-accepted)

# A success criterion about the PR body can never be evidenced by the pre-PR functional review

## What happened

Deliverable 5 of plan-04 had criterion D5.C3: "The PR body records whether the mappers-inactive
hypothesis was confirmed or refuted", with residual D5.R1. The finalize functional review grades the
head commit before `create-pr` runs, so no PR body exists at grading time. Both findings were
`error` severity (253267 unevidenced_criterion, 699f58 residual). They could only be closed by an
operator decision that accepted them on a condition: the PR body must state the refutation.

## Rule

- When authoring success criteria in the outline, do not phrase a criterion as a property of an
  artifact that only exists after the gate that grades it (PR body, PR comments, merge state).
- Phrase it as an evidenceable pre-PR artifact instead. Examples: a statement in the change ledger,
  the plan's `request.md`/outline, or a doc or ADR in the tree. Alternatively route the PR-body text
  through the finalize PR-body template as an input, not as a graded criterion.
- Consider a Q-Gate check at outline time that flags criteria referring to "PR body" or "PR
  description" as unevidenceable.

## Impact

The criterion produces a guaranteed error finding at finalize. Closing it needs an operator stop,
which adds a manual interruption to an otherwise automated run.
