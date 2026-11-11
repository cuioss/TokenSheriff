envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:12Z

# Candidate lesson: a success criterion naming the PR body cannot be evidenced by the pre-push functional review

**Source record**: Q-Gate finding 3dac30 (phase 6-finalize, functional-review unevidenced_criterion at D5.C3, resolved accepted by operator).
**Suggested component**: plan-marshall:phase-3-outline (criterion authoring) / plan-marshall:phase-6-finalize (functional review grading)
**Suggested category**: improvement

## What happened

Deliverable 5 carried the success criterion "D-13 is recorded against this PR in the PR body". The functional review runs before push, when no PR exists, so the criterion was graded unevidenced (severity error) and needed an operator acceptance at finalize to pass. Nothing was actually wrong with the deliverable.

## Corrective rule

Either (a) the outline author should not phrase deliverable success criteria against artifacts that only exist after create-pr (PR title/body, PR labels, review state) - put them on the plan metadata (`pr_title`) or the commit message instead; or (b) the functional review should classify such criteria as deferred-to-create-pr and grade them at the create-pr step rather than as unevidenced. Either route removes a guaranteed operator interrupt.
