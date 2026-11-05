envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:48Z

# Code-review verifier JSON written with the wrong key (findings_supported instead of grading_supported) and read as malformed

**Source**: reported by the finalize dispatcher for this run. Partial confirmation only: the surviving artifact `work/code-review-verifier.json` now carries the correct key (`{"grading_supported": true, "may_close": true, ...}`), but no work-log or decision-log line records the malformed read itself. The orchestrator should weigh this candidate with that gap in mind.

## What happened (as reported)

A code-review verifier dispatch wrote its verdict JSON with the key `findings_supported`. The code-review workflow expects `grading_supported` (the same key the functional-review verifier uses), so the verdict was read as malformed and had to be redone or corrected before the round could close.

## Candidate rule

The verifier verdict schema (`grading_supported`, `may_close`, `reason`) should be validated by a script at write time, rejecting unknown keys with a message naming the expected key, rather than discovered as malformed by the reader. If the code-review and functional-review workflows describe the verifier output in prose, the key name should come from one shared schema.
