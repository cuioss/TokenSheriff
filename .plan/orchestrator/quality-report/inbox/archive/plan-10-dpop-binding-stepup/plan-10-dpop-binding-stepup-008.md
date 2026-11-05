envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:57Z

# CodeRabbit quota limit left the final commit unreviewed; merged under a barrier-ask-override

**Source**: decision log 63a9a6, cdb885, 82faee (19:27-19:35) and 0e719d (20:27), 2026-11-04.

## What happened

After the inline-fix commit 310669e, `automatic-review` asked CodeRabbit for a re-review (trigger B). CodeRabbit answered with a quota rate-limit refusal (stated ETA 17 minutes). The 600 s re-review wait timed out, `re_review_on_timeout=ask` escalated to the operator, and the operator chose merge-anyway: the head advanced UNREVIEWED. CodeRabbit had reviewed the plan in full at 25e892e; only 310669e (its own two suggested fixes, including the negative-max_age guard) was unseen. At merge time the pre-merge review barrier proceeded with `unproven_bots=coderabbit,sourcery` under an authorization `barrier-ask-override` granted over `review-barrier-gap`.

## Candidate rule

A bot's quota refusal is a known, distinguishable answer, not a silent timeout. The re-review wait could recognise it, read the stated ETA, and either wait that long when it fits the budget or escalate immediately with "quota, ETA N min" rather than burning the full 600 s. Separately, when the unseen delta consists only of the bot's own accepted suggestions, that fact should be recorded with the override so the merge-anyway decision is auditable as low-risk.
