envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:18Z

# Candidate lesson: get-deliverable called with invented --number flag

**Signal source:** script-failure cluster, work-log `[ERROR] ... script_failure` at 2026-11-01T20:58:31Z (phase 5-execute, during TASK-005)
**Notation:** plan-marshall:manage-solution-outline:manage-solution-outline
**Failure kind:** argparse_rejection, exit_code=2

## What happened

The agent called `manage-solution-outline get-deliverable --number ...`. The verb declares only `--deliverable-number` and `--plan-id`. The call was rejected at parse time and then retried successfully. Execution continued.

## Candidate rule

This is the "verb-paraphrase / typed-ID flag" recurrence signature: the typed-ID flag is `--deliverable-number`, not `--number`. Check whether the execute-task / phase-5-execute docs that instruct a deliverable read quote the canonical invocation verbatim. If a doc shows the short form, fix the doc. Otherwise this adds one more recurrence to the existing argparse-rejection lesson.
