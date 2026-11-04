envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:25Z

# Candidate lesson: manage-tasks list --status given a comma-joined value

**Signal source:** script-failure cluster, work-log `[ERROR] ... script_failure` at 2026-11-01T22:36:47Z (phase 5-execute, right after a verification-feedback dispatch)
**Notation:** plan-marshall:manage-tasks:manage-tasks
**Failure kind:** argparse_rejection, exit_code=2

## What happened

The caller ran `manage-tasks list --status pending,in_progress`. `--status` is a single-choice argument (`pending`, `in_progress`, `done`, `blocked`, `infeasible`, `all`), so the comma-joined value was rejected.

## Candidate rule

`manage-tasks list --status` takes exactly one value. To get "everything not done", use `--status all` and filter, or make two calls. Alternatively this is a script improvement: accept a repeatable or comma-separated `--status` because "open tasks" (pending plus in_progress) is a natural query for the orchestrator after verification-feedback adds fix tasks.
