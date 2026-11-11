envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:13Z

# Candidate lesson: accepted weakened_test finding re-raised a third time after HEAD advanced

Source: Q-Gate finding 565027 (phase 6-finalize, functional-review weakened_test, error, resolution accepted by operator, "accepted again for this later commit under the same decision")

After HEAD advanced to 7d929659, the functional-review re-fire raised the identical 27-assertion phantom-removal finding on `InsufficientScopeChallengeParserTest` a third time. The operator accepted it again under the same decision. A full closing round graded all 9 deliverables and 29 criteria met.

This is the third record of one root cause (see 3a2142 and a994eb). The orchestrator may want to fold all three into one candidate: the test-integrity counter's handling of raw U+2028, plus no carry-forward of an operator acceptance across review firings and HEAD advances.
