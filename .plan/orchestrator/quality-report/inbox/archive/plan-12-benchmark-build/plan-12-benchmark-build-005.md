envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=candidate-lesson
created=2026-11-01T17:31:47Z

# A quota-limited required review bot (CodeRabbit) blocks the automatic-review gate

Signal: automatic-review (signal_automated_review_count = 1).

## What happened

CodeRabbit is a required review bot for this repository. It produced one actionable finding (60646d), and the run fixed it as TASK-10. After that it was quota-limited and posted no completed review for the final head. The automatic-review step could not reach a completion state for a required bot. The only way out was an operator override to close the review step.

## Suggested direction

- Have the bot-completion check recognise a quota or rate-limit notice from the bot as its own state, distinct from "pending". Surface it as an explicit decision for the operator (wait, override, or re-trigger later), so it does not sit as an open wait.
- Consider a configured policy for required bots that are quota-limited (for example, accept the last completed review on an earlier head when the later diff is only fix commits for that review's findings), and record which policy was applied.

## Component

plan-marshall:automatic-review / plan-marshall:workflow-integration-github (bot_completion)
