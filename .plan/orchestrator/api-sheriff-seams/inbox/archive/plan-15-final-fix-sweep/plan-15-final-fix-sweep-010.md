envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:52Z

# Candidate lesson: manage-solution-outline get-deliverable invoked with --number instead of --deliverable-number

**Signal:** script failure cluster `plan-marshall:manage-solution-outline:manage-solution-outline`
**Records:** work log 6a94f5, three times (2026-11-20T19:07:34Z, 20:05:25Z, 20:15:14Z), each the same: `script_failure ... exit_code=2 failure_kind=argparse_rejection detail=--number is not declared for ... get-deliverable: ['deliverable-number', 'plan-id']`.

## What happened

Each of the three phase-5-execute envelope entries that read a deliverable first called `get-deliverable --number N`, got rejected, and retried. The same wrong flag came back at every re-entry, so whatever source the call is copied from (a workflow doc or the model's habit) was not corrected inside the run.

## Why it may be worth carrying

This is the verb-scoped flag-paraphrase class. The canonical flag is `--deliverable-number`. If a phase-5-execute or execute-task doc shows `--number` for this verb, the doc is the thing to fix. If not, a recurrence signature for this verb would help.
