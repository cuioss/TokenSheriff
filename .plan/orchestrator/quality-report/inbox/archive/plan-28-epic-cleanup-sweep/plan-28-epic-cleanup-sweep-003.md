envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:30Z

# Candidate lesson: deliverable premise named two suppressions where the code has one

Source: Q-Gate finding e5602d (phase 4-plan, scope_criterion_validator over_coverage, deliverable 4, resolution taken_into_account)

The criterion "Both health-check suppressions are in the proven adjacent placement" assumed two cui-rewrite directives. A content search found only one, in `TokenValidatorHealthCheck.java`. `JwksEndpointHealthCheck.java` was planned as write-replace but carries no directive (its only catch is a narrow `IllegalStateException | IllegalArgumentException`).

Resolution in run: TASK-6 regenerated; JwksEndpointHealthCheck.java became a read step.

Possible rule: when a carried defect counts instances ("both", "all three"), confirm the count with a content search before the deliverable names write targets.
