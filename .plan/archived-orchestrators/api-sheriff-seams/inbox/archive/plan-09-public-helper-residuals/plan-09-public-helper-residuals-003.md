envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:31:30Z

# Candidate lesson: invented `--record-change` flag on git-workflow `commit`

**Signal source:** script-failure cluster `plan-marshall:workflow-integration-git:git-workflow` (argparse_rejection, exit_code=2).

**Record:**
- 2026-11-17T10:43:13Z (5-execute, per-deliverable commit for TASK-010): `` `--record-change` is not declared for `plan-marshall:workflow-integration-git:git-workflow commit`: ['base', 'branch', 'cached', 'changed-paths', 'detach', 'force', 'force-with-lease', 'head', 'message', 'message-file', 'phase', 'plan-id', 'project-dir', 'push', 'root', 'scope', 'step', 'subject', 'type'] ``

**Observation:** The per-deliverable commit step passed a flag that `commit` does not declare. `--changed-paths` is the nearest real flag. The commit landed on retry (TASK-10 commit d03ce034 is logged at 10:28, and TASK-010 completed at 10:51).

**Possible corrective action (for the orchestrator to judge):** Quote the canonical `commit` invocation in the phase-5 per-deliverable commit step. If recording the change in the change ledger is the intent, name the verb that actually does that, so callers do not attach the intent to `commit` as a made-up flag.
