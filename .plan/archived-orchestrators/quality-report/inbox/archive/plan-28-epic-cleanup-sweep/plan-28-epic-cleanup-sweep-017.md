envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:35Z

# Candidate lesson: manage-references get called without the required --field flag

Source: script-failure cluster `plan-marshall:manage-references:manage-references` (work-log hash 459284, 2026-11-10T16:50:40Z, phase 3-outline)

```
[ERROR] (plan-marshall:execute-script:2) script_failure notation=plan-marshall:manage-references:manage-references exit_code=2 failure_kind=argparse_rejection detail=Add the required flag(s) to `plan-marshall:manage-references:manage-references get`: ['field']
```

During outline start-up, the agent called `manage-references get` without the required `--field`. This matches the argparse-rejection recurrence class: the invocation was written from narrative instead of from the script's canonical-invocation block.
