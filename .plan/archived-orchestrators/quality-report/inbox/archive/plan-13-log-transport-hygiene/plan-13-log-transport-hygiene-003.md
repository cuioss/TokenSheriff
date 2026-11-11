envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:20:06Z

# Non-required third-party checks stall review/sonar arms; quota refusal forces PR recreation

**Defect**:
- The `license/cla` status check stayed pending for 30+ minutes and, on its own, made the review-arm and sonar-arm preconditions return `arm_pending`. It had to be overridden with a logged decision.
- CodeRabbit quota refusals forced closing PR #859 and recreating it as #861 just to trigger a fresh review.

**Corrective action**:
- Arm preconditions should ignore (or separately classify) non-required third-party status checks instead of treating any pending check as blocking.
- The review-bot quota path should offer a retry-after (wait for the quota window and re-request) rather than requiring PR recreation.

**Evidence**: plan-13-log-transport-hygiene decision log (arm_pending override), PR #859 closed / #861 opened.

**Suggested component**: plan-marshall:automatic-review / plan-marshall:tools-integration-ci
