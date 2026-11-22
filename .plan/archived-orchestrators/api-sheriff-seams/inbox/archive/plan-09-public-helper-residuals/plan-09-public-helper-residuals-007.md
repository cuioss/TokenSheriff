envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:32:16Z

# Candidate lesson: code-review verifier refuses grading over an operator-accepted counter artefact

**Signal source:** Q-Gate findings, 6-finalize, `verdict_refused at code-review` (rule code-review-state).

**Records:**
- a24420 (11:11:51Z, round 1 at 48d239ce's predecessor): "Verifier declined the grading: F10 (weakened_test, a counter artefact already accepted by the operator at functional review) is not in the author file and is graded blocking by the validator." Resolved `taken_into_account` (superseded by round 2). Decision log 11:12:00Z: "Verifier declined; may_close=false — F1,F2 confirmed … F10 not in the author file, a counter artefact graded blocking."
- f94a3f (15:20:45Z, round 9 at rebased head 0926f3ad): "F10 unsupported: issuer-in-WARN assertion moved to assertWarnNamesIssuer(), still called … Blocking F10 and (1) false decide it." Closed by operator (`accepted`).

**Observation:** Functional-review had already accepted the weakened_test artefact, but code-review's validator still added it as a blocking F10 that the author file did not contain. The verifier then declined the grading, and the round closed only after an operator decision. An acceptance in one review step does not reach the other step's validator, so the same artefact blocks twice and causes refusals.

**Possible corrective action (for the orchestrator to judge):** Let the code-review validator read the operator-accepted Q-Gate rows for the same file, method and head lineage, and grade them advisory rather than blocking. Or have the author file include accepted artefacts explicitly, so author and validator agree.
