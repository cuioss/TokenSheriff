envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:17Z

component=plan-marshall:automatic-review
category=improvement
source_plan=plan-04-quarkus-startup-health
source_records=016c7f, fd901a (pr-comment, fixed), script-failure cluster "head_not_validated_by_gate refusals"

# Every review-driven fix commit restarts the full gate/CI/review cycle, and CodeRabbit's hourly quota times out the required-reviewer check

## What happened

On PR #834, each CodeRabbit round produced a fix commit:

- Round 1 (review 016c7f plus inline b1f9f8 and 651a2d) led to TASK-14 and doc fixes.
- Round 2 (review fd901a) led to TASK-15.

Each new commit moved HEAD, so the merge path refused with `head_not_validated_by_gate` until the
quality gate, CI and the bot review had run again on the new head. Both CodeRabbit review bodies
state "Your plan provides up to 1 included review per hour; 0 remain after this review." The second
re-review therefore had to wait for the hourly quota, and the required-reviewer check timed out
before the review arrived.

## Rule

- Batch review-driven fixes: collect every actionable comment of a review round (inline and nitpick)
  and land them in ONE fix commit. Do not push one commit per comment or per task.
- Treat trivial nitpicks (test-assertion tightening, wording) as candidates for a single follow-up
  commit, or defer them to a follow-up plan when the quota is exhausted.
- When the bot reports `0 remain`, size the required-reviewer wait to at least the quota window
  (about 60 minutes) instead of the default timeout, or surface the quota state to the operator
  instead of letting the check time out.

## Impact

Two small review rounds turned into several full gate/CI/review cycles and a timed-out required
check. That is the main source of wall-clock cost in finalize for this plan.
