envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:29:07Z

Candidate lesson (signal: Q-Gate finding 3620ab, phase 6-finalize, source code-review, resolution accepted)

Observation: the code review's test-integrity audit raised again the rename JwtTokenTamperingUtilReproductionTest -> JwtTokenTamperingUtilDecodedSignatureTest as `weakened_test` (2 assertions gone under the old class::function identity). The functional review had already raised it at the same HEAD (finding e2c1cb) and the operator had accepted it.

Resolution: accepted by pointing to the functional-review acceptance; the verifier accepted the grading.

Possible lesson: this is the same double report as 101529, this time for a rename that is also a false positive. Two separate improvements would each remove it: rename-aware assertion matching, and carrying an acceptance across the two reviews at one HEAD.

Component hint: plan-marshall:phase-6-finalize (review_support test-integrity).
