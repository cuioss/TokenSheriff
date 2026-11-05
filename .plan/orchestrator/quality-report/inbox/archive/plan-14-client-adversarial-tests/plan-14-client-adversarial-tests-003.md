envelope_version=1
sender_type=plan
sender_id=plan-14-client-adversarial-tests
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:00:21Z

## Candidate lesson: F7 weakened_test false positive on a pure test-file move (TC-9)

**Signal source:** Q-Gate findings (repeated across rounds).

**Observation:** In plan-14, TC-9 relocated an existing test file without changing its assertions. The F7 `weakened_test` check fired repeatedly, treating the deletion at the old path as a removed/weakened test and ignoring the byte-equivalent (or assertion-equivalent) addition at the new path. Each recurrence had to be dismissed manually as a false positive.

**Shape:** defect + corrective action (detector false positive, recurring within one run).

**Suggested corrective rule:** the weakened-test detector should pair deleted and added test files by rename detection (git rename similarity) or by matching test-method sets before emitting `weakened_test`; a pure move with an unchanged assertion set must not raise the finding. Until fixed, a move-only commit should be recorded as a rename so the detector sees one renamed file rather than a delete plus an add.

**Candidate component(s):** the Q-Gate / verification check that emits F7 `weakened_test` (classification deferred to orchestrator).
