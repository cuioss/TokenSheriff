envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T14:56:57Z

component=plan-marshall:persona-plan-marshall-agent
category=anti-pattern
status=active

# Invented script verbs and flags recur inside one run because the correction is not carried across context

The same argparse rejection was made twice, 35 minutes apart, in one plan.

## What happened

In plan plan-03-client-key-material-config-validators the work log carries three `script_failure` lines over two notations, all `argparse_rejection`, all caused by an invented name rather than a documented one:

- `manage-findings qgate query` (not a registered verb; the registered ones are add, clear, list, resolve, resolve-by-rule, resolve-evidenced). Corrected to `qgate list`.
- `manage-solution-outline get-deliverable --number` (the declared flag is `--deliverable-number`), rejected at 09:26 and again at 10:01 in the same plan.

## Rule

Before calling a verb or flag the run has not already used successfully, read it from the loaded skill's canonical invocation or `--help`. After a rejection, the corrected form belongs in the work log line that follows so a later context sees it.
