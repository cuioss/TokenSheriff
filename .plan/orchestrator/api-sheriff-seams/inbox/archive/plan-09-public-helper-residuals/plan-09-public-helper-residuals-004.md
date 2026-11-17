envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:31:42Z

# Candidate lesson: git-workflow `commit` refuses an inline functional-review fix in 6-finalize (no_sanction)

**Signal source:** script-failure cluster `plan-marshall:workflow-integration-git:git-workflow` (failure_kind=script_internal_failure, exit_code=1).

**Record:**
- 2026-11-17T11:02:32Z (6-finalize, step `default:functional-review`, between the two functional-review loop_back rounds): `Source-tree commit refused in 6-finalize: no step id from the composed phase-6 manifest, no open loop-back fix task and no review-rubric section-6 inline-correction record. Route the fix through a loop-back to 5-execute (open a fix task), pass --step with a composed phase-6 manifest step id, or record the section-6 inline correction, then retry.` The payload was `error_code: INVALID_INPUT`, `refused: true`, `reason: no_sanction`, `phase: 6-finalize`, `step: "default:functional-review"`, `changed_paths[1]` (truncated in the work log).

**Observation:** The functional-review round fixed the weakened issuer-in-WARN assertion inline (the fix shows up later as commit f57edf20) and tried to commit it without one of the three sanctions the guard requires. The guard did what it is designed to do, but the commit was still attempted, so one wasted call and a script_failure marker came before the sanctioned path. The rejection carries the generic `INVALID_INPUT` error_code, so tooling that branches on the code cannot tell this policy refusal apart from a malformed argument. Only `reason: no_sanction` makes that distinction.

**Possible corrective action (for the orchestrator to judge):** In the functional-review workflow, state up front that an inline source correction must be recorded as a review-rubric section-6 inline correction, or passed with `--step <composed phase-6 step id>`, before `commit` is called. Also consider a dedicated error_code for the policy refusal instead of `INVALID_INPUT`. Note: the caller described this record as a generic FETCH_FAILURE on unstaged edits. The work-log record shows `INVALID_INPUT` / `no_sanction`. The untruncated script-log entry was not checked.
