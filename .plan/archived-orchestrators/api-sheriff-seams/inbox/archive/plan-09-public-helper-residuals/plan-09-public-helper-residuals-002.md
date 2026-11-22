envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:31:20Z

# Candidate lesson: `get-deliverable --number` instead of `--deliverable-number` (argparse rejection, twice)

**Signal source:** script-failure cluster `plan-marshall:manage-solution-outline:manage-solution-outline` (failure_kind=argparse_rejection, exit_code=2).

**Records:**
- 2026-11-17T10:14:41Z (5-execute, before TASK-009): `` `--number` is not declared for `plan-marshall:manage-solution-outline:manage-solution-outline get-deliverable`: ['deliverable-number', 'plan-id'] ``
- 2026-11-17T11:39:39Z (6-finalize, code-review round 2 that produced TASK-013): same rejection, same flag.

**Observation:** The same shortened flag name was guessed in two separate envelopes, one in phase 5 and one in phase 6. The canonical typed-ID flag is `--deliverable-number`. The argument-naming standard prefers typed-ID flags, but callers still reach for the short form.

**Possible corrective action (for the orchestrator to judge):** Quote the exact `get-deliverable --plan-id … --deliverable-number N` invocation in the execute-task and code-review workflow steps that read a deliverable. Or have the script accept `--number` as a deprecated alias, or point to `--deliverable-number` in the rejection message.

**In-run impact:** None lasting. Each retry succeeded.
