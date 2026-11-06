envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:14:30Z

# Candidate lesson: acceptance criteria observable only after push make the pre-push functional review refuse its verdict

## Source records

- Q-Gate findings 6b98fc and bd0304 — phase 6-finalize, rule functional-review-state, verdict_refused, resolution accepted (operator close at 7b8ed2a7).

## What happened

Two acceptance criteria could not be observed before the push: D1.C3 ("Sonar issue gone") and D6.C2 ("PR body records the per-site line"). The functional-review author graded both as met; the verifier refused the verdict in two consecutive rounds because the evidence could not show them. There were no blocking findings in either round. The operator closed the review by hand, noting that sonar-roundtrip and create-pr check those criteria later.

## Candidate rule

Criteria whose evidence exists only after push / PR creation / Sonar analysis should be marked as post-push (or owned by the step that can observe them) at outline time, so the pre-push functional review neither grades them met nor refuses on them. As written, every plan with a "Sonar issue gone" or "PR body says X" criterion forces an operator override of the functional review.

## Classification left to the orchestrator

Likely a plan-marshall outline/functional-review contract gap rather than a TokenSheriff fact.
