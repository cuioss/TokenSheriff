envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=candidate-lesson
created=2026-10-29T13:11:45Z

## Candidate lesson: the review-bot quota (1 included CodeRabbit review per hour) leaves the fix-up head unreviewed; merge went through only on an operator override

**Source**: plan decision log:
- 2026-10-29T10:12:32Z (c3357f): automatic-review re-review timeout (trigger B) at head 467a857b, escalated to the operator.
- 2026-10-29T11:12:09Z (757014): "coderabbit answered only with quota refusals: user chose merge-anyway on the condition that at least one valid CodeRabbit review exists ... advancing UNREVIEWED head_sha=467a857b".
- 2026-10-29T12:48:50Z (5e0500) / 12:49:21Z (4b34bd): pre-merge barrier "participation incomplete (coderabbit refused_awaitable/quota, sourcery optional refused_hard/quota; 0 pending comments)", proceeding "under authorization barrier-ask-override granted over review-barrier-gap at 467a857b".
- The first CodeRabbit review body (finding e94755) states: "Your plan provides up to 1 included review per hour; 0 remain after this review."

**What happened**: the first review on 9648063c used up the hourly allowance. The review-fix push (467a857b, the TASK-4 and Sonar fixes) therefore never received a review from the required bot. The run waited the full 600 s re-review timeout, asked the operator, and merged under a head-bound barrier-ask-override grant. The last commit landed without bot review, and the timeout plus the operator round-trip cost about an hour of elapsed time.

**Corrective rule (proposed)** (for the orchestrator to judge):
- When the first review body reports the quota as exhausted, record that so the re-review wait does not spend its full timeout, and surface the override question at once.
- Consider batching review-fix work before the first push (run Sonar and self-review earlier), so that the one included review per hour covers the final head.
- Treat "merged with the last commit unreviewed by the required bot" as a recorded process cost on the landing, not a silent pass.

**Scope**: plan-marshall automatic-review re-review timing and the pre-merge comment barrier; project review-bot configuration (CodeRabbit plan quota) across the epic's plans.
