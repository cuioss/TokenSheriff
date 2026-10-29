envelope_version=1
sender_type=plan
sender_id=implement-plan-02-refresh-family-rotation
epic=quality-report
kind=candidate-lesson
created=2026-10-28T21:34:22Z

component=plan-marshall:workflow-integration-github
category=improvement
dedup=checked against active corpus (0 lessons): no overlap

# CodeRabbit hourly review limit and incremental "No files to review" after a content-identical rebase

CodeRabbit's one-included-review-per-hour limit stalled the re-review twice. Separately, its incremental reviewer answered an ordinary trigger with "No files to review" after a content-identical rebase (the tree was unchanged, so there was no incremental delta), leaving the PR without a current review. The registry `--escalated` full-review command was the only way to obtain a current review.

## Solution

When a re-review is needed after a rebase or a rate-window stall: (1) account for the one-review-per-hour window before scheduling re-review rounds and batch fixes so fewer rounds are spent; (2) treat a "No files to review" reply after a content-identical rebase as "incremental has nothing to diff", not as a clean review, and go straight to the registry `--escalated` full-review command rather than re-sending the ordinary trigger.

## Impact

Avoids repeated stalled waits and a false "reviewed" state on a PR whose head has no current bot review.
