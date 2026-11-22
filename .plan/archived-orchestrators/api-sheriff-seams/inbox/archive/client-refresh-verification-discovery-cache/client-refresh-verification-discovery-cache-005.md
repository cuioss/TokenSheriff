envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:07:58Z

component=plan-marshall:manage-solution-outline
category=anti-pattern
source_plan=client-refresh-verification-discovery-cache
source_record=work-log 6a94f5 (2026-11-15T09:55:35Z, 5-execute) script_failure argparse_rejection

# Invented flag `--number` on manage-solution-outline get-deliverable (canonical is `--deliverable-number`)

## What happened

During the second 5-execute envelope, an agent called
`manage-solution-outline get-deliverable --number ...`. argparse rejected it (exit 2):
`--number` is not declared, and the accepted flags are `deliverable-number` and `plan-id`.

## Why it matters

This is the verb-paraphrase recurrence signature: a plausible short flag name shortened from the
canonical typed-ID flag. It costs a retry each time. If the error is not read, it also silently skips
the deliverable read the task needed.

## Candidate rule

Use the typed-ID flag `--deliverable-number` for every deliverable-selecting verb, and quote it from the
canonical-invocation block. Consider an argparse alias (`--number` -> `--deliverable-number`) or an
explicit "did you mean" in the rejection detail, since this paraphrase is the predictable one.
