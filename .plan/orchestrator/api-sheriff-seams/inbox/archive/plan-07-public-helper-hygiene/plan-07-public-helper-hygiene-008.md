envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:59Z

# Candidate lesson: invented verb query on manage-findings qgate

**Source signal**: script failure cluster, notation `plan-marshall:manage-findings:manage-findings` (failure_kind=argparse_rejection, exit_code=2).

## Record

Work log 2026-11-15T21:06:37Z, during a functional-review delta round:

```
`query` is not a registered verb for `plan-marshall:manage-findings:manage-findings qgate`:
['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced']
```

## For classification

This is a verb paraphrase (`query` in place of `list`) on the qgate noun. It is the same "never invent script subcommands" recurrence class as the manage-solution-outline `--number` rejection in this plan. Two argparse rejections in one plan, one on a flag and one on a verb, both from leaves that wrote the call from memory rather than from the canonical-invocation block.
