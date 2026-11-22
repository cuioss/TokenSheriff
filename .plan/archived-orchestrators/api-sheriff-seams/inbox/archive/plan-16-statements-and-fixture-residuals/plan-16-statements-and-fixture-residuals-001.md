envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:28:44Z

Candidate lesson (signal: Q-Gate finding eb4197, phase 6-finalize, source functional-review, resolution accepted)

Observation: the functional review's test-integrity audit flagged `weakened_test` at BackChannelHttpExchangeTest.java: 4 assertions of `shouldMapCheckedCauseToFixedIOExceptionWithoutCause` are gone. The outline had already removed that case on purpose as a confirmed duplicate of BackChannelHttpExchangeFailureMappingTest:103 (same fixed text, null cause).

Resolution: the operator accepted the removal at the finalize review ("Accept both"); the verifier accepted the grading.

Possible lesson: when the outline removes a test on purpose as a confirmed duplicate, the test-integrity audit still reports it as a weakened test and needs an operator answer. The outline-time approval and the surviving pinning test could be carried into the audit, so an already-approved removal is not raised again.

Component hint: plan-marshall:phase-6-finalize (review_support test-integrity).
