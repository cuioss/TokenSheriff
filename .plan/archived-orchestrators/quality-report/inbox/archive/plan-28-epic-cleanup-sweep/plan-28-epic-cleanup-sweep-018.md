envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:42Z

# Candidate lesson: manage-solution-outline get-deliverable called with --number instead of --deliverable-number

Source: script-failure cluster `plan-marshall:manage-solution-outline:manage-solution-outline` (work-log hash 6a94f5, 2026-11-10T18:38:07Z, phase 5-execute)

```
[ERROR] (plan-marshall:execute-script:2) script_failure notation=plan-marshall:manage-solution-outline:manage-solution-outline exit_code=2 failure_kind=argparse_rejection detail=`--number` is not declared for `plan-marshall:manage-solution-outline:manage-solution-outline get-deliverable`: ['deliverable-number', 'plan-id']
```

Typed-ID flag paraphrase: the agent shortened the canonical `--deliverable-number` to `--number`. This belongs to the argument-naming recurrence class (typed-ID flags must be quoted verbatim).
