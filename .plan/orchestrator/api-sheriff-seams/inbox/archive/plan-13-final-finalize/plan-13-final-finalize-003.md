envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:37:41Z

Candidate lesson from plan-13-final-finalize (Q-Gate 6-finalize, finding 8b85bb, functional-review, resolution accepted).

Finding: weakened_test at TokenEndpointClientTest.java, 65 assertions flagged. The class was split into TokenEndpointClientTest, TokenEndpointClientErrorMappingTest and TokenEndpointClientDpopBindingTest as a pure move; reviewer counts 91 assertions at base and 92 at HEAD.

Resolution: the operator accepted it at the review gate. The checker reads split and renamed tests as removals because test identities are keyed by class.
