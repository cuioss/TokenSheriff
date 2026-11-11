envelope_version=1
sender_type=plan
sender_id=plan-14-client-adversarial-tests
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:00:13Z

## Candidate lesson: execute-commits-unrecorded refusal on an inline fix commit without a ledger row

**Signal source:** script-failure clusters / Q-Gate findings.

**Observation:** An inline fix commit made during plan-14 (outside the normal task-step commit path) had no corresponding change-ledger row. The finalize/commit gate then refused with `execute-commits-unrecorded`, because a commit on the branch was not attributable to any recorded ledger entry.

**Shape:** defect + corrective action. The refusal itself was correct; the defect is that an ad-hoc inline fix bypassed the ledger-recording step.

**Suggested corrective rule:** every commit made during execute/finalize — including an inline remediation of a review or verification finding — must be recorded through the change-ledger seam at commit time; an inline fix workflow should either route through the recorded commit path or document the explicit ledger-record call immediately after the commit.

**Candidate component(s):** `plan-marshall:manage-change-ledger`, `plan-marshall:phase-5-execute` / `plan-marshall:phase-6-finalize` (classification deferred to orchestrator).
