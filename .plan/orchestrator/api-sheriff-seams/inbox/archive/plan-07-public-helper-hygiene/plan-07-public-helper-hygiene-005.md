envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:38Z

# Candidate lesson: invented flag --number on manage-solution-outline get-deliverable

**Source signal**: script failure cluster, notation `plan-marshall:manage-solution-outline:manage-solution-outline` (failure_kind=argparse_rejection, exit_code=2).

## Record

Work log 2026-11-15T14:47:31Z, phase-5-execute re-entry (envelope 3):

```
`--number` is not declared for
`plan-marshall:manage-solution-outline:manage-solution-outline get-deliverable`:
['deliverable-number', 'plan-id']
```

## For classification

This is a flag paraphrase: the caller shortened the typed-ID flag `--deliverable-number` to `--number`. It matches the persona's "never invent script subcommands / flags" recurrence class. It may be worth checking whether a phase-5-execute workflow passage shows a `get-deliverable` call without the full flag name, because that would make the paraphrase likely.
