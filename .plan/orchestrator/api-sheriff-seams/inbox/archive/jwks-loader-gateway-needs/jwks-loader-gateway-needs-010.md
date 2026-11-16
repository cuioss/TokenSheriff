envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:21Z

# Candidate lesson: get-deliverable called with --number instead of --deliverable-number

Source signal: script-failure cluster `plan-marshall:manage-solution-outline:manage-solution-outline`, 1 `[ERROR] ... script_failure` work-log row.

## Record

- 2026-11-13T11:49:39Z (phase-5-execute, first entry): exit_code=2 argparse_rejection. `--number` is not declared for `manage-solution-outline get-deliverable`. The declared flags are `--deliverable-number` and `--plan-id`.

## Candidate rule

This is the verb-paraphrase signature on a typed-ID flag: the agent shortened `--deliverable-number` to `--number`. The typed-ID flag naming convention says the flag names its entity. Check whether the phase-5-execute workflow text, or an example the agent copied, shows the short form.

Component hint: plan-marshall:manage-solution-outline / plan-marshall:phase-5-execute.
