envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:10Z

# Quota-limited required review bot cannot cover a late final HEAD

**Source plan:** plan-01-jwks-loader-lifecycle (PR #830)
**Signal:** automatic-review (signal_automated_review_count=1)
**Suggested component:** plan-marshall:automatic-review / plan-marshall:manage-locks (review-bot rate window)

## What happened

CodeRabbit is a required review bot with an hourly quota (1 review per hour). Its last review covered f3dd50e9. A docs-only ADR commit then moved HEAD to 6198234e, and the bot was `refused_awaitable (quota)` for the new HEAD. The barrier reported `participation_complete=false` with 0 pending findings. The PR could only merge after the operator recorded a barrier-ask-override.

## Corrective rule (candidate)

When a required bot has a tight quota, every extra push in finalize costs one quota slot and can leave the final HEAD without bot coverage. Group all pushes before the review-bot round, so the bot's one review lands on the final HEAD. If a follow-up commit cannot be avoided, plan for the quota wait (about one hour), or decide early to use the override, instead of finding out at the merge barrier. A related idea for the epic: the barrier could treat a docs-only delta after full bot coverage as a distinct, lower-risk gap class.

## Evidence

- Work log 20:09:27Z: "required bot coderabbit is refused_awaitable (quota) and its last review covers f3dd50e9, not the current HEAD 6198234e".
- Work log 20:38:09Z: "merged past the CodeRabbit coverage gap under the operator's barrier-ask-override".
