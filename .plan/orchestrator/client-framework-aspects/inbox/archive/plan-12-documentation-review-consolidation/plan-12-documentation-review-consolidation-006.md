envelope_version=1
sender_type=plan
sender_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-23T20:28:48Z

# Candidate lesson: manage-solution-outline get-deliverable rejects --number

Source signal: script-failure cluster `plan-marshall:manage-solution-outline:manage-solution-outline`
(work log 2026-11-23T14:20:25Z, entry 6a94f5, phase 5-execute entry).

## Observation

```
script_failure notation=plan-marshall:manage-solution-outline:manage-solution-outline exit_code=2
failure_kind=argparse_rejection detail=`--number` is not declared for
`...manage-solution-outline get-deliverable`: ['deliverable-number', 'plan-id']
```

The caller paraphrased the typed-ID flag as `--number` instead of the declared `--deliverable-number`. This is a
known argparse-rejection signature: a paraphrased flag name.

## Candidate rule

Call `get-deliverable` only with `--plan-id` and `--deliverable-number`. Find the workflow text that led the agent
to write `--number`, and align it with the canonical-invocation block. Alternatively, add `--number` as an accepted
alias if that is the intended surface.

Component hint: plan-marshall:manage-solution-outline (canonical invocations) / plan-marshall:phase-5-execute caller prose.
