envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:28:59Z

Candidate lesson (signal: Q-Gate finding 101529, phase 6-finalize, source code-review, resolution accepted)

Observation: the code review's test-integrity audit raised again the same `weakened_test` at BackChannelHttpExchangeTest.java (4 assertions of `shouldMapCheckedCauseToFixedIOExceptionWithoutCause`, a confirmed duplicate of BackChannelHttpExchangeFailureMappingTest:103) that the operator had already accepted in the functional review at the same HEAD, minutes earlier (finding eb4197).

Resolution: accepted by pointing to the functional-review acceptance; the verifier accepted the grading.

Possible lesson: the functional review and the code review run the same test-integrity detection over the same tree, so one accepted removal makes two findings and two loop_back rounds. The code review could read the operator's acceptance already recorded at this HEAD, or the detection could run in only one of the two reviews.

Component hint: plan-marshall:phase-6-finalize (code-review / functional-review, review_support test-integrity).
