envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:28:51Z

Candidate lesson (signal: Q-Gate finding e2c1cb, phase 6-finalize, source functional-review, resolution accepted)

Observation: the functional review's test-integrity audit flagged `weakened_test` at JwtTokenTamperingUtilReproductionTest.java: 2 assertions of two methods are gone under the old class::function identity. The change was a pure rename (Javadoc-only commit) to JwtTokenTamperingUtilDecodedSignatureTest; both methods and both assertions survive under the new name.

Resolution: the operator accepted it at the finalize review ("Accept both"); the verifier accepted the grading.

Possible lesson: the test-integrity audit keys assertions on class::function identity, so a pure file/class rename reads as removed assertions. Rename detection (git rename similarity, or matching method bodies under the new class) would avoid a false weakened-test finding and the operator prompt it costs.

Component hint: plan-marshall:phase-6-finalize (review_support test-integrity).
