envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:37:59Z

Candidate lesson from plan-13-final-finalize (Q-Gate 6-finalize, finding 063716, functional-review, resolution accepted).

Finding: unevidenced_criterion at D6.C5. The Sonar open-issue read at the PR head cannot exist before a PR does.

Resolution: the operator accepted it at the review gate; the read was owed to the sonar-roundtrip step after create-pr. The work log records that read later (edecf3): at PR 931 head a9f97d16, 4 open issues (java:S125), 0 hotspots, quality gate OK.

Observation: functional-review runs before create-pr, so a success criterion that needs a PR-head read is always unevidenced at that point.
