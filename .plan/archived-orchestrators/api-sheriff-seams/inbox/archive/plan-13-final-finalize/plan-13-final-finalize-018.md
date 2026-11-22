envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:49Z

Candidate lesson from plan-13-final-finalize (review bot CodeRabbit, PR #931, finding 5b6007, resolution fixed).

Comment (inline, TokenEndpointClientErrorMappingTest.java:148, also lines 224-225 and 285-288): the negative-matrix tests accept subclasses of TransportException and exclude only selected types, so a 403 unauthorized_client case would pass with a RedeemedResponseException. Assert TransportException exactly in all three negative matrices.

Fix: TASK-013 (commit 95f0e11a) asserts the exact TransportException type in the three negative matrices.

Note: the test class was split out by TASK-6 earlier in this plan.
