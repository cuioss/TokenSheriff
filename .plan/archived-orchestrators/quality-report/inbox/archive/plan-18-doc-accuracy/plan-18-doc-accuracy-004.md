envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:21Z

# Candidate lesson: PR-body criterion residual - same root as 3dac30

**Source record**: Q-Gate finding 4425ce (phase 6-finalize, functional-review residual at D5.R1, resolved accepted by operator).
**Suggested component**: plan-marshall:phase-6-finalize
**Suggested category**: improvement

## What happened

The functional review emitted a second error-severity finding for the same D5.C3 criterion: "No PR exists yet, so no PR body can name D-13. Only the plan metadata pr_title carries it, and the sole D5 commit message (c8728ac2) omits the finding ID. The create-pr step must write D-13 into the body." One unevidenceable criterion therefore produced two blocking findings (criterion + residual), both needing operator acceptance.

## Corrective rule

Same root as candidate 3dac30 - classify together. Additional angle: the residual itself names the right remedy ("the create-pr step must write D-13 into the body"), so a criterion of this shape should become a create-pr obligation (e.g. a required-content check on the generated PR body) rather than a pre-push review finding. Secondary point: per-deliverable commit messages should carry the finding IDs the deliverable fixes, which would have given the review in-branch evidence.
