envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:44:11Z

# Candidate lesson: CodeRabbit quota notice left the loop-back head without a review, but participation still read as participated

- Source: automated-review step on PR #879, reported by the finalize dispatcher
- Signal: automated review

## What happened

After the loop-back pushed the fixes (commits 56710e6 and 57a7a40), CodeRabbit posted a quota notice instead of a new review, so no review exists for the loop-back head. The review-bot participation check returned "participated" based on the first-pass review of the earlier head (f4e8dc6). The fix commits were therefore merged without a bot review of the final head, and the gate did not report this.

## Possible corrective rule

The participation check for a review bot should be bound to the current head SHA. A quota or rate-limit notice at the current head should be reported as "not reviewed at this head" (and surfaced to the operator), not satisfied by an earlier review of a different head.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
